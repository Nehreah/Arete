package com.renea.arete_social.data.mesh

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "GattFramer"

/**
 * Utilidad de fragmentación y reensamblado de payloads para BLE GATT.
 *
 * ## Protocolo de framing robusto (cabecera de 7 bytes)
 * ```
 * ┌──────────┬───────────────┬──────────────────┬──────────────────┬─────────────────────┐
 * │ byte [0] │  bytes [1..2] │   bytes [3..4]   │   bytes [5..6]   │  bytes [7..MTU-1]   │
 * │  flags   │ messageId U16 │ fragmentIndex U16│ totalFragments U16│    payload chunk    │
 * └──────────┴───────────────┴──────────────────┴──────────────────┴─────────────────────┘
 * ```
 * - `messageId` se incrementa atómicamente por transmisión para aislar mensajes concurrentes.
 * - `fragmentIndex` soporta hasta 65,535 fragmentos (superando el límite anterior de 255).
 * - `sessionKey` = "$mac:$messageId"
 */
object GattFramer {

    private const val HEADER_SIZE        = 7
    private const val FLAG_FIRST: Byte   = 0b01
    private const val FLAG_LAST: Byte    = 0b10
    private const val WINDOW_TIMEOUT_MS  = 12_000L   // 12 s para limpiar sesiones parciales

    // Límites anti-abuso: cualquier dispositivo BLE cercano puede escribir en la característica.
    // Antes `total` (u16, hasta 65 535) reservaba un array de ese tamaño por sesión, con msgIds
    // ilimitados por dispositivo: agotamiento de memoria con unos pocos cientos de tramas.
    private const val MAX_FRAGMENTOS         = 2_048
    private const val MAX_BYTES_MENSAJE      = 64_000
    private const val MAX_SESIONES_POR_MAC   = 4
    private const val MAX_SESIONES_TOTALES   = 64

    private val nextMessageId = AtomicInteger(1)

    private fun generarMessageId(): Int {
        val id = nextMessageId.getAndIncrement() and 0xFFFF
        if (id == 0) return nextMessageId.getAndIncrement() and 0xFFFF
        return id
    }

    /** Sesión de reensamblado en curso para una pareja (mac, messageId). */
    private data class Session(
        val total: Int,
        val fragments: Array<ByteArray?>,
        val receivedAt: Long = System.currentTimeMillis(),
        var bytes: Int = 0
    )

    private val sessions = HashMap<String, Session>()

    // ─────────────────────────────────────────────────────────────────────────
    // FRAGMENTACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    fun fragmentar(payload: ByteArray, mtu: Int): List<ByteArray> {
        val chunkSize = (mtu - HEADER_SIZE).coerceAtLeast(1)
        val chunks    = payload.toChunks(chunkSize)
        val total     = chunks.size
        val msgId     = generarMessageId()

        return chunks.mapIndexed { index, chunk ->
            var flags: Byte = 0
            if (index == 0)          flags = (flags.toInt() or FLAG_FIRST.toInt()).toByte()
            if (index == total - 1)  flags = (flags.toInt() or FLAG_LAST.toInt()).toByte()

            ByteArray(HEADER_SIZE + chunk.size).also { frame ->
                frame[0] = flags
                frame[1] = ((msgId shr 8) and 0xFF).toByte()
                frame[2] = (msgId and 0xFF).toByte()
                frame[3] = ((index shr 8) and 0xFF).toByte()
                frame[4] = (index and 0xFF).toByte()
                frame[5] = ((total shr 8) and 0xFF).toByte()
                frame[6] = (total and 0xFF).toByte()
                System.arraycopy(chunk, 0, frame, HEADER_SIZE, chunk.size)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REENSAMBLADO
    // ─────────────────────────────────────────────────────────────────────────

    fun ensamblar(mac: String, frame: ByteArray): ByteArray? {
        if (frame.size < HEADER_SIZE) {
            Log.w(TAG, "[$mac] Frame demasiado corto (${frame.size} bytes), descartado")
            return null
        }

        val flags = frame[0]
        val msgId = ((frame[1].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF)
        val index = ((frame[3].toInt() and 0xFF) shl 8) or (frame[4].toInt() and 0xFF)
        val total = ((frame[5].toInt() and 0xFF) shl 8) or (frame[6].toInt() and 0xFF)
        val chunk = frame.copyOfRange(HEADER_SIZE, frame.size)

        val esFirst = (flags.toInt() and FLAG_FIRST.toInt()) != 0
        val esLast  = (flags.toInt() and FLAG_LAST.toInt()) != 0

        // Paquete de un único fragmento → atajo rápido sin crear sesión
        if (esFirst && esLast && total == 1) {
            return chunk
        }

        synchronized(this) {
            limpiarSesionesExpiradas()

            val sessionKey = "$mac:$msgId"

            val session = if (esFirst) {
                if (total !in 2..MAX_FRAGMENTOS) {
                    Log.w(TAG, "[$mac] totalFragments=$total fuera de rango, descartado")
                    return null
                }
                hacerHuecoParaSesion(mac)
                Session(total = total, fragments = arrayOfNulls(total))
                    .also { sessions[sessionKey] = it }
            } else {
                sessions[sessionKey]
            }

            if (session == null) {
                Log.w(TAG, "[$mac] Fragmento $index/$total sin sesión activa (msgId=$msgId), descartado")
                return null
            }
            if (total != session.total || index >= session.fragments.size) {
                Log.w(TAG, "[$mac] Fragmento inconsistente (idx=$index total=$total esperado=${session.total})")
                sessions.remove(sessionKey)
                return null
            }

            if (session.fragments[index] == null) {
                session.fragments[index] = chunk
                session.bytes += chunk.size
                if (session.bytes > MAX_BYTES_MENSAJE) {
                    Log.w(TAG, "[$mac] Mensaje excede $MAX_BYTES_MENSAJE bytes, descartado")
                    sessions.remove(sessionKey)
                    return null
                }
            }

            val fragmentosCompletos = session.fragments.filterNotNull()
            if (fragmentosCompletos.size == session.total) {
                sessions.remove(sessionKey)
                val salida = java.io.ByteArrayOutputStream(session.bytes)
                fragmentosCompletos.forEach { salida.write(it) }
                return salida.toByteArray()
            }
            return null
        }
    }

    /** Aplica los topes de sesiones abiertas (por dispositivo y globales) descartando las más viejas. */
    private fun hacerHuecoParaSesion(mac: String) {
        val delMac = sessions.entries.filter { it.key.startsWith("$mac:") }.sortedBy { it.value.receivedAt }
        if (delMac.size >= MAX_SESIONES_POR_MAC) {
            delMac.take(delMac.size - MAX_SESIONES_POR_MAC + 1).forEach { sessions.remove(it.key) }
        }
        if (sessions.size >= MAX_SESIONES_TOTALES) {
            sessions.entries.minByOrNull { it.value.receivedAt }?.let { sessions.remove(it.key) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIMPIEZA
    // ─────────────────────────────────────────────────────────────────────────

    @Synchronized
    fun limpiarSesion(mac: String) {
        sessions.entries.removeIf { it.key.startsWith("$mac:") }
    }

    @Synchronized
    private fun limpiarSesionesExpiradas() {
        val ahora     = System.currentTimeMillis()
        val expiradas = sessions.entries.filter { ahora - it.value.receivedAt > WINDOW_TIMEOUT_MS }
        expiradas.forEach {
            Log.d(TAG, "Sesión parcial expirada eliminada: ${it.key}")
            sessions.remove(it.key)
        }
    }

    @Synchronized
    fun limpiarTodo() {
        sessions.clear()
    }

    private fun ByteArray.toChunks(chunkSize: Int): List<ByteArray> {
        if (isEmpty()) return listOf(ByteArray(0))
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < size) {
            val end = minOf(offset + chunkSize, size)
            result += copyOfRange(offset, end)
            offset = end
        }
        return result
    }
}

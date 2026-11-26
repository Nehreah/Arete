package com.renea.arete_social.data.mesh

import android.util.Log
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.ProtocoloPerfilMediaBle
import com.renea.arete_social.domain.repository.ResolverPerfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "GestorPerfilMediaBle"
private const val REINTENTO_MEDIA_MS = 20_000L
private const val PAUSA_CHAT_ACTIVO_MS = 2_000L
private const val INTERVALO_ENTRE_TROZOS_MS = 130L // <= 8 paquetes/segundo para proteger la radio BLE
private const val MAX_EDAD_TRANSFERENCIA_MS = 60_000L // Poda de transferencias incompletas

/**
 * Gestor de transferencia de medios (miniaturas de avatar y portada) por la malla BLE / Wi-Fi.
 * Opera en el canal dedicado "perfil-media", transfiriendo trozos de <= 2 KB bajo demanda.
 *
 * ## Salvaguardas operativas
 * - Ritmo de envío limitado a <= 8 paquetes/s.
 * - Pausa la transferencia automáticamente cuando detecta tráfico activo de mensajes de chat.
 * - Envío dirigido al peer destino para evitar saturar la malla con broadcasts de chunks.
 * - Reintento selectivo de chunks faltantes y limpieza automática de sesiones huérfanas.
 */
class GestorPerfilMediaBle(
    private val router: MessageRouter,
    private val resolverPerfiles: ResolverPerfiles,
    private val miIdProveedor: () -> String?,
    private val esAmigo: (String) -> Boolean,
    private val esPerfilPrivado: (() -> Boolean)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private var observadorJob: Job? = null
    private var limpiezaJob: Job? = null
    private val pedidosEnVuelo = ConcurrentHashMap<String, Long>()
    private val trozosRecibidos = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
    private val tiempoInicioTransferencia = ConcurrentHashMap<String, Long>()
    private val totalEsperado = ConcurrentHashMap<String, Int>()
    private val duenoPorHash = ConcurrentHashMap<String, String>()
    private val ultimoMensajeChatMs = AtomicLong(0L)

    fun iniciar() {
        if (observadorJob?.isActive == true) return
        observadorJob = scope.launch {
            router.todosLosPaquetesEntrantes()
                .filter { it.chatId == ProtocoloPerfilMediaBle.CHAT_ID }
                .collect { paquete ->
                    runCatching { procesar(paquete) }
                        .onFailure { Log.w(TAG, "Error procesando paquete de medios: ${it.message}") }
                }
        }

        // Tarea periódica de limpieza de transferencias incompletas
        limpiezaJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                podarTransferenciasExpiradas()
            }
        }
    }

    fun detener() {
        observadorJob?.cancel()
        limpiezaJob?.cancel()
        observadorJob = null
        limpiezaJob = null
    }

    fun notificarActividadChat() {
        ultimoMensajeChatMs.set(System.currentTimeMillis())
    }

    /**
     * Solicita una miniatura a un par por su hash SHA-256.
     * Si el archivo ya existe localmente en disco, no realiza ninguna petición de red.
     */
    fun solicitarMedia(peerId: String, hashSha256: String) {
        if (peerId.isBlank() || hashSha256.isBlank()) return
        val miId = miIdProveedor() ?: return

        // Si ya lo tenemos en disco, no hay nada que pedir por la red
        if (AlmacenMediaPerfil.obtenerArchivoPorHash(hashSha256) != null) return

        val ahora = System.currentTimeMillis()
        val previo = pedidosEnVuelo[hashSha256]
        if (previo != null && ahora - previo < REINTENTO_MEDIA_MS) return
        pedidosEnVuelo[hashSha256] = ahora
        duenoPorHash[hashSha256] = peerId

        scope.launch {
            val paquete = PaqueteMesh(
                id = "media-req-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilMediaBle.CHAT_ID,
                remitenteId = miId,
                destinatarioId = peerId,
                contenido = ProtocoloPerfilMediaBle.pedir(hashSha256),
                ttl = 1,
                timestamp = ahora
            )
            val enviadoDirecto = router.enviarADestino(peerId, paquete)
            if (!enviadoDirecto) {
                router.reenviar(paquete)
            }
        }
    }

    private suspend fun procesar(paquete: PaqueteMesh) {
        val miId = miIdProveedor() ?: return
        if (paquete.remitenteId == miId) return
        if (paquete.destinatarioId != miId) return

        // 1. ¿Es una petición de imagen que me hacen a mí?
        ProtocoloPerfilMediaBle.parsearPedir(paquete.contenido)?.let { hashPedido ->
            responderPeticionMedia(hashPedido, remitente = paquete.remitenteId)
            return
        }

        // 2. ¿Es una petición de trozos faltantes?
        ProtocoloPerfilMediaBle.parsearFaltantes(paquete.contenido)?.let { faltantes ->
            responderTrozosFaltantes(faltantes.hashSha256, faltantes.indices, remitente = paquete.remitenteId)
            return
        }

        // 3. ¿Es un trozo de imagen que me están entregando?
        ProtocoloPerfilMediaBle.parsearTrozo(paquete.contenido)?.let { trozo ->
            procesarTrozoEntrante(trozo, remitente = paquete.remitenteId)
            return
        }
    }

    private suspend fun responderPeticionMedia(hashSha256: String, remitente: String) {
        val miId = miIdProveedor() ?: return
        // Validar privacidad: si el perfil es privado y el remitente no es amigo, rechazar
        if (esPerfilPrivado?.invoke() == true && !esAmigo(remitente)) {
            Log.w(TAG, "Petición de medio $hashSha256 rechazada: perfil privado y remitente no es amigo.")
            return
        }
        // Solo responder con medios seguros (miniaturas <= 50 KB)
        val archivo = AlmacenMediaPerfil.obtenerMediaSeguraBle(hashSha256) ?: return
        val bytes = runCatching { archivo.readBytes() }.getOrNull() ?: return
        if (bytes.isEmpty()) return

        val chunkSize = ProtocoloPerfilMediaBle.CHUNK_SIZE_BYTES
        val total = (bytes.size + chunkSize - 1) / chunkSize
        if (total > ProtocoloPerfilMediaBle.MAX_CHUNKS) return

        for (i in 0 until total) {
            // Pausa preventiva si el canal de chat está activo
            while (System.currentTimeMillis() - ultimoMensajeChatMs.get() < PAUSA_CHAT_ACTIVO_MS) {
                delay(400)
            }

            val desde = i * chunkSize
            val hasta = minOf(desde + chunkSize, bytes.size)
            val trozoBytes = bytes.copyOfRange(desde, hasta)
            val contenidoTrozo = ProtocoloPerfilMediaBle.codificarTrozo(hashSha256, i, total, trozoBytes)

            val p = PaqueteMesh(
                id = "media-chk-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilMediaBle.CHAT_ID,
                remitenteId = miId,
                destinatarioId = remitente,
                contenido = contenidoTrozo,
                ttl = 1
            )
            val directo = router.enviarADestino(remitente, p)
            if (!directo) {
                router.reenviar(p)
            }

            if (i < total - 1) {
                delay(INTERVALO_ENTRE_TROZOS_MS)
            }
        }
    }

    private suspend fun responderTrozosFaltantes(hashSha256: String, indices: List<Int>, remitente: String) {
        val miId = miIdProveedor() ?: return
        if (esPerfilPrivado?.invoke() == true && !esAmigo(remitente)) {
            Log.w(TAG, "Petición de trozos faltantes rechazada: perfil privado.")
            return
        }
        val archivo = AlmacenMediaPerfil.obtenerMediaSeguraBle(hashSha256) ?: return
        val bytes = runCatching { archivo.readBytes() }.getOrNull() ?: return
        if (bytes.isEmpty()) return

        val chunkSize = ProtocoloPerfilMediaBle.CHUNK_SIZE_BYTES
        val total = (bytes.size + chunkSize - 1) / chunkSize

        for (i in indices) {
            if (i !in 0 until total) continue
            while (System.currentTimeMillis() - ultimoMensajeChatMs.get() < PAUSA_CHAT_ACTIVO_MS) {
                delay(400)
            }
            val desde = i * chunkSize
            val hasta = minOf(desde + chunkSize, bytes.size)
            val trozoBytes = bytes.copyOfRange(desde, hasta)
            val contenidoTrozo = ProtocoloPerfilMediaBle.codificarTrozo(hashSha256, i, total, trozoBytes)

            val p = PaqueteMesh(
                id = "media-chk-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilMediaBle.CHAT_ID,
                remitenteId = miId,
                destinatarioId = remitente,
                contenido = contenidoTrozo,
                ttl = 1
            )
            val directo = router.enviarADestino(remitente, p)
            if (!directo) router.reenviar(p)
            delay(INTERVALO_ENTRE_TROZOS_MS)
        }
    }

    private suspend fun procesarTrozoEntrante(trozo: ProtocoloPerfilMediaBle.TrozoMedia, remitente: String) {
        val hash = trozo.hashSha256
        val map = trozosRecibidos.getOrPut(hash) { ConcurrentHashMap() }
        tiempoInicioTransferencia.putIfAbsent(hash, System.currentTimeMillis())
        totalEsperado[hash] = trozo.total
        map[trozo.indice] = trozo.bytes

        if (map.size == trozo.total) {
            // Reensamblaje completo
            val stream = ByteArrayOutputStream()
            for (i in 0 until trozo.total) {
                val b = map[i] ?: return // falta uno
                stream.write(b)
            }
            val bytesCompletos = stream.toByteArray()
            val hashCalculado = AlmacenMediaPerfil.calcularHashSha256(bytesCompletos)

            if (hashCalculado.equals(hash, ignoreCase = true)) {
                val guardado = AlmacenMediaPerfil.guardarBytesConHash(bytesCompletos, hash, esMiniatura = true)
                val peerId = duenoPorHash[hash] ?: remitente
                resolverPerfiles.actualizarRutaMediaPar(peerId, hash, guardado.absolutePath)
                Log.d(TAG, "SYNC: Miniatura $hash recibida y guardada exitosamente (${bytesCompletos.size} bytes)")
            } else {
                Log.w(TAG, "SYNC: Hash no coincide para $hash: calculado $hashCalculado")
            }

            limpiarEstadoHash(hash)
        }
    }

    private fun limpiarEstadoHash(hash: String) {
        trozosRecibidos.remove(hash)
        totalEsperado.remove(hash)
        pedidosEnVuelo.remove(hash)
        duenoPorHash.remove(hash)
        tiempoInicioTransferencia.remove(hash)
    }

    private fun podarTransferenciasExpiradas() {
        val ahora = System.currentTimeMillis()
        val expirados = tiempoInicioTransferencia.filter { ahora - it.value > MAX_EDAD_TRANSFERENCIA_MS }.keys
        for (h in expirados) {
            Log.d(TAG, "SYNC: Podando transferencia de media expirada/incompleta: $h")
            limpiarEstadoHash(h)
        }
    }
}

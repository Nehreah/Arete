package com.renea.arete_social.data.remote

import android.util.Log
import com.renea.arete_social.data.remote.RelayEnvelope
import com.renea.arete_social.data.remote.RelayTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "HttpRelayTransport"
private const val TIMEOUT_CONEXION_MS = 8_000
private const val TIMEOUT_LECTURA_MS  = 12_000
private const val MAX_PAYLOAD_BYTES   = 65_536 // 64 KB máximo por sobre
private const val USER_AGENT          = "Arete-Android/1.0 (Relay)"

/** DTO de depósito enviado al servidor relay. */
@Serializable
private data class DepositRequest(
    val tag: String,
    val id: String,
    val payloadHex: String,
    val timestampMs: Long,
    val ttlSeconds: Long
)

/** DTO de sobre recibido del servidor relay. */
@Serializable
private data class FetchResponse(val envelopes: List<EnvelopeDto>)

@Serializable
private data class EnvelopeDto(val id: String, val tag: String, val payloadHex: String, val timestampMs: Long)

/** DTO de confirmación de recepción enviado al servidor. */
@Serializable
private data class AckRequest(val tag: String, val ids: List<String>)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Implementación HTTP/HTTPS del transporte de Relay Ciego para la red mallada federada de Aréte.
 *
 * ## Protocolo (REST sobre HTTPS)
 * - `POST /api/v1/relay/deposit` : Deposita un sobre cifrado en el buzón indicado.
 * - `GET  /api/v1/relay/fetch`   : Recupera sobres pendientes del buzón.
 * - `POST /api/v1/relay/ack`     : Confirma la recepción y solicita la eliminación de sobres.
 * - `GET  /api/v1/relay/health`  : Estado del servidor.
 *
 * ## Resiliencia Multi-Servidor
 * Soporta múltiples URLs de relay. Si el primero falla (timeout, error 5xx o red inaccesible)
 * conmuta automáticamente al siguiente, garantizando disponibilidad ante nodos caídos o censurados.
 *
 * ## Privacidad y Seguridad
 * - Solo conoce el [mailboxTagHex]: nunca ve identidades reales de usuarios.
 * - El payload es un blob opaco de bytes cifrados E2EE con padding PKCS7 antes de ser enviado.
 */
class HttpRelayTransport(
    private val relayUrlsProveedor: () -> List<String>
) : RelayTransport {

    /** Índice actual del relay activo para distribución round-robin simple. */
    private val indiceActivo = AtomicInteger(0)

    override suspend fun deposit(
        mailboxTagHex: String,
        envelopeBytes: ByteArray,
        ttlSeconds: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (envelopeBytes.size > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "Sobre excede tamaño máximo ($MAX_PAYLOAD_BYTES B): ${envelopeBytes.size} B. Descartado.")
            return@withContext false
        }

        val id = java.util.UUID.randomUUID().toString()
        val payloadHex = envelopeBytes.joinToString("") { "%02x".format(it) }
        val body = json.encodeToString(
            DepositRequest(
                tag = mailboxTagHex,
                id = id,
                payloadHex = payloadHex,
                timestampMs = System.currentTimeMillis(),
                ttlSeconds = ttlSeconds
            )
        )

        ejecutarConFailover(
            descripcion = "deposit tag=${mailboxTagHex.take(12)}..."
        ) { baseUrl ->
            post("$baseUrl/api/v1/relay/deposit", body) == 200
        }
    }

    override suspend fun fetch(
        mailboxTagHex: String,
        afterTimestampMs: Long
    ): List<RelayEnvelope> = withContext(Dispatchers.IO) {
        val resultados = mutableListOf<RelayEnvelope>()
        ejecutarConFailover(
            descripcion = "fetch tag=${mailboxTagHex.take(12)}..."
        ) { baseUrl ->
            val respuesta = get("$baseUrl/api/v1/relay/fetch?tag=$mailboxTagHex&after=$afterTimestampMs")
            if (respuesta != null) {
                runCatching {
                    val dto = json.decodeFromString<FetchResponse>(respuesta)
                    dto.envelopes.forEach { e ->
                        resultados.add(
                            RelayEnvelope(
                                id = e.id,
                                mailboxTagHex = e.tag,
                                payloadBytes = hexToBytes(e.payloadHex),
                                timestampMs = e.timestampMs
                            )
                        )
                    }
                }.onFailure { Log.w(TAG, "Error deserializando respuesta fetch: ${it.message}") }
                true
            } else {
                false
            }
        }
        resultados
    }

    override suspend fun ack(
        mailboxTagHex: String,
        envelopeIds: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (envelopeIds.isEmpty()) return@withContext true
        val body = json.encodeToString(AckRequest(tag = mailboxTagHex, ids = envelopeIds))
        ejecutarConFailover(descripcion = "ack ${envelopeIds.size} sobres") { baseUrl ->
            post("$baseUrl/api/v1/relay/ack", body) == 200
        }
    }

    // ─── Núcleo de Resiliencia Multi-Relay ────────────────────────────────────

    /**
     * Ejecuta [bloque] contra cada relay de la lista hasta que uno tenga éxito.
     * Rota el índice activo en caso de fallo para equilibrar la carga entre servidores.
     */
    private suspend fun ejecutarConFailover(
        descripcion: String,
        bloque: suspend (String) -> Boolean
    ): Boolean {
        val urls = relayUrlsProveedor()
        if (urls.isEmpty()) {
            Log.w(TAG, "No hay relays configurados para: $descripcion")
            return false
        }

        val inicio = indiceActivo.get() % urls.size
        for (i in urls.indices) {
            val idx = (inicio + i) % urls.size
            val baseUrl = urls[idx].trimEnd('/')
            try {
                val exito = bloque(baseUrl)
                if (exito) {
                    indiceActivo.set(idx)
                    return true
                }
                Log.d(TAG, "Relay $baseUrl: fallo lógico en $descripcion, probando siguiente...")
            } catch (e: Exception) {
                Log.w(TAG, "Relay $baseUrl: error de red ($descripcion): ${e.message}")
            }
        }
        Log.w(TAG, "Todos los relays fallaron para: $descripcion")
        return false
    }

    // ─── Funciones HTTP Primitivas ─────────────────────────────────────────────

    private fun post(url: String, body: String): Int? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONEXION_MS
            readTimeout = TIMEOUT_LECTURA_MS
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            doOutput = true
        }
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            conn.responseCode
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_CONEXION_MS
            readTimeout = TIMEOUT_LECTURA_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        if (len % 2 != 0) return ByteArray(0)
        val result = ByteArray(len / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}

package com.renea.arete_social.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private const val TAG = "NostrRelayTransport"
private const val TIMEOUT_MS = 10_000

/**
 * Implementación de RelayTransport sobre la red global federada de Nostr (NIP-01).
 *
 * ## ¿Por qué Nostr?
 * 1. **Público y Comunitario:** Existen miles de relays libres, abiertos y mantenidos por la comunidad global.
 * 2. **Sin dependencias pesadas:** Implementa un cliente WebSocket RFC 6455 nativo mínimo y seguro sobre SSLSocket.
 * 3. **Buzón Ciego Zero-Knowledge:**
 *    - Usa eventos efímeros/temporales de Nostr (kind 20004 o kind 1 con tags).
 *    - El tag del evento es el hash del buzón ciego (`#t`: `mailboxTagHex`).
 *    - El contenido del evento es el sobre E2EE con padding PKCS7 en Base64.
 *    - El servidor relay NUNCA conoce quién es el remitente ni el receptor ni el contenido.
 */
class NostrRelayTransport(
    private val relaysProvider: () -> List<String>
) : RelayTransport {

    private val indiceActivo = AtomicInteger(0)
    private val keyPairCache = ConcurrentHashMap<String, NostrCrypto.KeyPairSecp256k1>()
    // Limitar conexiones simultáneas a relays para no saturar el stack de red del dispositivo
    private val semaforoConexiones = Semaphore(3)

    override suspend fun deposit(
        mailboxTagHex: String,
        envelopeBytes: ByteArray,
        ttlSeconds: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val uris = obtenerRelaysUris()
        if (uris.isEmpty()) return@withContext false

        val payloadBase64 = base64Encode(envelopeBytes)
        val eventIdUuid = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis() / 1000

        // Claves secp256k1 deterministas derivadas del tag del buzón ciego (cacheadas por tag)
        val keyPair = keyPairCache.getOrPut(mailboxTagHex) {
            NostrCrypto.derivarClavesDesdeSemilla(mailboxTagHex)
        }
        val tagsList = listOf(
            listOf("t", mailboxTagHex),              // tag de buzón ciego para búsqueda por #t
            listOf("p", keyPair.pubKeyHex),          // pubkey del buzón para indexado NIP-59 por #p
            listOf("e_id", eventIdUuid),
            listOf("expiration", (timestamp + ttlSeconds).toString())
        )
        val kind = 1059 // Kind 1059: Gift Wrap NIP-59 (almacenado, E2EE, diseñado para DMs cifrados en relay)

        val canonico = NostrCrypto.serializarEventoCanónico(
            pubkey = keyPair.pubKeyHex,
            createdAt = timestamp,
            kind = kind,
            tags = tagsList,
            content = payloadBase64
        )
        val msgHash = NostrCrypto.sha256Bytes(canonico.toByteArray(Charsets.UTF_8))
        val eventIdHash = NostrCrypto.bytesAHex(msgHash)
        val sigHex = NostrCrypto.firmarBip340(msgHash, keyPair.privKeyHex)

        val eventObj = buildJsonObject {
            put("id", eventIdHash)
            put("pubkey", keyPair.pubKeyHex)
            put("created_at", timestamp)
            put("kind", kind)
            putJsonArray("tags") {
                tagsList.forEach { tag ->
                    addJsonArray {
                        tag.forEach { add(it) }
                    }
                }
            }
            put("content", payloadBase64)
            put("sig", sigHex)
        }

        val nostrMsg = buildJsonArray {
            add("EVENT")
            add(eventObj)
        }.toString()

        val confirmados = coroutineScope {
            uris.map { uri ->
                async(Dispatchers.IO) {
                    semaforoConexiones.withPermit {
                        try {
                            enviarEventoNostr(uri, nostrMsg, eventIdHash)
                        } catch (e: Exception) {
                            Log.d(TAG, "Error enviando a relay ${uri.host}: ${e.message}")
                            false
                        }
                    }
                }
            }.awaitAll().count { it }
        }
        Log.d(TAG, "Depósito en buzón ${mailboxTagHex.take(12)}... aceptado por $confirmados/${uris.size} relays")
        confirmados > 0
    }

    override suspend fun fetch(
        mailboxTagHex: String,
        afterTimestampMs: Long
    ): List<RelayEnvelope> = withContext(Dispatchers.IO) {
        val uris = obtenerRelaysUris()
        if (uris.isEmpty()) return@withContext emptyList()

        val mapaResultados = ConcurrentHashMap<String, RelayEnvelope>()
        val sinceSec = afterTimestampMs / 1000
        val subId = "sub_${java.util.UUID.randomUUID().toString().take(8)}"

        // Derivar el pubkey del buzón (mismo método determinista que en deposit)
        val keyPairBuzon = keyPairCache.getOrPut(mailboxTagHex) {
            NostrCrypto.derivarClavesDesdeSemilla(mailboxTagHex)
        }

        // Filtro REQ: buscar por #t (tag Aréte) y #p (pubkey del buzón, NIP-59) para max compatibilidad
        val filterObj = buildJsonObject {
            putJsonArray("#t") { add(mailboxTagHex) }
            putJsonArray("#p") { add(keyPairBuzon.pubKeyHex) }
            putJsonArray("kinds") { add(1059) }
            if (sinceSec > 0) put("since", sinceSec)
            put("limit", 50)
        }
        val reqMsg = buildJsonArray {
            add("REQ")
            add(subId)
            add(filterObj)
        }.toString()

        // Multi-relay fetch concurrente: consultar todos los relays en paralelo y deduplicar por ID
        val listasPorRelay = coroutineScope {
            uris.map { uri ->
                async(Dispatchers.IO) {
                    semaforoConexiones.withPermit {
                        try {
                            consultarEventosNostr(uri, reqMsg, subId)
                        } catch (e: Exception) {
                            Log.d(TAG, "Error consultando relay ${uri.host}: ${e.message}")
                            emptyList<JsonObject>()
                        }
                    }
                }
            }.awaitAll()
        }

        for (eventos in listasPorRelay) {
            for (ev in eventos) {
                try {
                    val id = ev["id"]?.jsonPrimitive?.content ?: ""
                    if (id.isNotBlank() && !mapaResultados.containsKey(id)) {
                        val contentBase64 = ev["content"]?.jsonPrimitive?.content ?: ""
                        val bytes = base64Decode(contentBase64)
                        val ts = (ev["created_at"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000
                        mapaResultados[id] = RelayEnvelope(
                            id = id,
                            mailboxTagHex = mailboxTagHex,
                            payloadBytes = bytes,
                            timestampMs = ts
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parseando sobre de relay: ${e.message}")
                }
            }
        }
        mapaResultados.values.toList()
    }

    override suspend fun ack(mailboxTagHex: String, envelopeIds: List<String>): Boolean {
        // En Nostr los eventos expiran por el tag 'expiration' NIP-40. Confirmación local.
        return true
    }

    // ─── Resiliencia y Selección de Relays ────────────────────────────────────

    private fun obtenerRelaysUris(): List<URI> {
        val urls = relaysProvider()
        return urls.mapNotNull { raw ->
            val clean = raw.trim().trimEnd('/')
            if (clean.isBlank()) return@mapNotNull null
            val uriStr = when {
                clean.startsWith("wss://") || clean.startsWith("ws://") -> clean
                clean.startsWith("https://") -> clean.replace("https://", "wss://")
                clean.startsWith("http://") -> clean.replace("http://", "ws://")
                else -> "wss://$clean"
            }
            runCatching { URI(uriStr) }.getOrNull()
        }
    }

    // ─── WebSocket RFC 6455 Nativo Mínimo ───────────────────────────────────────

    private fun enviarEventoNostr(uri: URI, mensaje: String, eventIdEsperado: String): Boolean {
        var socket: Socket? = null
        try {
            socket = abrirWebSocket(uri)
            val os = socket.getOutputStream()
            val isStream = socket.getInputStream()

            escribirFrameTextoWs(os, mensaje)

            // Esperar confirmación NIP-20 ("OK", event_id, true/false, message)
            socket.soTimeout = 4000
            val limiteTiempo = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < limiteTiempo) {
                val frame = leerFrameWs(isStream) ?: break
                try {
                    val elem = Json.parseToJsonElement(frame)
                    val arr = elem.jsonArray
                    val tipo = arr.getOrNull(0)?.jsonPrimitive?.content
                    if (tipo == "OK") {
                        val idRecibido = arr.getOrNull(1)?.jsonPrimitive?.content ?: ""
                        val aceptado = arr.getOrNull(2)?.jsonPrimitive?.booleanOrNull == true
                        val motivo = arr.getOrNull(3)?.jsonPrimitive?.content ?: ""
                        if (idRecibido.isBlank() || idRecibido == eventIdEsperado) {
                            if (aceptado) {
                                Log.d(TAG, "Relay ${uri.host} aceptó evento ${eventIdEsperado.take(8)}")
                                return true
                            } else {
                                Log.w(TAG, "Relay ${uri.host} RECHAZÓ evento ${eventIdEsperado.take(8)}: $motivo")
                                return false
                            }
                        }
                    } else if (tipo == "NOTICE") {
                        Log.i(TAG, "Aviso de relay ${uri.host}: ${arr.getOrNull(1)?.jsonPrimitive?.content}")
                    }
                } catch (_: Exception) {
                    // Frame no es JSON o es intermedio, continuar leyendo
                }
            }
            return false
        } catch (e: Exception) {
            Log.d(TAG, "Excepción enviando a relay ${uri.host}: ${e.message}")
            return false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun consultarEventosNostr(uri: URI, reqMensaje: String, subId: String): List<JsonObject> {
        val lista = mutableListOf<JsonObject>()
        var socket: Socket? = null
        try {
            socket = abrirWebSocket(uri)
            val os = socket.getOutputStream()
            val isStream = socket.getInputStream()

            escribirFrameTextoWs(os, reqMensaje)
            socket.soTimeout = 4000

            val limiteTiempo = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < limiteTiempo) {
                val frame = leerFrameWs(isStream) ?: break
                runCatching {
                    val arr = Json.parseToJsonElement(frame).jsonArray
                    val tipo = arr.getOrNull(0)?.jsonPrimitive?.content
                    val idSub = arr.getOrNull(1)?.jsonPrimitive?.content
                    if (tipo == "EVENT" && idSub == subId) {
                        val evObj = arr.getOrNull(2)?.jsonObject
                        if (evObj != null) lista.add(evObj)
                    } else if (tipo == "EOSE" && idSub == subId) {
                        // Fin de eventos almacenados
                        return lista
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Excepción consultando relay ${uri.host}: ${e.message}")
        } finally {
            runCatching { socket?.close() }
        }
        return lista
    }

    private fun abrirWebSocket(uri: URI): Socket {
        val esSsl = uri.scheme.equals("wss", ignoreCase = true)
        val port = if (uri.port != -1) uri.port else if (esSsl) 443 else 80
        val host = uri.host

        val socket = if (esSsl) {
            val baseSocket = Socket()
            baseSocket.connect(java.net.InetSocketAddress(host, port), 4000)
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = (sslFactory.createSocket(baseSocket, host, port, true) as SSLSocket).apply {
                runCatching {
                    val params = sslParameters
                    params.serverNames = listOf(SNIHostName(host))
                    sslParameters = params
                }
            }
            sslSocket.startHandshake()
            sslSocket
        } else {
            val s = Socket()
            s.connect(java.net.InetSocketAddress(host, port), 4000)
            s
        }
        socket.soTimeout = 4000

        val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val secKey = base64Encode(keyBytes)
        val path = if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath

        val handshake = "GET $path HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $secKey\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Origin: https://$host\r\n" +
                "User-Agent: Arete-NostrRelay/1.0\r\n\r\n"

        socket.getOutputStream().write(handshake.toByteArray(StandardCharsets.US_ASCII))
        socket.getOutputStream().flush()

        // Leer encabezados HTTP de respuesta hasta "\r\n\r\n"
        val isStream = socket.getInputStream()
        val headerBuf = StringBuilder()
        var c: Int
        while (isStream.read().also { c = it } != -1) {
            headerBuf.append(c.toChar())
            if (headerBuf.endsWith("\r\n\r\n")) break
        }
        if (!headerBuf.contains("101")) {
            socket.close()
            throw IllegalStateException("Handshake WS falló: $headerBuf")
        }
        return socket
    }

    private fun escribirFrameTextoWs(os: OutputStream, texto: String) {
        val payload = texto.toByteArray(StandardCharsets.UTF_8)
        val maskKey = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val masked = ByteArray(payload.size)
        for (i in payload.indices) {
            masked[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
        }

        val frame = ByteBuffer.allocate(10 + masked.size)
        frame.put(0x81.toByte()) // FIN + Opcode Text (1)

        val len = payload.size
        when {
            len <= 125 -> {
                frame.put((0x80 or len).toByte()) // MASK bit = 1
            }
            len <= 65535 -> {
                frame.put((0x80 or 126).toByte())
                frame.putShort(len.toShort())
            }
            else -> {
                frame.put((0x80 or 127).toByte())
                frame.putLong(len.toLong())
            }
        }
        frame.put(maskKey)
        frame.put(masked)

        os.write(frame.array(), 0, frame.position())
        os.flush()
    }

    private fun leerFrameWs(isStream: InputStream): String? {
        val b1 = isStream.read()
        if (b1 == -1) return null
        val b2 = isStream.read()
        if (b2 == -1) return null

        val isMasked = (b2 and 0x80) != 0
        var payloadLen = (b2 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val h = isStream.read()
            val l = isStream.read()
            if (h == -1 || l == -1) return null
            payloadLen = ((h shl 8) or l).toLong()
        } else if (payloadLen == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                val b = isStream.read()
                if (b == -1) return null
                len = (len shl 8) or (b.toLong() and 0xFF)
            }
            payloadLen = len
        }

        if (payloadLen > 256 * 1024) return null // Máximo 256KB por frame

        val maskKey = if (isMasked) {
            val key = ByteArray(4)
            var read = 0
            while (read < 4) {
                val r = isStream.read(key, read, 4 - read)
                if (r == -1) return null
                read += r
            }
            key
        } else null

        val data = ByteArray(payloadLen.toInt())
        var totalLeido = 0
        while (totalLeido < data.size) {
            val r = isStream.read(data, totalLeido, data.size - totalLeido)
            if (r == -1) break
            totalLeido += r
        }

        if (maskKey != null) {
            for (i in data.indices) {
                data[i] = (data[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        return String(data, StandardCharsets.UTF_8)
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun base64Encode(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    private fun base64Decode(str: String): ByteArray {
        return java.util.Base64.getDecoder().decode(str.trim())
    }
}

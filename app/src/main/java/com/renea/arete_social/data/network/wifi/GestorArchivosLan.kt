package com.renea.arete_social.data.network.wifi

import android.content.Context
import android.util.Base64
import android.util.Log
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.feature.comunidades.ArchivoComunidad
import com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de transferencia de archivos pesados de comunidad a través de Wi-Fi LAN (TCP).
 * Evita la saturación del ancho de banda y las limitaciones de MTU del Bluetooth BLE.
 */
class GestorArchivosLan(
    private val context: Context,
    private val router: MessageRouter,
    private val miIdProveedor: () -> String?
) {
    companion object {
        private const val TAG = "GestorArchivosLan"
        const val CHAT_PREFIX = "com-files-lan:"
        private const val CHUNK_SIZE_BYTES = 48 * 1024 // 48 KB por chunk TCP
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Buffer de ensamblado: archivoId -> Map<chunkIndex, bytes>
    private val bufferChunks = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
    private val totalChunksEsperados = ConcurrentHashMap<String, Int>()

    fun iniciar() {
        scope.launch {
            router.todosLosPaquetesEntrantes()
                .filter { it.chatId.startsWith(CHAT_PREFIX) }
                .catch { Log.w(TAG, "Error en flujo de archivos LAN", it) }
                .collect { paquete -> procesarPaquete(paquete) }
        }
    }

    private suspend fun procesarPaquete(paquete: PaqueteMesh) {
        val payload = paquete.contenido
        when {
            payload.startsWith("REQ_LIST:") -> {
                val comunidadId = payload.removePrefix("REQ_LIST:").trim()
                responderListaArchivos(comunidadId, paquete.remitenteId)
            }
            payload.startsWith("RESP_LIST:") -> {
                val resto = payload.removePrefix("RESP_LIST:")
                val primerSeparador = resto.indexOf(':')
                if (primerSeparador > 0) {
                    val comunidadId = resto.substring(0, primerSeparador)
                    val rawJson = resto.substring(primerSeparador + 1)
                    procesarListaRecibida(comunidadId, rawJson)
                }
            }
            payload.startsWith("REQ_FILE:") -> {
                // REQ_FILE:<comunidadId>:<archivoId>
                val partes = payload.removePrefix("REQ_FILE:").split(':')
                if (partes.size >= 2) {
                    val comunidadId = partes[0]
                    val archivoId = partes[1]
                    enviarArchivoEnChunks(comunidadId, archivoId, paquete.remitenteId)
                }
            }
            payload.startsWith("CHUNK_FILE:") -> {
                // CHUNK_FILE:<comunidadId>:<archivoId>:<chunkIndex>:<totalChunks>:<base64>
                val partes = payload.removePrefix("CHUNK_FILE:").split(':', limit = 5)
                if (partes.size == 5) {
                    val comunidadId = partes[0]
                    val archivoId = partes[1]
                    val chunkIndex = partes[2].toIntOrNull() ?: return
                    val totalChunks = partes[3].toIntOrNull() ?: return
                    val base64Data = partes[4]
                    recibirChunk(comunidadId, archivoId, chunkIndex, totalChunks, base64Data)
                }
            }
        }
    }

    /** Solicita el inventario de archivos de una comunidad a un peer por LAN. */
    fun solicitarListaArchivos(comunidadId: String, destinoPeerId: String) {
        val miId = miIdProveedor() ?: return
        val paquete = PaqueteMesh(
            id = UUID.randomUUID().toString(),
            chatId = "$CHAT_PREFIX$comunidadId",
            remitenteId = miId,
            destinatarioId = destinoPeerId,
            contenido = "REQ_LIST:$comunidadId",
            timestamp = System.currentTimeMillis()
        )
        scope.launch { router.enviarADestino(destinoPeerId, paquete) }
    }

    /** Solicita la descarga directa de un archivo binario por LAN. */
    fun descargarArchivo(comunidadId: String, archivoId: String, destinoPeerId: String) {
        val miId = miIdProveedor() ?: return
        val paquete = PaqueteMesh(
            id = UUID.randomUUID().toString(),
            chatId = "$CHAT_PREFIX$comunidadId",
            remitenteId = miId,
            destinatarioId = destinoPeerId,
            contenido = "REQ_FILE:$comunidadId:$archivoId",
            timestamp = System.currentTimeMillis()
        )
        scope.launch { router.enviarADestino(destinoPeerId, paquete) }
    }

    private suspend fun responderListaArchivos(comunidadId: String, destinatarioId: String) {
        GestorMuroComunidades.inicializar(context)
        val archivos = GestorMuroComunidades.obtenerArchivos(comunidadId)
        if (archivos.isEmpty()) return
        val miId = miIdProveedor() ?: return
        val rawJson = runCatching { json.encodeToString(archivos) }.getOrNull() ?: return

        val paquete = PaqueteMesh(
            id = UUID.randomUUID().toString(),
            chatId = "$CHAT_PREFIX$comunidadId",
            remitenteId = miId,
            destinatarioId = destinatarioId,
            contenido = "RESP_LIST:$comunidadId:$rawJson",
            timestamp = System.currentTimeMillis()
        )
        router.enviarADestino(destinatarioId, paquete)
    }

    private fun procesarListaRecibida(comunidadId: String, rawJson: String) {
        GestorMuroComunidades.inicializar(context)
        val recibidos = runCatching { json.decodeFromString<List<ArchivoComunidad>>(rawJson) }.getOrDefault(emptyList())
        for (archivo in recibidos) {
            GestorMuroComunidades.guardarArchivo(comunidadId, archivo)
        }
    }

    private suspend fun enviarArchivoEnChunks(comunidadId: String, archivoId: String, destinatarioId: String) {
        val dir = File(context.filesDir, "media/comunidades/$comunidadId")
        val file = File(dir, "$archivoId.dat")
        if (!file.exists()) return

        val miId = miIdProveedor() ?: return
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return
        val totalChunks = ((bytes.size + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).coerceAtLeast(1)

        for (i in 0 until totalChunks) {
            val start = i * CHUNK_SIZE_BYTES
            val end = minOf(start + CHUNK_SIZE_BYTES, bytes.size)
            val chunk = bytes.copyOfRange(start, end)
            val base64 = Base64.encodeToString(chunk, Base64.NO_WRAP)

            val paquete = PaqueteMesh(
                id = UUID.randomUUID().toString(),
                chatId = "$CHAT_PREFIX$comunidadId",
                remitenteId = miId,
                destinatarioId = destinatarioId,
                contenido = "CHUNK_FILE:$comunidadId:$archivoId:$i:$totalChunks:$base64",
                timestamp = System.currentTimeMillis()
            )
            router.enviarADestino(destinatarioId, paquete)
        }
    }

    private fun recibirChunk(
        comunidadId: String,
        archivoId: String,
        chunkIndex: Int,
        totalChunks: Int,
        base64Data: String
    ) {
        val bytes = runCatching { Base64.decode(base64Data, Base64.NO_WRAP) }.getOrNull() ?: return
        val map = bufferChunks.getOrPut(archivoId) { ConcurrentHashMap() }
        map[chunkIndex] = bytes
        totalChunksEsperados[archivoId] = totalChunks

        if (map.size == totalChunks) {
            // Todos los chunks recibidos: reensamblar y escribir en disco
            val bos = ByteArrayOutputStream()
            for (i in 0 until totalChunks) {
                val b = map[i] ?: return
                bos.write(b)
            }
            val completo = bos.toByteArray()
            bufferChunks.remove(archivoId)
            totalChunksEsperados.remove(archivoId)

            val dir = File(context.filesDir, "media/comunidades/$comunidadId")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "$archivoId.dat")
            runCatching { destFile.writeBytes(completo) }

            // Actualizar URL del archivo en GestorMuroComunidades si ya existe en la lista
            GestorMuroComunidades.inicializar(context)
            val actuales = GestorMuroComunidades.obtenerArchivos(comunidadId)
            val archivoExistente = actuales.firstOrNull { it.id == archivoId }
            if (archivoExistente != null) {
                GestorMuroComunidades.guardarArchivo(
                    comunidadId,
                    archivoExistente.copy(url = destFile.absolutePath, tamanoBytes = completo.size.toLong())
                )
            }
            Log.i(TAG, "Archivo $archivoId recibido y ensamblado con éxito sobre Wi-Fi LAN (${completo.size} bytes)")
        }
    }
}

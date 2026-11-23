package com.renea.arete_social.data.mesh.sync

import com.renea.arete_social.data.protocol.OperationEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Protocolo de Sincronización por Inventario de Comunidades (P2P Mesh).
 *
 * En lugar de inundar la red con mensajes completos repetidos:
 * 1. Los pares intercambian un [InventorySummary] (cabeza del dueño y resumen compacto de secuencias/hashes).
 * 2. Si detectan discrepancias causales o huecos, solicitan únicamente las operaciones faltantes vía [InventoryRequest].
 * 3. El par responde con [InventoryResponse] conteniendo los sobres binarios canónicos.
 */
object CommunitySyncProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class InventorySummary(
        val communityIdHex: String,
        val ownerHeadSeq: Long,
        val ownerHeadHashHex: String,
        val knownDeviceSeqs: Map<String, Long> = emptyMap(), // deviceId -> maxDeviceSeq conocida
        val bloomFilterBase64: String? = null                // Filtro de Bloom opcional para opIds
    )

    @Serializable
    data class InventoryRequest(
        val communityIdHex: String,
        val missingOpIdsHex: List<String> = emptyList(),
        val fromOwnerSeq: Long? = null,
        val toOwnerSeq: Long? = null
    )

    @Serializable
    data class InventoryResponse(
        val communityIdHex: String,
        val operationsHex: List<String> // Sobres serializados completos en formato encodeFullOperation() hex
    )

    fun serializeSummary(summary: InventorySummary): ByteArray {
        return json.encodeToString(InventorySummary.serializer(), summary).toByteArray(Charsets.UTF_8)
    }

    fun deserializeSummary(bytes: ByteArray): InventorySummary {
        return json.decodeFromString(InventorySummary.serializer(), String(bytes, Charsets.UTF_8))
    }

    fun serializeRequest(request: InventoryRequest): ByteArray {
        return json.encodeToString(InventoryRequest.serializer(), request).toByteArray(Charsets.UTF_8)
    }

    fun deserializeRequest(bytes: ByteArray): InventoryRequest {
        return json.decodeFromString(InventoryRequest.serializer(), String(bytes, Charsets.UTF_8))
    }

    fun serializeResponse(response: InventoryResponse): ByteArray {
        return json.encodeToString(InventoryResponse.serializer(), response).toByteArray(Charsets.UTF_8)
    }

    fun deserializeResponse(bytes: ByteArray): InventoryResponse {
        return json.decodeFromString(InventoryResponse.serializer(), String(bytes, Charsets.UTF_8))
    }

    /**
     * Calcula qué operaciones le faltan al par remoto comparando el inventario local con el recibido.
     */
    fun computeMissingOps(
        localOperations: List<OperationEnvelope>,
        remoteSummary: InventorySummary
    ): List<OperationEnvelope> {
        val missing = mutableListOf<OperationEnvelope>()

        for (op in localOperations) {
            // Si la operación del dueño es más reciente que la cabeza reportada por el remoto
            if (op.ownerHeadSeq > remoteSummary.ownerHeadSeq) {
                missing.add(op)
                continue
            }

            // Si es una operación de un dispositivo cuya secuencia supera la conocida remotamente
            val devId = op.deviceId.toString()
            val remoteKnownSeq = remoteSummary.knownDeviceSeqs[devId] ?: 0L
            if (op.deviceSeq > remoteKnownSeq) {
                missing.add(op)
            }
        }

        return missing
    }
}

package com.renea.arete_social.data.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import com.renea.arete_social.domain.util.toHex

/**
 * Representa el sobre canónico inmutable de una operación en el protocolo Aréte v1.
 *
 * Principio rector: Se firman los bytes exactos que viajan por el transporte.
 * Nunca se vuelve a serializar un objeto reconstruido para verificar su firma.
 */
data class OperationEnvelope(
    val version: Short = PROTOCOL_VERSION_1,
    val operationType: Short,
    val communityId: ByteArray, // 32 bytes
    val authorIk: ByteArray,    // 65 bytes (P-256 sin comprimir: 0x04 || X || Y)
    val deviceId: UUID,         // 16 bytes
    val deviceEpoch: Int,       // 4 bytes
    val deviceSeq: Long,        // 8 bytes (monótona por dispositivo)
    val prevOpHash: ByteArray,  // 32 bytes
    val ownerHeadSeq: Long,     // 8 bytes
    val ownerHeadHash: ByteArray,// 32 bytes
    val epochId: ByteArray,     // 32 bytes (0x00... si no aplica)
    val timestamp: Long,        // 8 bytes (Epoch millis)
    val bodyBytes: ByteArray,   // Máximo MAX_BODY_BYTES (8 KB)
    val signatureBytes: ByteArray = ByteArray(0) // Firma de DK-s sobre DOMAIN_V1 || envelopeBytes
) {
    init {
        require(communityId.size == 32) { "communityId debe tener exactamente 32 bytes" }
        require(authorIk.size == 65) { "authorIk debe tener exactamente 65 bytes (P-256 no comprimida)" }
        require(prevOpHash.size == 32) { "prevOpHash debe tener exactamente 32 bytes" }
        require(ownerHeadHash.size == 32) { "ownerHeadHash debe tener exactamente 32 bytes" }
        require(epochId.size == 32) { "epochId debe tener exactamente 32 bytes" }
        require(bodyBytes.size <= MAX_BODY_BYTES) { "bodyBytes excede el límite de $MAX_BODY_BYTES bytes (${bodyBytes.size})" }
        require(signatureBytes.size <= MAX_SIGNATURE_BYTES) { "signatureBytes excede el límite de $MAX_SIGNATURE_BYTES bytes (${signatureBytes.size})" }
    }

    /**
     * Serializa los campos del sobre sin la firma.
     * Estos son los bytes exactos que entran al cálculo de la firma con el separador de dominio.
     */
    fun encodeEnvelopeBytes(): ByteArray {
        val totalEnvelopeLength = HEADER_FIXED_SIZE + bodyBytes.size
        val buffer = ByteBuffer.allocate(totalEnvelopeLength).order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(version)
        buffer.putShort(operationType)
        buffer.put(communityId)
        buffer.put(authorIk)
        buffer.putLong(deviceId.mostSignificantBits)
        buffer.putLong(deviceId.leastSignificantBits)
        buffer.putInt(deviceEpoch)
        buffer.putLong(deviceSeq)
        buffer.put(prevOpHash)
        buffer.putLong(ownerHeadSeq)
        buffer.put(ownerHeadHash)
        buffer.put(epochId)
        buffer.putLong(timestamp)
        buffer.putInt(bodyBytes.size)
        buffer.put(bodyBytes)

        return buffer.array()
    }

    /**
     * Bytes que se presentan a la clave privada del dispositivo para firma:
     * "arete/op/v1" || encodeEnvelopeBytes()
     */
    fun bytesToSign(): ByteArray {
        val domainBytes = DOMAIN_SEPARATOR_V1.toByteArray(Charsets.UTF_8)
        val envBytes = encodeEnvelopeBytes()
        val result = ByteArray(domainBytes.size + envBytes.size)
        System.arraycopy(domainBytes, 0, result, 0, domainBytes.size)
        System.arraycopy(envBytes, 0, result, domainBytes.size, envBytes.size)
        return result
    }

    /**
     * Serializa la operación completa incluyendo la firma (sobre completo para transmisión/almacenamiento).
     */
    fun encodeFullOperation(): ByteArray {
        val envBytes = encodeEnvelopeBytes()
        val totalLength = envBytes.size + 2 + signatureBytes.size
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)
        buffer.put(envBytes)
        buffer.putShort(signatureBytes.size.toShort())
        buffer.put(signatureBytes)
        return buffer.array()
    }

    /**
     * Identificador criptográfico único de la operación: SHA-256(encodeFullOperation())
     */
    fun calculateOpId(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(encodeFullOperation())
    }

    fun calculateOpIdHex(): String = calculateOpId().toHex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OperationEnvelope

        if (version != other.version) return false
        if (operationType != other.operationType) return false
        if (!communityId.contentEquals(other.communityId)) return false
        if (!authorIk.contentEquals(other.authorIk)) return false
        if (deviceId != other.deviceId) return false
        if (deviceEpoch != other.deviceEpoch) return false
        if (deviceSeq != other.deviceSeq) return false
        if (!prevOpHash.contentEquals(other.prevOpHash)) return false
        if (ownerHeadSeq != other.ownerHeadSeq) return false
        if (!ownerHeadHash.contentEquals(other.ownerHeadHash)) return false
        if (!epochId.contentEquals(other.epochId)) return false
        if (timestamp != other.timestamp) return false
        if (!bodyBytes.contentEquals(other.bodyBytes)) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + operationType.toInt()
        result = 31 * result + communityId.contentHashCode()
        result = 31 * result + authorIk.contentHashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + deviceEpoch
        result = 31 * result + deviceSeq.hashCode()
        result = 31 * result + prevOpHash.contentHashCode()
        result = 31 * result + ownerHeadSeq.hashCode()
        result = 31 * result + ownerHeadHash.contentHashCode()
        result = 31 * result + epochId.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + bodyBytes.contentHashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }

    companion object {
        const val PROTOCOL_VERSION_1: Short = 1
        const val DOMAIN_SEPARATOR_V1: String = "arete/op/v1"

        // Tamaños fijos en bytes del encabezado:
        // version (2) + operationType (2) + communityId (32) + authorIk (65) +
        // deviceId (16) + deviceEpoch (4) + deviceSeq (8) + prevOpHash (32) +
        // ownerHeadSeq (8) + ownerHeadHash (32) + epochId (32) + timestamp (8) + bodyLength (4) = 245 bytes
        const val HEADER_FIXED_SIZE: Int = 2 + 2 + 32 + 65 + 16 + 4 + 8 + 32 + 8 + 32 + 32 + 8 + 4

        const val MAX_BODY_BYTES: Int = 8192 // 8 KB según sección 9.6 de auditoría
        const val MAX_SIGNATURE_BYTES: Int = 72 // P-256 DER signature (máx 72 bytes)

        // Tipos de operaciones de la columna del dueño
        const val TYPE_GENESIS: Short = 0x0001
        const val TYPE_RULE_CHANGE: Short = 0x0002
        const val TYPE_LEADER_DESIGNATE: Short = 0x0003
        const val TYPE_LEADER_REVOKE: Short = 0x0004
        const val TYPE_REVOKE_SUBTREE: Short = 0x0005
        const val TYPE_OWNERSHIP_TRANSFER: Short = 0x0006
        const val TYPE_HEIR_DESIGNATE: Short = 0x0007
        const val TYPE_HEIR_CANCEL: Short = 0x0008
        const val TYPE_CHECKPOINT: Short = 0x0009
        const val TYPE_DISSOLVE: Short = 0x000A

        // Tipos de operaciones delegadas
        const val TYPE_ADMIN_DESIGNATE: Short = 0x0010
        const val TYPE_MODERATOR_DESIGNATE: Short = 0x0011
        const val TYPE_ROLE_REVOKE: Short = 0x0012
        const val TYPE_JOIN_ACCEPT: Short = 0x0013
        const val TYPE_JOIN_REJECT: Short = 0x0014
        const val TYPE_EXPEL: Short = 0x0015
        const val TYPE_EPOCH_ROTATE: Short = 0x0016
        const val TYPE_APPEAL_DECISION: Short = 0x0017
        const val TYPE_HEIR_CLAIM: Short = 0x0018

        // Tipos de operaciones de miembro
        const val TYPE_JOIN_REQUEST: Short = 0x0020
        const val TYPE_LEAVE: Short = 0x0021
        const val TYPE_APPEAL_REQUEST: Short = 0x0022
        const val TYPE_CONTENT_POST: Short = 0x0023
        const val TYPE_REACTION_OR_ACK: Short = 0x0024

        fun decode(bytes: ByteArray): OperationEnvelope {
            require(bytes.size >= HEADER_FIXED_SIZE + 2) { "Longitud de datos insuficiente para OperationEnvelope (${bytes.size})" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val version = buffer.short
            val operationType = buffer.short

            val communityId = ByteArray(32)
            buffer.get(communityId)

            val authorIk = ByteArray(65)
            buffer.get(authorIk)

            val deviceIdMsb = buffer.long
            val deviceIdLsb = buffer.long
            val deviceId = UUID(deviceIdMsb, deviceIdLsb)

            val deviceEpoch = buffer.int
            val deviceSeq = buffer.long

            val prevOpHash = ByteArray(32)
            buffer.get(prevOpHash)

            val ownerHeadSeq = buffer.long

            val ownerHeadHash = ByteArray(32)
            buffer.get(ownerHeadHash)

            val epochId = ByteArray(32)
            buffer.get(epochId)

            val timestamp = buffer.long

            val bodyLength = buffer.int
            require(bodyLength in 0..MAX_BODY_BYTES) { "bodyLength inválido: $bodyLength" }
            val bodyBytes = ByteArray(bodyLength)
            buffer.get(bodyBytes)

            val signatureLength = buffer.short.toInt() and 0xFFFF
            require(signatureLength in 0..MAX_SIGNATURE_BYTES) { "signatureLength inválido: $signatureLength" }
            val signatureBytes = ByteArray(signatureLength)
            buffer.get(signatureBytes)

            return OperationEnvelope(
                version = version,
                operationType = operationType,
                communityId = communityId,
                authorIk = authorIk,
                deviceId = deviceId,
                deviceEpoch = deviceEpoch,
                deviceSeq = deviceSeq,
                prevOpHash = prevOpHash,
                ownerHeadSeq = ownerHeadSeq,
                ownerHeadHash = ownerHeadHash,
                epochId = epochId,
                timestamp = timestamp,
                bodyBytes = bodyBytes,
                signatureBytes = signatureBytes
            )
        }
    }
}

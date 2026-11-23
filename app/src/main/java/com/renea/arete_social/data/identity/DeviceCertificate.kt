package com.renea.arete_social.data.identity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Certificado de dispositivo según la sección 8.1 de la auditoría arquitectónica v3.
 *
 * Vincula las claves efímeras del dispositivo (DK-s y DK-a) con la Clave de Identidad (IK)
 * soberana del usuario, firmado exclusivamente por la IK.
 */
data class DeviceCertificate(
    val version: Short = 1,
    val authorIk: ByteArray,    // 65 bytes P-256 no comprimida
    val deviceId: UUID,         // 16 bytes
    val deviceEpoch: Int,       // 4 bytes
    val dksPublicKey: ByteArray,// X.509 DER de la clave de firma del dispositivo
    val dkaPublicKey: ByteArray,// X.509 DER de la clave de acuerdo del dispositivo
    val validFromMillis: Long,  // 8 bytes
    val validUntilMillis: Long, // 8 bytes (0 = indefinido)
    val ikSignature: ByteArray  // Firma de la IK sobre "arete/cert/v1" || certPayloadBytes
) {
    init {
        require(authorIk.size == 65) { "authorIk debe tener 65 bytes" }
        require(dksPublicKey.isNotEmpty()) { "dksPublicKey no puede estar vacía" }
        require(dkaPublicKey.isNotEmpty()) { "dkaPublicKey no puede estar vacía" }
    }

    fun encodePayloadBytes(): ByteArray {
        val totalLength = 2 + 65 + 16 + 4 + 2 + dksPublicKey.size + 2 + dkaPublicKey.size + 8 + 8
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(version)
        buffer.put(authorIk)
        buffer.putLong(deviceId.mostSignificantBits)
        buffer.putLong(deviceId.leastSignificantBits)
        buffer.putInt(deviceEpoch)

        buffer.putShort(dksPublicKey.size.toShort())
        buffer.put(dksPublicKey)

        buffer.putShort(dkaPublicKey.size.toShort())
        buffer.put(dkaPublicKey)

        buffer.putLong(validFromMillis)
        buffer.putLong(validUntilMillis)

        return buffer.array()
    }

    fun bytesToSign(): ByteArray {
        val domain = DOMAIN_SEPARATOR.toByteArray(Charsets.UTF_8)
        val payload = encodePayloadBytes()
        val result = ByteArray(domain.size + payload.size)
        System.arraycopy(domain, 0, result, 0, domain.size)
        System.arraycopy(payload, 0, result, domain.size, payload.size)
        return result
    }

    fun verifyCertificate(ikPublicKey: PublicKey): Boolean {
        if (ikSignature.isEmpty()) return false
        return try {
            val bytes = bytesToSign()
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(ikPublicKey)
            verifier.update(bytes)
            verifier.verify(ikSignature)
        } catch (_: Exception) {
            false
        }
    }

    fun getDksPublicKeyObject(): PublicKey? {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(X509EncodedKeySpec(dksPublicKey))
        } catch (_: Exception) {
            null
        }
    }

    fun getDkaPublicKeyObject(): PublicKey? {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(X509EncodedKeySpec(dkaPublicKey))
        } catch (_: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceCertificate

        if (version != other.version) return false
        if (!authorIk.contentEquals(other.authorIk)) return false
        if (deviceId != other.deviceId) return false
        if (deviceEpoch != other.deviceEpoch) return false
        if (!dksPublicKey.contentEquals(other.dksPublicKey)) return false
        if (!dkaPublicKey.contentEquals(other.dkaPublicKey)) return false
        if (validFromMillis != other.validFromMillis) return false
        if (validUntilMillis != other.validUntilMillis) return false
        if (!ikSignature.contentEquals(other.ikSignature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + authorIk.contentHashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + deviceEpoch
        result = 31 * result + dksPublicKey.contentHashCode()
        result = 31 * result + dkaPublicKey.contentHashCode()
        result = 31 * result + validFromMillis.hashCode()
        result = 31 * result + validUntilMillis.hashCode()
        result = 31 * result + ikSignature.contentHashCode()
        return result
    }

    companion object {
        const val DOMAIN_SEPARATOR: String = "arete/cert/v1"
    }
}

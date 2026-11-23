package com.renea.arete_social.data.community

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestión de Solicitudes de Ingreso a Comunidades según la sección 7.3 y 10.4 de la auditoría v3.
 *
 * La solicitud de ingreso (JOIN_REQUEST, 0x0020) viaja sellada hacia la AdmissionKey (Kadm)
 * derivada de la clave de época de la comunidad:
 * Kadm = HKDF-SHA256(Ke, "arete/epoch/admission", 32)
 *
 * Incluye la clave pública de identidad (IK) del aspirante y su clave pública de acuerdo DK-a
 * para que el aprobador pueda empaquetar el EpochKeyShare de bienvenida inmediatamente en JOIN_ACCEPT.
 */
class CommunityJoinRequests(
    private val epochKeyManager: EpochKeyManager
) {
    companion object {
        const val SUBKEY_ADMISSION = "admission"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class JoinRequestPayload(
        val applicantIkHex: String,
        val applicantDkaPublicKeyDerHex: String,
        val displayName: String,
        val introMessage: String,
        val requestedAtMs: Long
    )

    data class SealedJoinRequest(
        val communityId: ByteArray,
        val epochId: ByteArray,
        val iv: ByteArray,
        val encryptedPayload: ByteArray
    )

    /**
     * Sella una solicitud de ingreso usando la clave Kadm de la comunidad.
     */
    fun sealRequest(
        communityId: ByteArray,
        epochId: ByteArray,
        payload: JoinRequestPayload
    ): SealedJoinRequest {
        val ke = epochKeyManager.getEpochKey(communityId, epochId)
            ?: error("Clave de época no encontrada para derivar clave de admisión")

        val kAdm = epochKeyManager.deriveSubkey(ke, SUBKEY_ADMISSION)
        val payloadBytes = json.encodeToString(JoinRequestPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

        val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(kAdm, "AES")
        val gcmSpec = GCMParameterSpec(TAG_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        // AAD = communityId || epochId || "arete/join/v1"
        cipher.updateAAD(communityId + epochId + "arete/join/v1".toByteArray(Charsets.UTF_8))

        val ciphertext = cipher.doFinal(payloadBytes)

        return SealedJoinRequest(
            communityId = communityId,
            epochId = epochId,
            iv = iv,
            encryptedPayload = ciphertext
        )
    }

    /**
     * Abre y descifra una solicitud de ingreso (ejecutado por Dueño, Líder o Administradores con Kadm).
     */
    fun unsealRequest(
        sealed: SealedJoinRequest
    ): JoinRequestPayload {
        val ke = epochKeyManager.getEpochKey(sealed.communityId, sealed.epochId)
            ?: error("No se posee la clave de época para descifrar la solicitud de ingreso")

        val kAdm = epochKeyManager.deriveSubkey(ke, SUBKEY_ADMISSION)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(kAdm, "AES")
        val gcmSpec = GCMParameterSpec(TAG_BITS, sealed.iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(sealed.communityId + sealed.epochId + "arete/join/v1".toByteArray(Charsets.UTF_8))

        val plaintextBytes = cipher.doFinal(sealed.encryptedPayload)
        val jsonString = String(plaintextBytes, Charsets.UTF_8)

        return json.decodeFromString(JoinRequestPayload.serializer(), jsonString)
    }
}

package com.renea.arete_social.data.community

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.renea.arete_social.domain.util.toHex
import com.renea.arete_social.domain.util.hexToByteArray

/**
 * Gestor de Claves de Época y Cifrado de Grupo según la sección 10 de la auditoría v3.
 *
 * Cada comunidad posee una clave de época Ke (32 bytes aleatorios).
 * Los mensajes se cifran con claves derivadas (Kmsg, Kfile) usando AES-256-GCM.
 * La distribución se efectúa mediante EpochKeyShare cifrado punto a punto para cada miembro activo.
 */
class EpochKeyManager {

    private val secureRandom = SecureRandom()

    // communityIdHex -> (epochIdHex -> Ke)
    private val epochKeys = ConcurrentHashMap<String, ConcurrentHashMap<String, ByteArray>>()
    // communityIdHex -> currentCanonicalEpochIdHex
    private val canonicalEpochs = ConcurrentHashMap<String, String>()

    data class EpochKeyShare(
        val communityId: ByteArray,
        val epochId: ByteArray,
        val recipientIk: ByteArray,
        val ephemeralPublicKey: ByteArray, // X.509 DER
        val iv: ByteArray,                 // 12 bytes
        val encryptedKe: ByteArray         // AES-GCM ciphertext
    )

    data class EncryptedEpochMessage(
        val epochId: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    fun generateEpochKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    fun storeEpochKey(communityId: ByteArray, epochId: ByteArray, ke: ByteArray, isCanonical: Boolean = true) {
        val commKey = communityId.toHex()
        val epKey = epochId.toHex()

        val map = epochKeys.computeIfAbsent(commKey) { ConcurrentHashMap() }
        map[epKey] = ke.clone()

        if (isCanonical || !canonicalEpochs.containsKey(commKey)) {
            canonicalEpochs[commKey] = epKey
        }
    }

    fun getEpochKey(communityId: ByteArray, epochId: ByteArray): ByteArray? {
        return epochKeys[communityId.toHex()]?.get(epochId.toHex())
    }

    fun getCurrentEpochId(communityId: ByteArray): ByteArray? {
        val hex = canonicalEpochs[communityId.toHex()] ?: return null
        return hex.hexToByteArray()
    }

    /**
     * Deriva una subclave de época (Kmsg o Kfile) mediante HKDF-SHA256:
     * HKDF(Ke, salt = empty, info = "arete/epoch/" + label, len = 32)
     */
    fun deriveSubkey(ke: ByteArray, label: String): ByteArray {
        val salt = ByteArray(32) // salt vacío de 32 bytes de ceros
        val info = "arete/epoch/$label".toByteArray(Charsets.UTF_8)
        return hkdfSha256(ke, salt, info, 32)
    }

    /**
     * Cifra un mensaje con la clave Kmsg de la época actual.
     * AAD = communityId || epochId || messageId
     */
    fun encryptCommunityMessage(
        communityId: ByteArray,
        epochId: ByteArray,
        messageId: ByteArray,
        plaintext: ByteArray
    ): EncryptedEpochMessage {
        val ke = getEpochKey(communityId, epochId)
            ?: throw IllegalStateException("Clave de época no encontrada para cifrar mensaje")
        val kMsg = deriveSubkey(ke, "msg")

        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kMsg, "AES"), GCMParameterSpec(128, iv))

        val aad = ByteBuffer.allocate(communityId.size + epochId.size + messageId.size)
            .put(communityId)
            .put(epochId)
            .put(messageId)
            .array()
        cipher.updateAAD(aad)

        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEpochMessage(epochId, iv, ciphertext)
    }

    /**
     * Descifra un mensaje de comunidad usando la época correspondiente.
     */
    fun decryptCommunityMessage(
        communityId: ByteArray,
        epochId: ByteArray,
        messageId: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val ke = getEpochKey(communityId, epochId)
            ?: throw IllegalStateException("Clave de época no disponible para descifrar el mensaje")
        val kMsg = deriveSubkey(ke, "msg")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kMsg, "AES"), GCMParameterSpec(128, iv))

        val aad = ByteBuffer.allocate(communityId.size + epochId.size + messageId.size)
            .put(communityId)
            .put(epochId)
            .put(messageId)
            .array()
        cipher.updateAAD(aad)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Crea un EpochKeyShare cifrado punto a punto para la clave pública DK-a de un miembro.
     */
    fun createEpochKeyShare(
        communityId: ByteArray,
        epochId: ByteArray,
        ke: ByteArray,
        recipientIk: ByteArray,
        recipientDkaPublic: PublicKey
    ): EpochKeyShare {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val ephemeralKeyPair = kpg.generateKeyPair()

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephemeralKeyPair.private)
        ka.doPhase(recipientDkaPublic, true)
        val sharedSecret = ka.generateSecret()

        val wrapSalt = MessageDigest.getInstance("SHA-256").digest(ephemeralKeyPair.public.encoded)
        val wrapInfo = "arete/share/v1".toByteArray(Charsets.UTF_8)
        val wrapKeyBytes = hkdfSha256(sharedSecret, wrapSalt, wrapInfo, 32)

        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrapKeyBytes, "AES"), GCMParameterSpec(128, iv))

        val aad = ByteBuffer.allocate(communityId.size + epochId.size + recipientIk.size)
            .put(communityId)
            .put(epochId)
            .put(recipientIk)
            .array()
        cipher.updateAAD(aad)

        val encryptedKe = cipher.doFinal(ke)

        return EpochKeyShare(
            communityId = communityId,
            epochId = epochId,
            recipientIk = recipientIk,
            ephemeralPublicKey = ephemeralKeyPair.public.encoded,
            iv = iv,
            encryptedKe = encryptedKe
        )
    }

    /**
     * Descifra un EpochKeyShare recibido usando la clave privada DK-a del dispositivo.
     */
    fun decryptEpochKeyShare(
        share: EpochKeyShare,
        myDkaPrivateKey: PrivateKey
    ): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val ephemeralPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(share.ephemeralPublicKey))

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myDkaPrivateKey)
        ka.doPhase(ephemeralPublicKey, true)
        val sharedSecret = ka.generateSecret()

        val wrapSalt = MessageDigest.getInstance("SHA-256").digest(share.ephemeralPublicKey)
        val wrapInfo = "arete/share/v1".toByteArray(Charsets.UTF_8)
        val wrapKeyBytes = hkdfSha256(sharedSecret, wrapSalt, wrapInfo, 32)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrapKeyBytes, "AES"), GCMParameterSpec(128, share.iv))

        val aad = ByteBuffer.allocate(share.communityId.size + share.epochId.size + share.recipientIk.size)
            .put(share.communityId)
            .put(share.epochId)
            .put(share.recipientIk)
            .array()
        cipher.updateAAD(aad)

        val ke = cipher.doFinal(share.encryptedKe)
        storeEpochKey(share.communityId, share.epochId, ke)
        return ke
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(0x01.toByte())
        return mac.doFinal().copyOf(len)
    }
}

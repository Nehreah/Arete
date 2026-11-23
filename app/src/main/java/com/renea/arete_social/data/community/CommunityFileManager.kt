package com.renea.arete_social.data.community

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de Archivos de Comunidad según la sección 10.2 y 10.5 de la auditoría v3.
 *
 * Los archivos grandes (imágenes, documentos, audios) se cifran localmente
 * usando la subclave de archivo Kfile derivada de la clave de época Ke:
 * Kfile = HKDF-SHA256(Ke, "arete/epoch/file", 32)
 *
 * Formato del archivo cifrado:
 * [12 bytes: IV aleatorio] || [Ciphertext AES-256-GCM con Tag de 128 bits]
 * AAD = communityId || epochId || fileHashOriginal
 */
class CommunityFileManager(
    private val epochKeyManager: EpochKeyManager
) {
    companion object {
        const val IV_SIZE_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        const val SUBKEY_LABEL = "file"
    }

    private val secureRandom = SecureRandom()

    data class EncryptedFileHeader(
        val communityId: ByteArray,
        val epochId: ByteArray,
        val originalSha256Hex: String,
        val iv: ByteArray
    )

    /**
     * Cifra un arreglo de bytes en memoria (para miniaturas, audios cortos o avatares).
     */
    fun encryptFileBytes(
        communityId: ByteArray,
        epochId: ByteArray,
        fileBytes: ByteArray
    ): ByteArray {
        val ke = epochKeyManager.getEpochKey(communityId, epochId)
            ?: error("Clave de época no encontrada para cifrar archivo")

        val kFile = epochKeyManager.deriveSubkey(ke, SUBKEY_LABEL)
        val originalSha256 = MessageDigest.getInstance("SHA-256").digest(fileBytes)

        val iv = ByteArray(IV_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(kFile, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        // AAD = communityId || epochId || originalSha256
        val aad = communityId + epochId + originalSha256
        cipher.updateAAD(aad)

        val ciphertext = cipher.doFinal(fileBytes)

        // Resultado = IV (12 bytes) + Ciphertext (incluye tag GCM de 16 bytes)
        return iv + ciphertext
    }

    /**
     * Descifra un archivo cifrado en memoria usando la subclave Kfile de la época correspondiente.
     */
    fun decryptFileBytes(
        communityId: ByteArray,
        epochId: ByteArray,
        encryptedBytes: ByteArray,
        originalSha256: ByteArray
    ): ByteArray {
        require(encryptedBytes.size > IV_SIZE_BYTES) { "Archivo cifrado demasiado corto" }

        val ke = epochKeyManager.getEpochKey(communityId, epochId)
            ?: error("Clave de época no disponible para descifrar archivo")

        val kFile = epochKeyManager.deriveSubkey(ke, SUBKEY_LABEL)
        val iv = encryptedBytes.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = encryptedBytes.copyOfRange(IV_SIZE_BYTES, encryptedBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(kFile, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val aad = communityId + epochId + originalSha256
        cipher.updateAAD(aad)

        val plaintext = cipher.doFinal(ciphertext)

        // Validar integridad SHA-256
        val actualSha256 = MessageDigest.getInstance("SHA-256").digest(plaintext)
        require(actualSha256.contentEquals(originalSha256)) {
            "El hash SHA-256 del archivo descifrado no coincide con el esperado"
        }

        return plaintext
    }

    /**
     * Cifra un flujo de entrada hacia un flujo de salida (para archivos grandes en disco sin saturar RAM).
     */
    fun encryptStream(
        communityId: ByteArray,
        epochId: ByteArray,
        originalSha256: ByteArray,
        input: InputStream,
        output: OutputStream
    ) {
        val ke = epochKeyManager.getEpochKey(communityId, epochId)
            ?: error("Clave de época no encontrada para cifrar flujo")

        val kFile = epochKeyManager.deriveSubkey(ke, SUBKEY_LABEL)
        val iv = ByteArray(IV_SIZE_BYTES).also { secureRandom.nextBytes(it) }

        // Escribir IV como encabezado en el destino
        output.write(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(kFile, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(communityId + epochId + originalSha256)

        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut)
        }
    }
}

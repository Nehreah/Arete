package com.renea.arete_social.data.identity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de Respaldo y Recuperación de Identidad según la sección 6.4 de la auditoría v3.
 *
 * Permite al usuario exportar su frase mnemotécnica de 12 palabras protegida por una contraseña,
 * utilizando PBKDF2-HMAC-SHA256 con 100,000 iteraciones y salt aleatorio de 16 bytes,
 * seguido de cifrado AES-256-GCM con tag de 128 bits y separación de dominio "arete/backup/v1".
 *
 * Formato del payload de respaldo:
 * [16 bytes: Salt PBKDF2] || [12 bytes: IV AES-GCM] || [Ciphertext cifrado + Tag de 16 bytes]
 */
class RecoveryManager(
    private val identityManager: IdentityManager
) {
    companion object {
        const val SALT_LENGTH = 16
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val PBKDF2_ITERATIONS = 600_000
        const val PBKDF2_ITERATIONS_LEGACY = 100_000
        const val KEY_LENGTH_BITS = 256
        const val BACKUP_DOMAIN = "arete/backup/v1"
    }

    private val secureRandom = SecureRandom()

    /**
     * Exporta la lista de palabras mnemotécnicas cifrada con una contraseña del usuario.
     *
     * @param words Lista de 12 o 24 palabras.
     * @param userPassword Contraseña elegida por el usuario para proteger el archivo de respaldo.
     * @return Arreglo de bytes empaquetado listo para guardar en almacenamiento o compartir.
     */
    fun exportEncryptedBackup(words: List<String>, userPassword: CharArray): ByteArray {
        require(words.size == 12 || words.size == 24) { "Se esperan 12 o 24 palabras" }
        require(userPassword.isNotEmpty()) { "La contraseña no puede estar vacía" }

        val mnemonic = words.joinToString(" ")
        val mnemonicBytes = mnemonic.toByteArray(StandardCharsets.UTF_8)

        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

        // Derivar clave de cifrado desde la contraseña usando PBKDF2
        val keyBytes = deriveKeyFromPassword(userPassword, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(BACKUP_DOMAIN.toByteArray(StandardCharsets.UTF_8))

        val ciphertext = cipher.doFinal(mnemonicBytes)

        val buffer = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + ciphertext.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)

        return buffer.array()
    }

    /**
     * Descifra un respaldo y devuelve la lista de palabras mnemotécnicas.
     *
     * @param backupBytes Arreglo de bytes del archivo de respaldo.
     * @param userPassword Contraseña proporcionada por el usuario.
     * @return Lista de 12 o 24 palabras.
     */
    fun importEncryptedBackup(backupBytes: ByteArray, userPassword: CharArray): List<String> {
        require(backupBytes.size > SALT_LENGTH + IV_LENGTH + 16) { "Respaldo corrupto o truncado" }

        val buffer = ByteBuffer.wrap(backupBytes).order(ByteOrder.BIG_ENDIAN)
        val salt = ByteArray(SALT_LENGTH)
        buffer.get(salt)

        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val plaintextBytes = runCatching {
            descifrarConIteraciones(userPassword, salt, iv, ciphertext, PBKDF2_ITERATIONS)
        }.recoverCatching {
            // Reintento con iteraciones heredadas v3 (100_000) por retrocompatibilidad
            descifrarConIteraciones(userPassword, salt, iv, ciphertext, PBKDF2_ITERATIONS_LEGACY)
        }.getOrThrow()

        val mnemonic = String(plaintextBytes, StandardCharsets.UTF_8).trim()

        return mnemonic.split("\\s+".toRegex())
    }

    private fun descifrarConIteraciones(
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        iterations: Int
    ): ByteArray {
        val keyBytes = deriveKeyFromPassword(password, salt, iterations)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(BACKUP_DOMAIN.toByteArray(StandardCharsets.UTF_8))

        return cipher.doFinal(ciphertext)
    }

    /**
     * Valida si una frase mnemotécnica introducida por el usuario coincide con un hash SHA-256 esperado,
     * sin necesidad de cargar la frase en memoria permanente ni derivar la clave completa.
     */
    fun verifyPhraseMatchesHash(words: List<String>, expectedHashHex: String): Boolean {
        val hash = identityManager.hashPhrase(words)
        return hash.equals(expectedHashHex, ignoreCase = true)
    }

    private fun deriveKeyFromPassword(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return factory.generateSecret(spec).encoded
    }
}

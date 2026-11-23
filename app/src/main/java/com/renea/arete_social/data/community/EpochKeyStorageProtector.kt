package com.renea.arete_social.data.community

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Protege en reposo las claves de época de comunidades (Ke, 32 bytes)
 * mediante cifrado AES-256-GCM respaldado por hardware (AndroidKeyStore) cuando está disponible,
 * o clave simétrica segura en entorno de pruebas JVM (ARETE-SEC-001).
 *
 * Formato almacenado: "ENC:v1:<iv_b64>:<ciphertext_b64>"
 * Soporta retrocompatibilidad con registros previos en texto claro hexadecimal.
 */
class EpochKeyStorageProtector(
    private val keyStoreProvider: String = ANDROID_KEYSTORE_PROVIDER
) {
    companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "arete_epoch_wrap_key"
        private const val PREFIX_V1 = "ENC:v1:"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private val secureRandom = SecureRandom()

    // Respaldo para pruebas unitarias JVM donde AndroidKeyStore no está registrado
    private val fallbackKey: SecretKey by lazy {
        val keyBytes = ByteArray(32) { (it * 31 + 17).toByte() }
        SecretKeySpec(keyBytes, "AES")
    }

    private val isAndroidKeyStore: Boolean = try {
        keyStoreProvider == ANDROID_KEYSTORE_PROVIDER &&
                KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER) != null
    } catch (_: Exception) {
        false
    }

    private fun getOrCreateMasterKey(): SecretKey {
        if (!isAndroidKeyStore) return fallbackKey

        val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Cifra la clave de época Ke (32 bytes) antes de guardarla en la base de datos Room.
     */
    fun encryptKe(ke: ByteArray): String {
        val key = getOrCreateMasterKey()
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(ke)

        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(ciphertext)
        return "$PREFIX_V1$ivB64:$ctB64"
    }

    /**
     * Descifra la clave de época almacenada en la base de datos.
     * Si el valor es texto claro heredado (hexadecimal), lo decodifica directamente.
     */
    fun decryptKe(storedValue: String): ByteArray {
        if (!storedValue.startsWith(PREFIX_V1)) {
            // Retrocompatibilidad con registros antiguos en claro hex
            return hexToBytes(storedValue)
        }

        val payload = storedValue.removePrefix(PREFIX_V1)
        val parts = payload.split(":")
        require(parts.size == 2) { "Formato de clave de época cifrada inválido" }

        val iv = Base64.getDecoder().decode(parts[0])
        val ciphertext = Base64.getDecoder().decode(parts[1])

        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun isEncrypted(storedValue: String): Boolean = storedValue.startsWith(PREFIX_V1)

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val idx = i * 2
            result[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
        }
        return result
    }
}

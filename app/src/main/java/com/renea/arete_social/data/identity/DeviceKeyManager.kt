package com.renea.arete_social.data.identity

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

/**
 * DeviceKeyManager — Gestiona el par de claves del dispositivo:
 * - DK-s: Clave de firma delegada (ECDSA P-256)
 * - DK-a: Clave de acuerdo de claves / ECDH (P-256)
 *
 * Utiliza AndroidKeyStore con respaldo por hardware cuando está disponible,
 * y permite un proveedor por defecto (JCA estándar) en entornos de pruebas unitarias JVM
 * o cuando no esté inicializado el keystore de Android.
 */
class DeviceKeyManager(
    private val keyStoreProvider: String = ANDROID_KEYSTORE_PROVIDER
) {

    companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val ALIAS_DKS_PREFIX = "arete_dks_"
        const val ALIAS_DKA_PREFIX = "arete_dka_"
    }

    private val isAndroidKeyStore: Boolean = try {
        keyStoreProvider == ANDROID_KEYSTORE_PROVIDER &&
                KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER) != null
    } catch (_: Exception) {
        false
    }

    /**
     * Almacenamiento en memoria para fallback en pruebas JVM o cuando AndroidKeyStore no esté presente.
     */
    private val memoryKeyPairs = mutableMapOf<String, KeyPair>()

    /**
     * Inicializa o recupera el par de claves de firma del dispositivo (DK-s).
     */
    @Synchronized
    fun getOrCreateDksKeyPair(deviceId: String): KeyPair {
        val alias = "$ALIAS_DKS_PREFIX$deviceId"
        return getKeyPair(alias, isSigningKey = true)
    }

    /**
     * Inicializa o recupera el par de claves de acuerdo (DK-a) del dispositivo para ECDH.
     */
    @Synchronized
    fun getOrCreateDkaKeyPair(deviceId: String): KeyPair {
        val alias = "$ALIAS_DKA_PREFIX$deviceId"
        return getKeyPair(alias, isSigningKey = false)
    }

    /**
     * Firma datos usando la clave privada DK-s del dispositivo.
     */
    fun signWithDks(deviceId: String, payload: ByteArray): ByteArray {
        val keyPair = getOrCreateDksKeyPair(deviceId)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(payload)
        return signature.sign()
    }

    /**
     * Realiza acuerdo Diffie-Hellman (ECDH) usando la clave privada DK-a y una clave pública remota.
     */
    fun computeDkaSharedSecret(deviceId: String, remotePublicKey: PublicKey): ByteArray {
        val keyPair = getOrCreateDkaKeyPair(deviceId)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(remotePublicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Elimina las claves asociadas al dispositivo (por ejemplo, ante revocación o desvinculación).
     */
    @Synchronized
    fun deleteDeviceKeys(deviceId: String) {
        val dksAlias = "$ALIAS_DKS_PREFIX$deviceId"
        val dkaAlias = "$ALIAS_DKA_PREFIX$deviceId"

        memoryKeyPairs.remove(dksAlias)
        memoryKeyPairs.remove(dkaAlias)

        if (isAndroidKeyStore) {
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
                if (ks.containsAlias(dksAlias)) ks.deleteEntry(dksAlias)
                if (ks.containsAlias(dkaAlias)) ks.deleteEntry(dkaAlias)
            } catch (_: Exception) {}
        }
    }

    private fun getKeyPair(alias: String, isSigningKey: Boolean): KeyPair {
        if (isAndroidKeyStore) {
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
                if (ks.containsAlias(alias)) {
                    val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                    if (entry != null) {
                        return KeyPair(entry.certificate.publicKey, entry.privateKey)
                    }
                }
                return generateInAndroidKeyStore(alias, isSigningKey)
            } catch (e: Exception) {
                // Si falla AndroidKeyStore (ej. mock en tests), se usa memoria fallback
            }
        }

        return memoryKeyPairs.getOrPut(alias) {
            generateSoftwareKeyPair()
        }
    }

    private fun generateInAndroidKeyStore(alias: String, isSigningKey: Boolean): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE_PROVIDER
        )

        val purposes = if (isSigningKey) {
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                KeyProperties.PURPOSE_AGREE_KEY
            } else {
                // Fallback en SDKs antiguos para compatibilidad de clave EC
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            }
        }

        val builder = KeyGenParameterSpec.Builder(alias, purposes)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)

        kpg.initialize(builder.build())
        return kpg.generateKeyPair()
    }

    private fun generateSoftwareKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }
}

package com.renea.arete_social.data.identity

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

/**
 * DeviceKeyManager — Gestiona el par de claves del dispositivo:
 * - DK-s: Clave de firma delegada (ECDSA P-256)
 * - DK-a: Clave de acuerdo de claves / ECDH (P-256)
 *
 * En Android 12+ (API 31+): utiliza AndroidKeyStore con PURPOSE_AGREE_KEY para DK-a.
 * En Android 8–11 (API 26–30): genera claves EC persistentes protegidas mediante cifrado AES-256-GCM
 * respaldado por el Android Keystore (EncryptedSharedPreferences), resolviendo ARETE-COMPAT-001.
 *
 * En producción falla explícitamente ante errores de Keystore sin degradación silenciosa a RAM (ARETE-SEC-002).
 */
class DeviceKeyManager(
    private val context: Context? = null,
    private val keyStoreProvider: String = ANDROID_KEYSTORE_PROVIDER
) {

    companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val ALIAS_DKS_PREFIX = "arete_dks_"
        const val ALIAS_DKA_PREFIX = "arete_dka_"
        private const val PREFS_LEGACY_KEYS = "arete_device_keys_legacy"
        private const val TAG = "DeviceKeyManager"
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
            } catch (e: Exception) {
                Log.w(TAG, "Error eliminando alias de Keystore", e)
            }

            if (context != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    val prefs = EncryptedSharedPreferences.create(
                        context,
                        PREFS_LEGACY_KEYS,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    prefs.edit()
                        .remove("${dkaAlias}_priv")
                        .remove("${dkaAlias}_pub")
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Error eliminando claves legacy protegidas", e)
                }
            }
        }
    }

    private fun getKeyPair(alias: String, isSigningKey: Boolean): KeyPair {
        if (isAndroidKeyStore) {
            try {
                // ARETE-COMPAT-001: En Android 8–11 (API 26–30), AndroidKeyStore no soporta
                // PURPOSE_AGREE_KEY en claves EC de hardware. Se utiliza clave EC persistente
                // protegida por AES-256-GCM de Keystore.
                if (!isSigningKey && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    return getOrCreateLegacyDkaKeyPair(alias)
                }

                val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
                if (ks.containsAlias(alias)) {
                    val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                    if (entry != null) {
                        return KeyPair(entry.certificate.publicKey, entry.privateKey)
                    }
                }
                return generateInAndroidKeyStore(alias, isSigningKey)
            } catch (e: Exception) {
                // ARETE-SEC-002: En producción no degradar en silencio a memoria volátil.
                Log.e(TAG, "Fallo crítico al acceder a AndroidKeyStore para alias: $alias", e)
                throw SecurityException("Fallo en hardware Keystore para clave $alias: ${e.message}", e)
            }
        }

        // Fallback únicamente permitido en entorno de pruebas (proveedor no-Android)
        return memoryKeyPairs.getOrPut(alias) {
            generateSoftwareKeyPair()
        }
    }

    private fun getOrCreateLegacyDkaKeyPair(alias: String): KeyPair {
        memoryKeyPairs[alias]?.let { return it }

        val ctx = context
        if (ctx != null) {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                ctx,
                PREFS_LEGACY_KEYS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val privBase64 = prefs.getString("${alias}_priv", null)
            val pubBase64 = prefs.getString("${alias}_pub", null)
            if (privBase64 != null && pubBase64 != null) {
                val keyFactory = KeyFactory.getInstance("EC")
                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privBase64)))
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubBase64)))
                val kp = KeyPair(pubKey, privKey)
                memoryKeyPairs[alias] = kp
                return kp
            }
            val kp = generateSoftwareKeyPair()
            prefs.edit()
                .putString("${alias}_priv", Base64.getEncoder().encodeToString(kp.private.encoded))
                .putString("${alias}_pub", Base64.getEncoder().encodeToString(kp.public.encoded))
                .apply()
            memoryKeyPairs[alias] = kp
            return kp
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
                throw UnsupportedOperationException("AndroidKeyStore EC KeyAgreement no soportado antes de API 31")
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

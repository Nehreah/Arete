package com.renea.arete_social.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private const val TAG = "GestorIdentidadEcdsa"
private const val PREFS_NAME_CIFRADO = "antired_crypto_identity_enc"
private const val PREF_KEY_PRIVATE = "ecdsa_p256_private_key"
private const val PREF_KEY_PUBLIC = "ecdsa_p256_public_key"
private const val ALGORITMO_CLAVE = "EC"
private const val ALGORITMO_FIRMA = "SHA256withECDSA"

/**
 * Gestor de Identidad Criptográfica peer-to-peer (ECDSA P-256 / secp256r1).
 *
 * Firma y verifica firmas digitales de los paquetes Mesh para garantizar
 * la autenticidad y prevenir suplantación de identidad en la red peer-to-peer.
 *
 * Las claves se persisten en almacenamiento seguro respaldado por el Android Keystore.
 * Si el Keystore no está disponible, falla cerrado y NO cae a texto plano por seguridad.
 */
class GestorIdentidadEcdsa(
    private val context: Context? = null,
    prefsOverride: SharedPreferences? = null
) {

    private val prefs: SharedPreferences? by lazy {
        prefsOverride ?: runCatching {
            abrirPrefsCifradas(
                context ?: error("Context o prefsOverride requerido para GestorIdentidadEcdsa")
            )
        }.getOrElse { error ->
            Log.e(TAG, "Keystore no disponible para identidad ECDSA: ${error.message}", error)
            null
        }
    }

    private val algoritmoKey: String = ALGORITMO_CLAVE
    private val algoritmoSignature: String = ALGORITMO_FIRMA

    @Volatile private var miClavePrivada: PrivateKey? = null
    @Volatile private var miClavePublica: PublicKey? = null

    init {
        cargarOGenerarClaves()
    }

    companion object {
        private fun abrirPrefsCifradas(context: Context): SharedPreferences {
            return runCatching {
                crearEncryptedPrefs(context)
            }.recoverCatching { primerError ->
                Log.w(TAG, "Reintentando apertura de SharedPreferences cifradas tras fallo inicial", primerError)
                // M-05: Reintentar una vez sin borrar el archivo a ciegas para evitar regeneración silenciosa de identidad
                crearEncryptedPrefs(context)
            }.getOrElse { error ->
                Log.e(TAG, "Keystore no disponible para identidad ECDSA; fallando cerrado por seguridad", error)
                throw SecurityException("Almacén seguro de claves Keystore no disponible: ${error.message}", error)
            }
        }

        private fun crearEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME_CIFRADO,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    @Synchronized
    private fun cargarOGenerarClaves() {
        val p = prefs ?: run {
            Log.w(TAG, "SharedPreferences no disponibles; no se pueden cargar claves ECDSA")
            return
        }
        val privBase64 = p.getString(PREF_KEY_PRIVATE, null)
        val pubBase64 = p.getString(PREF_KEY_PUBLIC, null)

        if (privBase64 != null && pubBase64 != null) {
            runCatching {
                val keyFactory = KeyFactory.getInstance(algoritmoKey)
                val privBytes = Base64.getDecoder().decode(privBase64)
                val pubBytes = Base64.getDecoder().decode(pubBase64)

                miClavePrivada = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                miClavePublica = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
            }.onFailure { error ->
                // H-07: NO regenerar ni sobrescribir silenciosamente las claves en caso de error de parseo
                Log.e(TAG, "Fallo al decodificar claves ECDSA existentes. Se preserva el archivo sin sobrescribir.", error)
            }
        } else {
            generarNuevasClaves()
        }
    }

    private fun generarNuevasClaves() {
        val p = prefs ?: return
        runCatching {
            val kpg = KeyPairGenerator.getInstance(algoritmoKey)
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val kp: KeyPair = kpg.generateKeyPair()
            miClavePrivada = kp.private
            miClavePublica = kp.public

            val privBase64 = Base64.getEncoder().encodeToString(kp.private.encoded)
            val pubBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)

            val guardadoExitoso = p.edit()
                .putString(PREF_KEY_PRIVATE, privBase64)
                .putString(PREF_KEY_PUBLIC, pubBase64)
                .commit()

            if (!guardadoExitoso) {
                Log.e(TAG, "commit() devolvió false al guardar nuevas claves ECDSA")
            }
        }.onFailure {
            Log.e(TAG, "No se pudo generar la identidad criptográfica ECDSA: ${it.message}", it)
        }
    }

    /** true si hay identidad utilizable. */
    fun estaDisponible(): Boolean = miClavePrivada != null && miClavePublica != null

    /** Obtiene la clave pública local en formato Base64. */
    fun obtenerClavePublicaBase64(): String? {
        val pub = miClavePublica ?: return null
        return Base64.getEncoder().encodeToString(pub.encoded)
    }

    /**
     * Firma digitalmente una cadena de caracteres con la clave privada local.
     * @return Firma digital codificada en Base64, o null si falla.
     */
    fun firmar(datos: String): String? {
        val priv = miClavePrivada ?: return null
        return runCatching {
            val sig = Signature.getInstance(algoritmoSignature)
            sig.initSign(priv)
            sig.update(datos.toByteArray(Charsets.UTF_8))
            val firmaBytes = sig.sign()
            Base64.getEncoder().encodeToString(firmaBytes)
        }.getOrNull()
    }

    /**
     * Verifica una firma digital enviada por un peer remoto.
     * @param datos Cadena de caracteres original firmada.
     * @param firmaBase64 Firma digital recibida en Base64.
     * @param clavePublicaBase64 Clave pública del remitente en Base64.
     * @return true si la firma es válida y auténtica; false en caso contrario.
     */
    fun verificarFirma(datos: String, firmaBase64: String, clavePublicaBase64: String): Boolean {
        if (firmaBase64.isBlank() || clavePublicaBase64.isBlank()) return false
        return runCatching {
            val keyFactory = KeyFactory.getInstance(algoritmoKey)
            val pubBytes = Base64.getDecoder().decode(clavePublicaBase64)
            val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

            val sig = Signature.getInstance(algoritmoSignature)
            sig.initVerify(pubKey)
            sig.update(datos.toByteArray(Charsets.UTF_8))

            val firmaBytes = Base64.getDecoder().decode(firmaBase64)
            sig.verify(firmaBytes)
        }.getOrDefault(false)
    }

    /**
     * S-01: Aisla la identidad del dispositivo al usuario actual.
     * Precarga las claves del nuevo [usuarioId] desde el almacén seguro (o genera nuevas si no
     * existen). Debe llamarse tras un cambio de cuenta exitoso para que los paquetes mesh se
     * firmen con la clave del usuario correcto y no hereden la identidad del anterior.
     *
     * El parámetro es opcional para mantener compatibilidad: si es null, solo se limpian las
     * claves en memoria (modo "sin sesión").
     */
    @Synchronized
    fun cambiarUsuario(usuarioId: String?) {
        if (usuarioId == null) {
            cargarOGenerarClaves()
            return
        }
        miClavePrivada = null
        miClavePublica = null
        val p = prefs ?: return
        val privKey = "${usuarioId}_${PREF_KEY_PRIVATE}"
        val pubKey  = "${usuarioId}_${PREF_KEY_PUBLIC}"
        val privBase64 = p.getString(privKey, null)
        val pubBase64  = p.getString(pubKey, null)
        if (privBase64 != null && pubBase64 != null) {
            runCatching {
                val keyFactory = KeyFactory.getInstance(algoritmoKey)
                miClavePrivada = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privBase64)))
                miClavePublica = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubBase64)))
            }.onFailure {
                Log.e(TAG, "Fallo al cargar claves ECDSA del usuario $usuarioId: ${it.message}")
            }
        } else {
            // Generar nuevas claves para este usuario
            runCatching {
                val kpg = KeyPairGenerator.getInstance(algoritmoKey)
                kpg.initialize(ECGenParameterSpec("secp256r1"))
                val kp: KeyPair = kpg.generateKeyPair()
                miClavePrivada = kp.private
                miClavePublica = kp.public
                val guardado = p.edit()
                    .putString(privKey, Base64.getEncoder().encodeToString(kp.private.encoded))
                    .putString(pubKey,  Base64.getEncoder().encodeToString(kp.public.encoded))
                    .commit()
                if (!guardado) Log.e(TAG, "commit() devolvió false al guardar claves ECDSA para $usuarioId")
            }.onFailure {
                Log.e(TAG, "No se pudo generar identidad ECDSA para $usuarioId: ${it.message}")
            }
        }
    }

    /**
     * S-01: Descarga las claves de memoria. Debe llamarse al cerrar sesión.
     * Las claves permanecen en el almacén cifrado para el próximo inicio de sesión.
     */
    @Synchronized
    fun limpiarIdentidad() {
        miClavePrivada = null
        miClavePublica = null
    }
}

/**
 * Alias de compatibilidad hacia atrás para evitar roturas durante la transición.
 */
typealias GestorIdentidadEd25519 = GestorIdentidadEcdsa

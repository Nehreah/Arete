package com.renea.arete_social.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.renea.arete_social.domain.crypto.CifradoE2eeException
import com.renea.arete_social.domain.crypto.CifradorE2ee
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "CifradorE2eeAndroid"
private const val PREFS_NAME_CIFRADO = "antired_crypto_keystore_enc"
private const val PREFIX_E2EE_V3 = "ENC:v3:"
private const val PREFIX_E2EE_V4 = "ENC:v4:"
private const val ALGORITMO_EC = "EC"
private const val CURVA_EC = "secp256r1"
private const val ALGORITMO_ACUERDO = "ECDH"
private const val ALGORITMO_AES_GCM = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH = 128
private const val GCM_IV_LENGTH = 12
private val BASE64_REGEX = Regex("^[A-Za-z0-9+/=]+$")

/**
 * Implementación de Cifrado Extremo a Extremo (E2EE) v3:
 * - Curvas Elípticas ECDH (P-256 / secp256r1) para intercambio de claves asimétrico.
 * - HKDF-SHA256 con salt = SHA-256(claves públicas ordenadas) e info = "antired-e2ee-v3".
 * - AES-256-GCM con IV aleatorio de 12 bytes y AAD contextual (remitenteId|destinatarioId).
 * - Almacenamiento seguro respaldado por Android Keystore con política fail-closed.
 * - Usa [java.util.Base64] para ejecución directa sobre JVM en pruebas unitarias.
 */
class CifradorE2eeAndroid(
    private val context: Context? = null,
    prefsOverride: SharedPreferences? = null
) : CifradorE2ee {

    private val prefs: SharedPreferences by lazy {
        prefsOverride ?: abrirPrefsCifradas(
            context ?: error("Context requerido para CifradorE2eeAndroid si no se provee prefsOverride")
        )
    }

    private fun abrirPrefsCifradas(ctx: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                PREFS_NAME_CIFRADO,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse { error ->
            Log.e(TAG, "Android Keystore no disponible para E2EE; fallando cerrado por seguridad: ${error.message}", error)
            throw CifradoE2eeException("Almacén seguro de claves Keystore no disponible: ${error.message}", error)
        }
    }

    override fun obtenerClavePublicaConocida(usuarioId: String): String? {
        if (usuarioId.isBlank()) return null
        val direct = prefs.getString("peer_pub_$usuarioId", null)
        if (direct != null) return direct

        val norm = usuarioId.replace("-", "").trim().lowercase()
        val fromNorm = prefs.getString("peer_pub_$norm", null)
        if (fromNorm != null) return fromNorm

        if (usuarioId.startsWith("invitado-local-")) {
            val sinPrefijo = usuarioId.removePrefix("invitado-local-")
            val fromSinPrefijo = prefs.getString("peer_pub_$sinPrefijo", null)
                ?: prefs.getString("peer_pub_${sinPrefijo.replace("-", "").lowercase()}", null)
            if (fromSinPrefijo != null) return fromSinPrefijo
        } else {
            val conPrefijo = "invitado-local-$usuarioId"
            val fromConPrefijo = prefs.getString("peer_pub_$conPrefijo", null)
                ?: prefs.getString("peer_pub_invitado-local-$norm", null)
            if (fromConPrefijo != null) return fromConPrefijo
        }
        return null
    }

    override fun obtenerOrigenClavePublicaConocida(usuarioId: String): String? {
        if (usuarioId.isBlank()) return null
        val direct = prefs.getString("peer_orig_$usuarioId", null)
        if (direct != null) return direct
        val norm = usuarioId.replace("-", "").trim().lowercase()
        return prefs.getString("peer_orig_$norm", null)
    }

    override fun guardarClavePublicaConocida(
        usuarioId: String,
        clavePublicaBase64: String,
        origen: String
    ) {
        if (clavePublicaBase64.isNotBlank() && usuarioId.isNotBlank()) {
            val origenExistente = obtenerOrigenClavePublicaConocida(usuarioId)
            if ((origenExistente == "verificado" || origenExistente == "oficial") && (origen == "mesh" || origen == "mesh_no_verificada")) {
                return
            }
            val norm = usuarioId.replace("-", "").trim().lowercase()
            val editor = prefs.edit()
                .putString("peer_pub_$usuarioId", clavePublicaBase64)
                .putString("peer_orig_$usuarioId", origen)

            if (norm.isNotEmpty() && norm != usuarioId) {
                editor.putString("peer_pub_$norm", clavePublicaBase64)
                editor.putString("peer_orig_$norm", origen)
            }
            if (usuarioId.startsWith("invitado-local-")) {
                val sinPrefijo = usuarioId.removePrefix("invitado-local-")
                editor.putString("peer_pub_$sinPrefijo", clavePublicaBase64)
                editor.putString("peer_orig_$sinPrefijo", origen)
                val normSin = sinPrefijo.replace("-", "").trim().lowercase()
                if (normSin.isNotEmpty()) {
                    editor.putString("peer_pub_$normSin", clavePublicaBase64)
                    editor.putString("peer_orig_$normSin", origen)
                }
            } else {
                val conPrefijo = "invitado-local-$usuarioId"
                editor.putString("peer_pub_$conPrefijo", clavePublicaBase64)
                editor.putString("peer_orig_$conPrefijo", origen)
            }
            editor.commit()
        }
    }

    override fun borrarClavePublicaConocida(usuarioId: String) {
        if (usuarioId.isBlank()) return
        val norm = usuarioId.replace("-", "").trim().lowercase()
        val editor = prefs.edit()
            .remove("peer_pub_$usuarioId")
            .remove("peer_orig_$usuarioId")

        if (norm.isNotEmpty()) {
            editor.remove("peer_pub_$norm")
            editor.remove("peer_orig_$norm")
        }
        if (usuarioId.startsWith("invitado-local-")) {
            val sinPrefijo = usuarioId.removePrefix("invitado-local-")
            editor.remove("peer_pub_$sinPrefijo")
            editor.remove("peer_orig_$sinPrefijo")
            val normSin = sinPrefijo.replace("-", "").trim().lowercase()
            if (normSin.isNotEmpty()) {
                editor.remove("peer_pub_$normSin")
                editor.remove("peer_orig_$normSin")
            }
        } else {
            val conPrefijo = "invitado-local-$usuarioId"
            editor.remove("peer_pub_$conPrefijo")
            editor.remove("peer_orig_$conPrefijo")
            val normCon = "invitado-local-$norm"
            editor.remove("peer_pub_$normCon")
            editor.remove("peer_orig_$normCon")
        }
        editor.commit()
    }

    private val secureRandom = SecureRandom()
    private val lockClaves = Any()

    override fun obtenerClavePublicaPropia(usuarioId: String): String {
        synchronized(lockClaves) {
            val clavePublicaExistente = prefs.getString("pub_$usuarioId", null)
            if (clavePublicaExistente != null) {
                return clavePublicaExistente
            }

            val kpg = KeyPairGenerator.getInstance(ALGORITMO_EC)
            try {
                kpg.initialize(ECGenParameterSpec(CURVA_EC), secureRandom)
            } catch (_: Exception) {
                kpg.initialize(256, secureRandom)
            }

            val parClaves = kpg.generateKeyPair()
            val pubBase64 = Base64.getEncoder().encodeToString(parClaves.public.encoded)
            val privBase64 = Base64.getEncoder().encodeToString(parClaves.private.encoded)

            prefs.edit()
                .putString("pub_$usuarioId", pubBase64)
                .putString("priv_$usuarioId", privBase64)
                .commit()

            return pubBase64
        }
    }

    private fun obtenerClavePrivadaPropia(usuarioId: String): PrivateKey? {
        obtenerClavePublicaPropia(usuarioId)
        val privBase64 = prefs.getString("priv_$usuarioId", null) ?: return null
        val bytes = Base64.getDecoder().decode(privBase64)
        val keyFactory = KeyFactory.getInstance(ALGORITMO_EC)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun decodificarClavePublica(clavePublicaBase64: String): PublicKey {
        val bytes = Base64.getDecoder().decode(clavePublicaBase64)
        val keyFactory = KeyFactory.getInstance(ALGORITMO_EC)
        return keyFactory.generatePublic(X509EncodedKeySpec(bytes))
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

    private fun derivarClaveAes(
        miClavePrivada: PrivateKey,
        clavePublicaContraparte: PublicKey,
        miClavePublicaBase64: String,
        clavePublicaContraparteBase64: String
    ): SecretKeySpec {
        val keyAgreement = KeyAgreement.getInstance(ALGORITMO_ACUERDO)
        keyAgreement.init(miClavePrivada)
        keyAgreement.doPhase(clavePublicaContraparte, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Salt = SHA-256 de las dos claves públicas ordenadas lexicográficamente
        val (primera, segunda) = if (miClavePublicaBase64 < clavePublicaContraparteBase64) {
            Pair(miClavePublicaBase64, clavePublicaContraparteBase64)
        } else {
            Pair(clavePublicaContraparteBase64, miClavePublicaBase64)
        }
        val salt = MessageDigest.getInstance("SHA-256").digest("$primera|$segunda".toByteArray(Charsets.UTF_8))
        val info = "antired-e2ee-v3".toByteArray(Charsets.UTF_8)

        val aesKeyBytes = hkdfSha256(sharedSecret, salt, info, len = 32)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    override fun cifrar(
        textoPlano: String,
        clavePublicaDestinatarioBase64: String,
        miUsuarioId: String,
        destinatarioId: String,
        chatId: String
    ): String {
        try {
            val miPrivada = obtenerClavePrivadaPropia(miUsuarioId)
                ?: throw CifradoE2eeException("Clave privada local no disponible")
            val miPublicaBase64 = obtenerClavePublicaPropia(miUsuarioId)
            val publicaDestino = decodificarClavePublica(clavePublicaDestinatarioBase64)

            val secretKey = derivarClaveAes(
                miClavePrivada = miPrivada,
                clavePublicaContraparte = publicaDestino,
                miClavePublicaBase64 = miPublicaBase64,
                clavePublicaContraparteBase64 = clavePublicaDestinatarioBase64
            )

            val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(ALGORITMO_AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val aad = if (chatId.isNotBlank()) {
                "$miUsuarioId|$destinatarioId|$chatId"
            } else {
                "$miUsuarioId|$destinatarioId"
            }.toByteArray(Charsets.UTF_8)
            cipher.updateAAD(aad)

            val cipherBytes = cipher.doFinal(textoPlano.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.getEncoder().encodeToString(iv)
            val cipherBase64 = Base64.getEncoder().encodeToString(cipherBytes)
            return if (chatId.isNotBlank()) {
                "$PREFIX_E2EE_V4$chatId:$ivBase64:$cipherBase64"
            } else {
                "$PREFIX_E2EE_V3$ivBase64:$cipherBase64"
            }
        } catch (e: CifradoE2eeException) {
            throw e
        } catch (e: Exception) {
            throw CifradoE2eeException("No se pudo cifrar el mensaje: ${e.message}", e)
        }
    }

    override fun descifrar(
        textoCifrado: String,
        clavePublicaContraparteBase64: String,
        miUsuarioId: String,
        remitenteOriginalId: String,
        destinatarioOriginalId: String,
        chatId: String
    ): String {
        if (!esCifrado(textoCifrado)) {
            if (textoCifrado.startsWith(PREFIX_E2EE_V4) || textoCifrado.startsWith(PREFIX_E2EE_V3)) {
                return "[🔒 Mensaje cifrado - Formato inválido]"
            }
            return textoCifrado
        }

        return runCatching {
            val miPrivada = obtenerClavePrivadaPropia(miUsuarioId)
                ?: return "[🔒 Mensaje cifrado - Clave local no disponible]"
            val miPublicaBase64 = obtenerClavePublicaPropia(miUsuarioId)
            val publicaContraparte = decodificarClavePublica(clavePublicaContraparteBase64)

            val secretKey = derivarClaveAes(
                miClavePrivada = miPrivada,
                clavePublicaContraparte = publicaContraparte,
                miClavePublicaBase64 = miPublicaBase64,
                clavePublicaContraparteBase64 = clavePublicaContraparteBase64
            )

            val esV4 = textoCifrado.startsWith(PREFIX_E2EE_V4)
            val partes = if (esV4) {
                textoCifrado.removePrefix(PREFIX_E2EE_V4).split(":")
            } else {
                textoCifrado.removePrefix(PREFIX_E2EE_V3).split(":")
            }
            if ((esV4 && partes.size != 3) || (!esV4 && partes.size != 2)) {
                return "[🔒 Mensaje cifrado - Formato inválido]"
            }

            // H-09: En formato v4, validar que el chatId del payload coincida con el chatId de la conversación actual
            if (esV4 && chatId.isNotBlank() && partes[0] != chatId) {
                Log.w(TAG, "Rechazado descifrado E2EE v4: chatId de AAD '${partes[0]}' no coincide con el chatId esperado '$chatId'")
                return "[🔒 Mensaje cifrado para otra conversación]"
            }

            val iv = Base64.getDecoder().decode(if (esV4) partes[1] else partes[0])
            val cipherBytes = Base64.getDecoder().decode(if (esV4) partes[2] else partes[1])

            val cipher = Cipher.getInstance(ALGORITMO_AES_GCM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val destinoAAD = destinatarioOriginalId.ifBlank { miUsuarioId }
            val aadString = if (esV4) {
                val aadChatId = partes[0]
                "$remitenteOriginalId|$destinoAAD|$aadChatId"
            } else {
                "$remitenteOriginalId|$destinoAAD"
            }
            cipher.updateAAD(aadString.toByteArray(Charsets.UTF_8))

            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        }.getOrElse {
            "[🔒 Mensaje cifrado E2EE]"
        }
    }

    override fun esCifrado(contenido: String): Boolean {
        val base64Regex = Regex("^[A-Za-z0-9+/=]+$")
        if (contenido.startsWith(PREFIX_E2EE_V4)) {
            val payload = contenido.removePrefix(PREFIX_E2EE_V4)
            val partes = payload.split(":")
            if (partes.size != 3) return false
            return partes[1].isNotEmpty() && partes[2].isNotEmpty() &&
                    base64Regex.matches(partes[1]) && base64Regex.matches(partes[2])
        }
        if (contenido.startsWith(PREFIX_E2EE_V3)) {
            val payload = contenido.removePrefix(PREFIX_E2EE_V3)
            val partes = payload.split(":")
            if (partes.size != 2) return false
            return partes[0].isNotEmpty() && partes[1].isNotEmpty() &&
                    base64Regex.matches(partes[0]) && base64Regex.matches(partes[1])
        }
        return false
    }
}

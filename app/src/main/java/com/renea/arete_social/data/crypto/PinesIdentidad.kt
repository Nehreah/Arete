package com.renea.arete_social.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_NAME_CIFRADO = "antired_pines_cifrado"
private const val TAG = "PinesIdentidad"

/**
 * Estado de verificación de la identidad criptográfica de un peer (TOFU).
 */
enum class EstadoVerificacionIdentidad {
    VERIFICADO,        // Clave de firma validada por contacto directo o QR verificado
    NO_VERIFICADO,     // Clave vista únicamente por Mesh (offline)
    ALERTA_CONFLICTO   // La clave de firma presentada difiere de la clave previamente conocida
}

/**
 * Almacén de pines de identidad de claves públicas asociadas a cada remitente (TOFU: Trust On First Use).
 * Persistido de forma segura en [EncryptedSharedPreferences] respaldado por el Android Keystore.
 * Falla cerrado si el Keystore no está disponible.
 */
class PinesIdentidad(
    private val context: Context? = null,
    private val prefsOverride: SharedPreferences? = null
) {

    private val prefs: SharedPreferences by lazy {
        prefsOverride ?: abrirPrefsCifradas(
            context ?: error("Context o prefsOverride requerido para inicializar PinesIdentidad")
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
            Log.e(TAG, "Keystore no disponible para pines de identidad. Fallando cerrado por seguridad.", error)
            throw SecurityException("Almacén seguro de pines de identidad no disponible: ${error.message}", error)
        }
    }

    private fun clavesCandidatas(usuarioId: String): List<String> {
        val limpia = usuarioId.trim()
        val candidatos = mutableListOf(limpia)
        val norm = limpia.replace("-", "").lowercase()
        if (norm.isNotEmpty() && norm != limpia) candidatos.add(norm)
        if (limpia.startsWith("invitado-local-")) {
            val sin = limpia.removePrefix("invitado-local-")
            candidatos.add(sin)
            val sinNorm = sin.replace("-", "").lowercase()
            if (sinNorm.isNotEmpty() && sinNorm != sin) candidatos.add(sinNorm)
        } else if (limpia.startsWith("usuario-local-")) {
            val sin = limpia.removePrefix("usuario-local-")
            candidatos.add(sin)
            val sinNorm = sin.replace("-", "").lowercase()
            if (sinNorm.isNotEmpty() && sinNorm != sin) candidatos.add(sinNorm)
        } else {
            candidatos.add("invitado-local-$limpia")
            candidatos.add("usuario-local-$limpia")
        }
        return candidatos.distinct()
    }

    fun obtener(usuarioId: String): String? {
        for (c in clavesCandidatas(usuarioId)) {
            val p = prefs.getString("pin_$c", null) ?: prefs.getString(c, null)
            if (p != null) return p
        }
        return null
    }

    fun obtenerEstado(usuarioId: String): EstadoVerificacionIdentidad {
        for (c in clavesCandidatas(usuarioId)) {
            val str = prefs.getString("estado_$c", null)
            if (str != null) {
                return runCatching { EstadoVerificacionIdentidad.valueOf(str) }.getOrDefault(EstadoVerificacionIdentidad.NO_VERIFICADO)
            }
        }
        return EstadoVerificacionIdentidad.NO_VERIFICADO
    }

    fun fijar(
        usuarioId: String,
        clave: String,
        estado: EstadoVerificacionIdentidad = EstadoVerificacionIdentidad.NO_VERIFICADO
    ) {
        val pinExistente = obtener(usuarioId)
        val estadoExistente = obtenerEstado(usuarioId)

        // S-02: Si ya existe un pin previo y la nueva clave es discordante:
        if (pinExistente != null && pinExistente != clave) {
            // Un contacto VERIFICADO nunca se degrada ni sobreescribe por un paquete NO_VERIFICADO
            if (estadoExistente == EstadoVerificacionIdentidad.VERIFICADO && estado != EstadoVerificacionIdentidad.VERIFICADO) {
                Log.w(TAG, "Intento de sobreescribir identidad VERIFICADA de $usuarioId rechazado por seguridad.")
                return
            }
            // Si estaba en NO_VERIFICADO y llega una clave distinta sin verificación, marcar conflicto
            if (estado != EstadoVerificacionIdentidad.VERIFICADO) {
                Log.w(TAG, "Conflicto TOFU al fijar clave para $usuarioId: se mantiene pin previo y se alerta conflicto.")
                actualizarEstado(usuarioId, EstadoVerificacionIdentidad.ALERTA_CONFLICTO)
                return
            }
        }

        val editor = prefs.edit()
        for (c in clavesCandidatas(usuarioId)) {
            editor.putString("pin_$c", clave)
            editor.putString("estado_$c", estado.name)
            editor.remove(c)
        }
        editor.commit()
    }

    fun actualizarEstado(usuarioId: String, estado: EstadoVerificacionIdentidad) {
        val editor = prefs.edit()
        for (c in clavesCandidatas(usuarioId)) {
            editor.putString("estado_$c", estado.name)
        }
        editor.commit()
    }

    /**
     * Elimina el pin y el estado de identidad asociado a [usuarioId].
     * Permite reiniciar el handshake TOFU cuando el usuario confía en que el par
     * reinstaló la app legítimamente o cambió de dispositivo.
     */
    fun borrar(usuarioId: String) {
        val editor = prefs.edit()
        for (c in clavesCandidatas(usuarioId)) {
            editor.remove("pin_$c")
            editor.remove("estado_$c")
            editor.remove(c)
        }
        editor.commit()
    }
}

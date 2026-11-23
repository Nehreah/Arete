package com.renea.arete_social.feature.auth

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil

/**
 * Recuerda, en el propio dispositivo, cuál fue el último usuario con sesión
 * iniciada (con cuenta o invitado) de forma 100% local, síncrona y sin red.
 */
object GestorSesionLocal {

    private const val PREFS_NAME = "antired_sesion_local_prefs"
    private const val KEY_ULTIMO_USUARIO_ID = "ultimo_usuario_id"
    private const val KEY_ULTIMO_NOMBRE = "ultimo_nombre_perfil"
    private const val KEY_ULTIMA_FOTO = "ultima_foto_perfil"
    private const val KEY_ULTIMA_PORTADA = "ultima_portada_perfil"
    private const val KEY_MODO_TEMA = "modo_tema_comunidad"
    private const val KEY_ONBOARDING_COMPLETADO = "onboarding_completado_v1"
    private const val KEY_NOMBRE_MICHI = "nombre_michi_mascota"

    private var prefs: SharedPreferences? = null


    fun inicializar(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun inicializarConPrefs(prefsParaPrueba: SharedPreferences?) {
        prefs = prefsParaPrueba
    }

    /** Se llama tras registrar(), iniciarSesion() o entrarComoInvitado() exitosos. */
    fun guardarUltimoUsuario(usuarioId: String) {
        prefs?.edit()?.putString(KEY_ULTIMO_USUARIO_ID, usuarioId)?.apply()
    }

    fun obtenerUltimoUsuario(): String? = prefs?.getString(KEY_ULTIMO_USUARIO_ID, null)

    /**
     * Último nombre de perfil conocido en este dispositivo (registro/login, o la
     * última vez que [obtenerPorId] respondió con red). Es lo que se ve sin wifi:
     * en el perfil propio como respaldo, y en el radar BLE como nombre anunciado
     * a otros — ver [com.renea.arete_social.data.proximity.ble.ProximityManagerBle].
     */
    fun guardarUltimoNombre(nombre: String) {
        if (nombre.isBlank()) return
        prefs?.edit()?.putString(KEY_ULTIMO_NOMBRE, nombre)?.apply()
    }

    fun obtenerUltimoNombre(): String? = prefs?.getString(KEY_ULTIMO_NOMBRE, null)

    fun guardarUltimaFoto(foto: String?) {
        val editor = prefs?.edit() ?: return
        if (foto.isNullOrBlank() || foto.startsWith("data:")) {
            editor.remove(KEY_ULTIMA_FOTO)
        } else {
            editor.putString(KEY_ULTIMA_FOTO, foto)
        }
        editor.apply()
    }

    fun obtenerUltimaFoto(): String? {
        val f = prefs?.getString(KEY_ULTIMA_FOTO, null)
        return AlmacenMediaPerfil.migrarSiEsDataUrl(f, esAvatar = true)
    }

    /** Última portada conocida (ruta o URL), para mostrarla sin red. Con null o vacío se borra (la quitaron). */
    fun guardarUltimaPortada(portada: String?) {
        val editor = prefs?.edit() ?: return
        if (portada.isNullOrBlank() || portada.startsWith("data:")) {
            editor.remove(KEY_ULTIMA_PORTADA)
        } else {
            editor.putString(KEY_ULTIMA_PORTADA, portada)
        }
        editor.apply()
    }

    fun obtenerUltimaPortada(): String? {
        val p = prefs?.getString(KEY_ULTIMA_PORTADA, null)
        return AlmacenMediaPerfil.migrarSiEsDataUrl(p, esAvatar = false)
    }

    /**
     * Preferencia de modo día/noche de la pantalla de Comunidad (Plan de Fidelidad Visual
     * v2, sección 3 — "Persistir preferencia de tema"). Mismo patrón que [guardarUltimaFoto]:
     * lectura local, síncrona, sin red. `null` = "sin preferencia guardada todavía", para
     * que el llamador decida el default (hoy: OSCURO, ver [ModoTema]).
     */
    fun guardarModoTema(modoTema: com.renea.arete_social.gui.theme.ModoTema) {
        prefs?.edit()?.putString(KEY_MODO_TEMA, modoTema.name)?.apply()
    }

    fun obtenerModoTema(): com.renea.arete_social.gui.theme.ModoTema? =
        prefs?.getString(KEY_MODO_TEMA, null)?.let {
            runCatching { com.renea.arete_social.gui.theme.ModoTema.valueOf(it) }.getOrNull()
        }

    fun guardarUltimaVisibilidad(visibilidad: com.renea.arete_social.domain.model.Visibilidad) {
        prefs?.edit()?.putString("ultima_visibilidad", visibilidad.name)?.apply()
    }

    fun obtenerUltimaVisibilidad(): com.renea.arete_social.domain.model.Visibilidad =
        prefs?.getString("ultima_visibilidad", null)?.let {
            com.renea.arete_social.domain.model.Visibilidad.desdeServidor(it)
        } ?: com.renea.arete_social.domain.model.Visibilidad.VISIBLE

    /** Devuelve true si el usuario ya completó la bienvenida inicial (alias, avatar, michi y términos +18). */
    fun estaOnboardingCompletado(): Boolean =
        prefs?.getBoolean(KEY_ONBOARDING_COMPLETADO, false) ?: false

    fun guardarOnboardingCompletado(completado: Boolean = true) {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_COMPLETADO, completado)?.apply()
    }

    fun guardarNombreMichi(nombre: String) {
        if (nombre.isNotBlank()) {
            prefs?.edit()?.putString(KEY_NOMBRE_MICHI, nombre.trim())?.apply()
        }
    }

    fun obtenerNombreMichi(): String =
        prefs?.getString(KEY_NOMBRE_MICHI, null) ?: "Michi"

    /** Se llama al cerrar sesión explícitamente desde Perfil: si no se
     * borrara, la próxima vez que se abra la app (incluso sin wifi) se
     * volvería a entrar sola con este mismo usuario, como si nunca se
     * hubiera cerrado sesión. */
    fun limpiar() {
        prefs?.edit()
            ?.remove(KEY_ULTIMO_USUARIO_ID)
            ?.remove(KEY_ULTIMO_NOMBRE)
            ?.remove(KEY_ULTIMA_FOTO)
            ?.remove(KEY_ULTIMA_PORTADA)
            ?.remove("ultima_visibilidad")
            ?.remove(KEY_ONBOARDING_COMPLETADO)
            ?.remove(KEY_NOMBRE_MICHI)
            ?.apply()
    }
}
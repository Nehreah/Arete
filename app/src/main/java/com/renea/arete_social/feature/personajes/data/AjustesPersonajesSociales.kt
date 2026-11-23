package com.renea.arete_social.feature.personajes.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Control del interruptor de funcionalidad (Feature Flag) para el Mundo Social de Personajes.
 *
 * Por defecto permanece APAGADO (`false`) en la Fase 1 para permitir la introducción de
 * la arquitectura sin alterar la experiencia de usuario actual de la mascota.
 */
object AjustesPersonajesSociales {

    private const val PREFS_NAME = "antired_personajes_sociales"
    private const val KEY_MUNDO_SOCIAL_HABILITADO = "mundo_social_habilitado"

    private fun resolverPrefs(context: Context?, prefsOverride: SharedPreferences?): SharedPreferences? {
        return prefsOverride ?: context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Comprueba si el módulo del mundo social de personajes está habilitado.
     * @return false por defecto.
     */
    fun estaHabilitado(context: Context? = null, prefsOverride: SharedPreferences? = null): Boolean {
        val p = resolverPrefs(context, prefsOverride) ?: return false
        return p.getBoolean(KEY_MUNDO_SOCIAL_HABILITADO, false)
    }

    /**
     * Activa o desactiva el mundo social de personajes.
     */
    fun definirHabilitado(context: Context? = null, habilitado: Boolean, prefsOverride: SharedPreferences? = null) {
        val p = resolverPrefs(context, prefsOverride) ?: return
        p.edit().putBoolean(KEY_MUNDO_SOCIAL_HABILITADO, habilitado).apply()
    }
}

package com.renea.arete_social.data.local.ajustes

import android.content.Context

/**
 * Almacena el estado de notificaciones (activadas o silenciadas) para cada comunidad localmente.
 */
object AjustesNotificacionesComunidad {
    private const val PREFS = "arete_notificaciones_comunidades"
    private const val PREFIJO_SILENCIADA = "silenciada_"

    fun estaSilenciada(context: Context, comunidadId: String): Boolean {
        return prefs(context).getBoolean(PREFIJO_SILENCIADA + comunidadId, false)
    }

    fun alternarSilenciado(context: Context, comunidadId: String): Boolean {
        val clave = PREFIJO_SILENCIADA + comunidadId
        val nuevoEstado = !estaSilenciada(context, comunidadId)
        prefs(context).edit().putBoolean(clave, nuevoEstado).apply()
        return nuevoEstado
    }

    fun definirSilenciada(context: Context, comunidadId: String, silenciada: Boolean) {
        prefs(context).edit().putBoolean(PREFIJO_SILENCIADA + comunidadId, silenciada).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

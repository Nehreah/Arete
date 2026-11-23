package com.renea.arete_social.data.local.ajustes

import android.content.Context

/** Preferencias locales de Parche y estado efímero de Michi. */
object AjustesParche {
    private const val PREFS = "arete_parche"
    private const val ACTIVADO = "activado"
    private const val ESTADO = "estado_texto"
    private const val ESTADO_HASTA = "estado_hasta"
    const val DURACION_ESTADO_MS = 24L * 60L * 60L * 1000L

    fun activado(context: Context): Boolean = prefs(context).getBoolean(ACTIVADO, false)
    fun definirActivado(context: Context, activo: Boolean) {
        prefs(context).edit().putBoolean(ACTIVADO, activo).apply()
        // Compartir por cercanía solo se habilita cuando la persona activa Parche expresamente.
        AjustesPases.definirCompartir(context, activo)
    }

    fun guardarEstado(context: Context, texto: String, ahora: Long = System.currentTimeMillis()) {
        val limpio = texto.trim().take(60)
        prefs(context).edit()
            .putString(ESTADO, limpio)
            .putLong(ESTADO_HASTA, if (limpio.isEmpty()) 0L else ahora + DURACION_ESTADO_MS)
            .apply()
    }

    fun estado(context: Context, ahora: Long = System.currentTimeMillis()): String? {
        val p = prefs(context)
        val texto = p.getString(ESTADO, null)?.trim().orEmpty()
        val vence = p.getLong(ESTADO_HASTA, 0L)
        if (texto.isEmpty()) return null
        if (vence <= ahora) {
            p.edit().remove(ESTADO).remove(ESTADO_HASTA).apply()
            return null
        }
        return texto.take(60)
    }

    fun venceEstado(context: Context): Long = prefs(context).getLong(ESTADO_HASTA, 0L)

    /** Limpia el estado efímero y la activación de Parche al cerrar sesión. */
    fun limpiar(context: Context) {
        prefs(context).edit().clear().apply()
        AjustesPases.definirCompartir(context, false)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

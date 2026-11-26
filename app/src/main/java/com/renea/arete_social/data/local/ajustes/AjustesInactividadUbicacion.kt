package com.renea.arete_social.data.local.ajustes

import android.content.Context

/**
 * Controla la configuración de auto-pausa de ubicación por inactividad.
 * Si la app pasa más de [minutosInactividad] en segundo plano sin usarse,
 * se detiene automáticamente la emisión de ubicación para ahorrar batería y proteger la privacidad.
 */
object AjustesInactividadUbicacion {
    private const val PREFS = "arete_inactividad_ubicacion"
    private const val CLAVE_MINUTOS = "minutos_inactividad"

    const val MINUTOS_DEFECTO = 30
    val OPCIONES_MINUTOS = listOf(15, 30, 60, 0) // 0 significa desactivado

    fun minutosInactividad(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(CLAVE_MINUTOS, MINUTOS_DEFECTO)

    fun definirMinutosInactividad(context: Context, minutos: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(CLAVE_MINUTOS, minutos).apply()
    }

    fun estaActivado(context: Context): Boolean = minutosInactividad(context) > 0
}

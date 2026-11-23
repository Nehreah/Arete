package com.renea.arete_social.data.local.ajustes

import android.content.Context

/**
 * Ajuste «Compartir por cercanía»: si está activado y la persona cercana solicita sincronizar, recibe
 * lo que descubriste (lugares y eventos PÚBLICOS) durante 24 h. Recibir pases siempre está permitido.
 * Por defecto: desactivado. Se activa explícitamente desde Michi → Activar parche.
 */
object AjustesPases {
    private const val PREFS = "antired_ajustes_pases"
    private const val CLAVE_COMPARTIR = "compartir_por_cercania"

    fun compartirActivado(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CLAVE_COMPARTIR, false)

    fun definirCompartir(context: Context, activado: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(CLAVE_COMPARTIR, activado).apply()
    }
}

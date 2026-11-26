package com.renea.arete_social.data.local.ajustes

import android.content.Context

/**
 * Controla la configuración de consumo de red y datos móviles para la red mallada híbrida Aréte.
 *
 * ## Políticas
 * - [soloTextoEnDatosMoviles]: Si está activo (por defecto), sobre conexión de datos móviles
 *   únicamente se transmiten paquetes ligeros (mensajes de texto y metadatos ≤ 16 KB).
 *   Los medios pesados (fotos de perfil completas, murales e imágenes) quedan pospuestos
 *   para transmitirse sobre Wi-Fi o Bluetooth.
 */
object AjustesRed {
    private const val PREFS = "arete_ajustes_red"
    private const val CLAVE_SOLO_TEXTO = "solo_texto_en_datos_moviles"

    /** Límite de bytes para considerar un paquete como ligero (texto/señalización) */
    const val LIMITE_PAQUETE_LIGERO_BYTES = 16 * 1024

    @Volatile
    var prefsOverride: android.content.SharedPreferences? = null

    private fun prefs(context: Context): android.content.SharedPreferences =
        prefsOverride ?: context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val CLAVE_RELAYS = "relays_comunitarios"

    /**
     * Relays públicos y comunitarios abiertos de la red global federada Nostr.
     * Mantenidos por la comunidad de software libre 24/7 sin registro ni costo.
     */
    val RELAYS_POR_DEFECTO = listOf(
        "wss://nos.lol",              // Acepta kind 1059 (NIP-59), permisivo, comunitario
        "wss://relay.damus.io",       // Acepta kind 1059 (NIP-59), alta disponibilidad
        "wss://relay.nostr.band",     // Relay global permisivo con buen soporte de búsqueda
        "wss://nostr.wine",           // Relay premium permisivo con historial largo
        "wss://relay.primal.net",     // Relay de Primal (alta disponibilidad, puede filtrar algunos kinds)
        "wss://relay.snort.social"    // Relay de Snort (respaldo)
    )

    fun soloTextoEnDatosMoviles(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_SOLO_TEXTO, true)

    fun definirSoloTextoEnDatosMoviles(context: Context, activar: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(CLAVE_SOLO_TEXTO, activar)
            .apply()
    }

    /**
     * Obtiene la lista de servidores relay configurados por el usuario o los predeterminados.
     */
    fun obtenerRelaysConfigurados(context: Context): List<String> {
        val guardados = prefs(context).getStringSet(CLAVE_RELAYS, null)
        return if (guardados.isNullOrEmpty()) {
            RELAYS_POR_DEFECTO
        } else {
            guardados.toList()
        }
    }

    /**
     * Guarda la lista completa de relays comunitarios autorizados.
     */
    fun guardarRelays(context: Context, relays: List<String>) {
        val limpiados = relays.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.toSet()
        prefs(context)
            .edit()
            .putStringSet(CLAVE_RELAYS, limpiados)
            .apply()
    }

    /**
     * Añade un nuevo servidor relay a la lista.
     */
    fun agregarRelay(context: Context, url: String) {
        val actual = obtenerRelaysConfigurados(context).toMutableList()
        val limpia = url.trim().trimEnd('/')
        if (limpia.isNotBlank() && !actual.contains(limpia)) {
            actual.add(limpia)
            guardarRelays(context, actual)
        }
    }

    /**
     * Elimina un servidor relay de la lista.
     */
    fun eliminarRelay(context: Context, url: String) {
        val actual = obtenerRelaysConfigurados(context).toMutableList()
        val limpia = url.trim().trimEnd('/')
        actual.remove(limpia)
        guardarRelays(context, actual)
    }
}

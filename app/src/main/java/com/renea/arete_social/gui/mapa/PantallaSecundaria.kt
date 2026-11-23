package com.renea.arete_social.gui.mapa

import com.renea.arete_social.domain.model.Ubicacion

/**
 * Antes, cosas como "crear comunidad" o "ver un lugar" eran rutas propias del
 * NavHost (ver AreteNavHost.kt en versiones anteriores): al navegar a ellas,
 * la pantalla completa se reemplazaba y la "pantalla principal" (el módulo de
 * arriba en MapaScreen, donde vive el mapa/comunidades/perfil) desaparecía.
 *
 * Ahora TODAS estas pantallas se muestran DENTRO de ese mismo módulo superior
 * (ver ContenidoRadar.pilaSecundaria en MapaScreen.kt): en vez de navegar a una
 * ruta nueva del NavHost, se apilan aquí, y quien decide qué dibujar dentro del
 * bloque del mapa es [PantallaSecundariaHost]. El NavHost solo sigue existiendo
 * para Login / Registro / Mapa.
 */
sealed interface PantallaSecundaria {

    data class CrearLugarEn(val ubicacion: Ubicacion) : PantallaSecundaria

    data class VerLugar(val lugarId: String) : PantallaSecundaria

    data class CrearEventoEn(val lugarId: String) : PantallaSecundaria

    data class CrearEvento(
        val comunidadId: String? = null,
        val lugarId: String? = null,
        val ubicacionInicial: Ubicacion? = null
    ) : PantallaSecundaria

    /**
     * Paso previo a crear un evento: lleva al MAPA, que se oscurece y muestra una mira en el
     * centro. Al pulsar "Seleccionar lugar" se abre el formulario ([CrearEvento]) con ese punto.
     * [comunidadId] viene informado cuando el evento se crea desde una comunidad.
     */
    data class SeleccionarLugarEvento(val comunidadId: String? = null) : PantallaSecundaria

    data class VerEvento(val eventoId: String) : PantallaSecundaria

    data object CrearComunidadNueva : PantallaSecundaria

    data class VerComunidad(val comunidadId: String) : PantallaSecundaria

    /** Abre un chat cuyo id YA es un id de chat (UUID estándar, `local-dm-…` o el general). */
    data class AbrirChat(
        val chatId: String,
        val titulo: String = "Chat",
        val esSala: Boolean = false
    ) : PantallaSecundaria

    /**
     * Abre (o crea) el chat directo con una PERSONA. Se distingue de [AbrirChat] a propósito:
     * un UUID de usuario y un UUID de chat son indistinguibles por su forma, y adivinar cuál era
     * cuál dejaba chats abiertos con el id del usuario como chatId (nunca sincronizaban ni salían
     * en Activos).
     */
    data class AbrirChatConUsuario(val usuarioId: String, val titulo: String = "Chat") : PantallaSecundaria

    /** Perfil público de otra persona, abierto desde el mapa o la lista de "personas cercanas". */
    data class VerPerfil(val usuarioId: String) : PantallaSecundaria

    /** Videollamada Persona a Persona (1 a 1). */
    data class LlamadaVideo(
        val contactoId: String,
        val nombre: String,
        val foto: String? = null,
        val esEntrante: Boolean = false
    ) : PantallaSecundaria

    /** Habitación del usuario (personal o visita a contacto). */
    data class Habitacion(
        val ownerId: String,
        val ownerNombre: String? = null
    ) : PantallaSecundaria

    /** Identidad criptográfica soberana local-first (12 palabras, clave IK). */
    data object GestionarIdentidad : PantallaSecundaria
}

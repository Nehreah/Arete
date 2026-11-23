package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Usuario(
    val id: String,
    val nombre: String,
    val foto: String? = null,
    val descripcion: String = "",
    val estadoVisibilidad: Visibilidad = Visibilidad.VISIBLE,
    val ubicacion: Ubicacion? = null,
    val clavePublicaE2ee: String? = null,
    val clavePublicaFirma: String? = null,
    val hobby: String? = null,
    val deporte: String? = null,
    val ritmoMusical: String? = null,
    val serieFavorita: String? = null,
    val libroFavorito: String? = null,
    val signoZodiacal: String? = null,
    /**
     * Privacidad del perfil. false = público (cualquier persona ve estados, fotos y librería);
     * true = privado (solo la propia persona y sus amistades aceptadas).
     */
    val perfilPrivado: Boolean = false,
    /** URL de la imagen de portada del perfil (null = sin portada, se muestra el degradado por defecto). */
    val portada: String? = null,
    val parcheActivo: Boolean = false,
    val estadoMichi: String? = null,
    val estadoMichiHasta: Long? = null
)

package com.renea.arete_social.feature.auth

import com.renea.arete_social.domain.model.Usuario

sealed interface PerfilUiState {
    data object Cargando : PerfilUiState
    data class Listo(
        val usuario: Usuario,
        val guardando: Boolean = false,
        // Aparte de "guardando" (nombre/descripción/visibilidad) porque subir
        // la foto es una acción independiente disparada desde el cuadrado de
        // avatar, no desde el botón "Guardar" del formulario.
        val subiendoFoto: Boolean = false,
        // Igual que subiendoFoto pero para la portada del perfil.
        val subiendoPortada: Boolean = false,
        // true cuando este perfil se armó localmente (versión en caché o sin conexión).
        val sinConexion: Boolean = false
    ) : PerfilUiState
    data class Error(val mensaje: String) : PerfilUiState
}
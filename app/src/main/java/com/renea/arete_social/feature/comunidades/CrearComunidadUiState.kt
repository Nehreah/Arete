package com.renea.arete_social.feature.comunidades

sealed interface CrearComunidadUiState {
    data object Editando : CrearComunidadUiState
    data object Guardando : CrearComunidadUiState
    data class Creada(val comunidadId: String) : CrearComunidadUiState
    data class Error(val mensaje: String) : CrearComunidadUiState
}

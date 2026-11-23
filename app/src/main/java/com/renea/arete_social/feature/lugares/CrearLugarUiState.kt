package com.renea.arete_social.feature.lugares

sealed interface CrearLugarUiState {
    data object Editando : CrearLugarUiState
    data object Guardando : CrearLugarUiState
    data class Creado(val lugarId: String) : CrearLugarUiState
    data class Error(val mensaje: String) : CrearLugarUiState
}

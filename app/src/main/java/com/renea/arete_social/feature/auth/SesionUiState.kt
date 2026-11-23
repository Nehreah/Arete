package com.renea.arete_social.feature.auth

sealed interface SesionUiState {
    data object Inactivo : SesionUiState
    data object Cargando : SesionUiState
    data class Autenticado(val usuarioId: String, val soloLocal: Boolean = false, val esInvitado: Boolean = false) : SesionUiState
    data class Error(val mensaje: String) : SesionUiState
}

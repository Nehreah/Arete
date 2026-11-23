package com.renea.arete_social.feature.eventos

import com.renea.arete_social.domain.model.Evento

sealed interface EventoDetalleUiState {
    data object Cargando : EventoDetalleUiState
    data class Listo(
        val evento: Evento,
        val uniendose: Boolean = false,
        val participantesConRoles: List<com.renea.arete_social.domain.model.ParticipanteEvento> = emptyList(),
        val puedeGestionar: Boolean = false,
        val esCreador: Boolean = false
    ) : EventoDetalleUiState
    data class Error(val mensaje: String) : EventoDetalleUiState
}

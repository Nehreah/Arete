package com.renea.arete_social.feature.eventos

sealed interface CrearEventoUiState {
    data object Editando : CrearEventoUiState
    data object Guardando : CrearEventoUiState
    data class Creado(val eventoId: String) : CrearEventoUiState
    data class Error(val mensaje: String) : CrearEventoUiState
}

/**
 * Horas rápidas para elegir, en vez de un selector de hora minuto a minuto —
 * suficiente para "Reunión de estudiantes — 4:00 PM", como en el documento original.
 */
enum class HoraEvento(val etiqueta: String, val horaDelDia: Int) {
    MEDIODIA("12:00 m.", 12),
    DOS_PM("2:00 p.m.", 14),
    CUATRO_PM("4:00 p.m.", 16),
    SEIS_PM("6:00 p.m.", 18),
    OCHO_PM("8:00 p.m.", 20),
    DIEZ_PM("10:00 p.m.", 22)
}

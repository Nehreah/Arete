package com.renea.arete_social.feature.lugares

import com.renea.arete_social.domain.model.Lugar

sealed interface LugarDetalleUiState {
    data object Cargando : LugarDetalleUiState
    data class Listo(val lugar: Lugar) : LugarDetalleUiState
    data class Error(val mensaje: String) : LugarDetalleUiState
}

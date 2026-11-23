package com.renea.arete_social.feature.comunidades

import com.renea.arete_social.domain.model.Comunidad

/**
 * Estado de la pantalla intermedia "Comunidades" (listado), previa a
 * CrearComunidadScreen. Es deliberadamente simple: solo lista lo que ya
 * existe, sin unirse ni filtrar (eso vive en ComunidadDetalleScreen).
 */
sealed interface ComunidadesUiState {
    data object Cargando : ComunidadesUiState
    data class Listo(val comunidades: List<Comunidad>) : ComunidadesUiState
    data class Error(val mensaje: String) : ComunidadesUiState
}

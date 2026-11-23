package com.renea.arete_social.feature.libreria

import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.TipoObra

data class LibreriaUiState(
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val disponible: Boolean = true,
    val obras: List<ObraLibreria> = emptyList(),
    val mensaje: String? = null
) {
    fun deTipo(tipo: TipoObra): List<ObraLibreria> = obras.filter { it.tipo == tipo }
}

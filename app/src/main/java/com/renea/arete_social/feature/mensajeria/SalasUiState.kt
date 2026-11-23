package com.renea.arete_social.feature.mensajeria

import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.VisibilidadMapa

sealed interface SalasUiState {
    data object Cargando : SalasUiState
    data class Listo(
        val salas: List<Sala> = emptyList(),
        val salaGeneral: Sala? = null,
        val creando: Boolean = false,
        val mensajeError: String? = null,
        /** chatId del chat regional que se destaca (el de la región más cercana), o null si aún no hay ubicación. */
        val regionDestacadaChatId: String? = null,
        /** chatId de lugares/eventos visibles → de dónde viene y cuándo se oculta (etiquetas de la lista). */
        val visibilidadPorChat: Map<String, VisibilidadMapa> = emptyMap(),
        /** True mientras el Michi de la persona está en Modo Súper. */
        val modoSuperActivo: Boolean = false
    ) : SalasUiState
    data class Error(val mensaje: String) : SalasUiState
}

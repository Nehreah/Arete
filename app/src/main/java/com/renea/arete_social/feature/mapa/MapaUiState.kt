package com.renea.arete_social.feature.mapa

import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadMapa

sealed interface MapaUiState {
    data object Cargando : MapaUiState
    data class Listo(
        val ubicacionPropia: Ubicacion? = null,
        val personasCercanas: List<Dispositivo> = emptyList(),
        /** Lugares visibles: los de 500 m, los descubiertos en las últimas 24 h y, con el Súper, los de la zona. */
        val lugaresCercanos: List<Lugar> = emptyList(),
        /** Eventos visibles, con la misma lógica que [lugaresCercanos]. */
        val eventosCercanos: List<Evento> = emptyList(),
        /** De dónde viene cada lugar/evento visible y cuándo se oculta (clave «LUGAR:id» / «EVENTO:id»). */
        val visibilidad: Map<String, VisibilidadMapa> = emptyMap(),
        /** True mientras el Michi de la persona está en Modo Súper. */
        val modoSuperActivo: Boolean = false,
        /** Región más cercana a la persona (la misma que se destaca en la lista de chats). */
        val regionActual: Region? = null,
        /** Obras literarias compartidas en las últimas 24 h por personas cercanas. */
        val obrasCercanasMapa: List<com.renea.arete_social.domain.model.ObraLibreria> = emptyList()
    ) : MapaUiState
    data class Error(val mensaje: String) : MapaUiState
}
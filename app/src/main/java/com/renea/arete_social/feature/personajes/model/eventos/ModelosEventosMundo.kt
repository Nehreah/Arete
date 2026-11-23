package com.renea.arete_social.feature.personajes.model.eventos

import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.personajes.model.ambiente.TipoAmbiente
import kotlinx.serialization.Serializable

/**
 * Categorías de eventos temporales que ocurren periódicamente en los ambientes compartidos.
 */
@Serializable
enum class TipoEventoMundo(val nombreVisible: String) {
    TORNEO_FITNESS_GYM("Torneo Michi-Fit"),
    FESTIVAL_PRIMAVERA_PARQUE("Festival de Primavera"),
    TERTULIA_POESIA_CAFE("Tertulia Literaria")
}

/**
 * Evento temporal activo en el mundo social de los personajes.
 */
@Serializable
data class EventoMundo(
    val id: String,
    val tipo: TipoEventoMundo,
    val titulo: String,
    val descripcion: String,
    val ambienteAfin: TipoAmbiente,
    val temperamentoDestacado: Temperamento,
    val multiplicadorConfianza: Float = 1.5f,
    val objetoRecompensaId: String? = null
)

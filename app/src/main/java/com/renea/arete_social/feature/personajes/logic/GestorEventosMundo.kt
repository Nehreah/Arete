package com.renea.arete_social.feature.personajes.logic

import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.personajes.model.ambiente.AccionAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.TipoAmbiente
import com.renea.arete_social.feature.personajes.model.eventos.EventoMundo
import com.renea.arete_social.feature.personajes.model.eventos.TipoEventoMundo
import java.util.Calendar

/**
 * Gestor del calendario y rotación de eventos temporales en el mundo ficticio de las mascotas.
 */
object GestorEventosMundo {

    val EVENTOS_CATALOGO = listOf(
        EventoMundo(
            id = "torneo_fit_semanal",
            tipo = TipoEventoMundo.TORNEO_FITNESS_GYM,
            titulo = "Torneo Michi-Fit del Gimnasio",
            descripcion = "¡Quema calorías y levanta trofeos! Bonificación extra de confianza para personajes Enérgicos.",
            ambienteAfin = TipoAmbiente.GIMNASIO,
            temperamentoDestacado = Temperamento.ENERGETICO,
            multiplicadorConfianza = 1.5f,
            objetoRecompensaId = "trofeo_torneo_gym"
        ),
        EventoMundo(
            id = "festival_primavera_parque",
            tipo = TipoEventoMundo.FESTIVAL_PRIMAVERA_PARQUE,
            titulo = "Festival de Flores en el Parque",
            descripcion = "Disfruta de la brisa primaveral. Bonificación especial para personajes Tranquilos y Curiosos.",
            ambienteAfin = TipoAmbiente.PARQUE,
            temperamentoDestacado = Temperamento.TRANQUILO,
            multiplicadorConfianza = 1.5f,
            objetoRecompensaId = "farol_festival_parque"
        ),
        EventoMundo(
            id = "tertulia_cafe_pixel",
            tipo = TipoEventoMundo.TERTULIA_POESIA_CAFE,
            titulo = "Tertulia de Café y Poesía",
            descripcion = "Historias inolvidables y café caliente. Bonificación de confianza para Artistas y Sociables.",
            ambienteAfin = TipoAmbiente.CAFETERIA,
            temperamentoDestacado = Temperamento.ARTISTA,
            multiplicadorConfianza = 1.5f,
            objetoRecompensaId = "taza_dorada_cafe"
        )
    )

    /**
     * Retorna el evento que se encuentra activo en el momento especificado.
     * Rota de forma determinista según el día de la semana para que todos los usuarios
     * coincidan en el mismo evento sin necesidad de consultar al servidor continuamente.
     */
    fun eventoActivo(ahoraMillis: Long = System.currentTimeMillis()): EventoMundo {
        val cal = Calendar.getInstance().apply { timeInMillis = ahoraMillis }
        val diaSemana = cal.get(Calendar.DAY_OF_WEEK) // 1=Domingo, 2=Lunes, ... 7=Sábado

        return when (diaSemana) {
            Calendar.MONDAY, Calendar.TUESDAY -> EVENTOS_CATALOGO[0] // Gimnasio
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY -> EVENTOS_CATALOGO[1] // Parque
            else -> EVENTOS_CATALOGO[2] // Fin de semana: Cafetería
        }
    }

    /**
     * Calcula el multiplicador de confianza aplicado a una actividad considerando el evento activo y el temperamento.
     */
    fun calcularMultiplicador(
        evento: EventoMundo,
        temperamento: Temperamento,
        accion: AccionAmbiente
    ): Float {
        var multi = 1.0f
        if (accion.temperamentoAfin == evento.temperamentoDestacado || temperamento == evento.temperamentoDestacado) {
            multi = evento.multiplicadorConfianza
        }
        return multi
    }
}

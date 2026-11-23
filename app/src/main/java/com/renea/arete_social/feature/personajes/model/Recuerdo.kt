package com.renea.arete_social.feature.personajes.model

import kotlinx.serialization.Serializable

/**
 * Categoría del recuerdo generado entre personajes.
 */
@Serializable
enum class TipoRecuerdo(val titulo: String) {
    PRIMER_ENCUENTRO("Primer Encuentro"),
    VISITA_CASA("Visita en Casa"),
    ACTIVIDAD_PARQUE("Tarde en el Parque"),
    ENTRENAMIENTO_GIMNASIO("Sesión de Gimnasio"),
    CHARLA_CAFETERIA("Charla en la Cafetería"),
    REGALO_INTERCAMBIADO("Regalo Compartido")
}

/**
 * Evento significativo vivido entre el personaje local y un amigo.
 *
 * @param id Identificador único del recuerdo.
 * @param fechaMillis Marca de tiempo en que ocurrió el evento.
 * @param tipo Categoría del recuerdo.
 * @param conQuienId Identificador del personaje copartícipe.
 * @param conQuienNombre Nombre del personaje en el momento del evento.
 * @param lugar Nombre del ambiente o escenario (ej. "Casa de Michi", "Parque").
 * @param textoCorto Descripción validada del suceso (máx 120 caracteres).
 * @param firmaMutua Firma criptográfica conjunta (recibo v2) requerida para persistencia oficial.
 */
@Serializable
data class Recuerdo(
    val id: String,
    val fechaMillis: Long,
    val tipo: TipoRecuerdo,
    val conQuienId: String,
    val conQuienNombre: String,
    val lugar: String,
    val textoCorto: String,
    val firmaMutua: String? = null
)

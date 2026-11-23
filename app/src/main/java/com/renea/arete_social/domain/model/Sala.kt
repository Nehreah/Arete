package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TipoSala {
    GENERAL,
    PERSONALIZADA,
    COMUNIDAD,
    EVENTO,

    /** Chat de una ciudad principal. Siempre visible; el de la región más cercana se destaca. */
    REGIONAL,

    /** Chat de un lugar del mapa. Solo visible mientras ese lugar sea visible para ti. */
    LUGAR
}

@Serializable
data class Sala(
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val chatId: String,
    val creadorId: String,
    val esPublica: Boolean = true,
    val clave: String? = null,
    val tipo: TipoSala = TipoSala.PERSONALIZADA,
    val entidadId: String? = null,
    val fechaCreacion: Long = System.currentTimeMillis()
)

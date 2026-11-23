package com.renea.arete_social.domain.model

data class PublicacionMural(
    val id: String,
    val autorId: String,
    val autorNombre: String,
    val contenido: String,
    val imagenUrl: String?,
    val comunidadId: String?,
    val creadaEn: Long
)

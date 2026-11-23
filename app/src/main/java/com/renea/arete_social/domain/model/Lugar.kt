package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Lugar(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val ubicacion: Ubicacion,
    val chatId: String
)

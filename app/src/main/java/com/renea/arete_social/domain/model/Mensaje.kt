package com.renea.arete_social.domain.model

data class Mensaje(
    val id: String = "",
    val chatId: String = "",
    val remitenteId: String = "",
    val contenido: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
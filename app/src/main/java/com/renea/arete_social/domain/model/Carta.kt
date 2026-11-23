package com.renea.arete_social.domain.model

/** Carta escrita por una persona a una de sus amistades. Es inmutable salvo por [leida]. */
data class Carta(
    val id: String,
    val remitenteId: String,
    val remitenteNombre: String,
    val destinatarioId: String,
    val destinatarioNombre: String,
    val contenido: String,
    val leida: Boolean,
    val creadaEn: Long
)

/** Datos que aporta quien redacta; el repositorio completa id, fecha y remitente. */
data class NuevaCarta(
    val destinatarioId: String,
    val destinatarioNombre: String,
    val contenido: String
)

object LimitesCarta {
    const val MAX_CARACTERES = 4_000
}

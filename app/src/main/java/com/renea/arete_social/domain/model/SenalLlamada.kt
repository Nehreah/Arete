package com.renea.arete_social.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SenalLlamada(
    val id: String? = null,
    val tipo: String,
    @SerialName("remitente_id") val remitenteId: String,
    @SerialName("destinatario_id") val destinatarioId: String? = null,
    val payload: JsonObject,
    val consumido: Boolean = false,
    @SerialName("creado_en") val creadoEn: String? = null
)

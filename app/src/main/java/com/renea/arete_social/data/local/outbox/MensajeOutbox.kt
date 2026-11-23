package com.renea.arete_social.data.local.outbox

import com.renea.arete_social.domain.model.Mensaje

enum class EstadoEnvioOutbox {
    PENDIENTE,
    ENVIANDO,
    ENVIADO
}

/**
 * Entidad de cola de salida local (Outbox Pattern).
 * Representa un mensaje pendiente de envío a la red Mesh.
 */
data class MensajeOutbox(
    val id: String,
    val chatId: String,
    val remitenteId: String,
    val destinatarioId: String? = null,
    val contenido: String,
    val timestamp: Long,
    val estado: EstadoEnvioOutbox = EstadoEnvioOutbox.PENDIENTE,
    val intentos: Int = 0
) {
    fun toDomain(): Mensaje = Mensaje(
        id = id,
        chatId = chatId,
        remitenteId = remitenteId,
        contenido = contenido,
        timestamp = timestamp
    )
}

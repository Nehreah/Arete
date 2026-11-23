package com.renea.arete_social.domain.model

import java.util.UUID

enum class TipoNotificacionTactica {
    LLAMADA,
    MENSAJE,
    AMIGO_AGREGADO,
    SOLICITUD_AMISTAD,
    AMISTAD_ACEPTADA,
    REGALO_MASCOTA
}

data class NotificacionTactica(
    val id: String = UUID.randomUUID().toString(),
    val tipo: TipoNotificacionTactica,
    val titulo: String,
    val subtitulo: String,
    val remitenteId: String,
    val remitenteNombre: String,
    val remitenteFoto: String? = null,
    val chatId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SolicitudAmistad(
    val id: String = UUID.randomUUID().toString(),
    val remitenteId: String,
    val remitenteNombre: String,
    val remitenteFoto: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

package com.renea.arete_social.feature.mensajeria

/**
 * Versión de Mensaje ya lista para pintar: trae el nombre del remitente resuelto
 * (Mensaje solo trae remitenteId) y si es del usuario actual, para alinear la
 * burbuja a la derecha o a la izquierda.
 */
data class MensajeUi(
    val id: String = "",
    val remitenteId: String = "",
    val remitenteNombre: String = "",
    /** Foto de perfil del remitente (null = sin foto: la UI pinta su inicial). */
    val remitenteFoto: String? = null,
    val contenido: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val esPropio: Boolean = false,
    val esE2ee: Boolean = false
)

/** La otra persona en un chat 1-a-1. Alimenta la cabecera tocable que abre su perfil. */
data class InterlocutorUi(
    val id: String,
    val nombre: String,
    val foto: String? = null
)

sealed interface ChatUiState {
    data object Cargando : ChatUiState
    data class Listo(
        val mensajes: List<MensajeUi> = emptyList(),
        val enviando: Boolean = false,
        val esE2ee: Boolean = false,
        /** Mensaje visible cuando un envío se bloqueó (p.ej. falló el cifrado). null = sin error. */
        val errorEnvio: String? = null,
        /** Solo en chats 1-a-1; null en salas, comunidades y chat general. */
        val interlocutor: InterlocutorUi? = null,
        /** Aviso de moderación (bloqueo / reporte enviado). Se consume una sola vez. */
        val avisoModeracion: String? = null
    ) : ChatUiState
    data class Error(val mensaje: String) : ChatUiState
}

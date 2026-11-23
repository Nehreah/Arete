package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Chat
import com.renea.arete_social.domain.model.Mensaje
import com.renea.arete_social.domain.model.TipoChat
import kotlinx.coroutines.flow.Flow

interface RepositorioMensajes {
    suspend fun crearChat(tipo: TipoChat): Chat
    suspend fun obtenerOCrearChatDirecto(usuarioId1: String, usuarioId2: String): String
    suspend fun buscarChatDirectoExistente(usuarioId1: String, usuarioId2: String): String?
    suspend fun obtenerDestinatarioChatDirecto(chatId: String, usuarioIdActual: String): String?
    suspend fun obtenerTodosMisChatsDirectos(usuarioIdActual: String): Map<String, String>
    /**
     * [timestamp]: momento REAL de envío. Si se omite se usa la hora actual. Al drenar la Outbox
     * debe pasarse el original, o el mensaje aparecería "enviado" en la hora de la sincronización.
     */
    suspend fun enviarMensaje(
        chatId: String,
        remitenteId: String,
        contenido: String,
        idMensaje: String? = null,
        timestamp: Long? = null
    ): Mensaje

    fun observarMensajes(chatId: String): Flow<List<Mensaje>>

    /**
     * Último mensaje conocido SOLO desde el almacenamiento local (sin red). Sirve para pintar la
     * lista de chats activos sin abrir una suscripción completa por cada conversación.
     */
    suspend fun obtenerUltimoMensajeLocal(chatId: String, usuarioIdActual: String): Mensaje? = null

    /**
     * Obtiene el último mensaje para un conjunto de IDs de chat en una sola consulta por lotes.
     */
    suspend fun obtenerUltimosMensajes(chatIds: List<String>): Map<String, Mensaje> = emptyMap()

    /**
     * Elimina un mensaje propio en el historial local.
     */
    suspend fun eliminarMensaje(mensajeId: String): Boolean = false

    /**
     * Elimina una conversación completa con todos sus mensajes e historial.
     */
    suspend fun eliminarConversacion(chatId: String): Boolean = false
}
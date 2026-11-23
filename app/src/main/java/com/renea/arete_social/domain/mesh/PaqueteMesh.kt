package com.renea.arete_social.domain.mesh

import kotlinx.serialization.Serializable

/**
 * Representa un paquete de datos que viaja a través de la red mallada (Mesh) de AntiRed.
 *
 * @param id Identificador único del paquete (UUID) utilizado para deduplicación global y prevención de bucles.
 * @param chatId Sala o canal de conversación al que pertenece el mensaje.
 * @param remitenteId ID del usuario que originó el mensaje.
 * @param destinatarioId ID del usuario destino final en chats privados (null si es broadcast / canal grupal).
 * @param contenido Payload del mensaje (puede ser texto plano o un payload protegido E2EE con formato "ENC:v4:...").
 * @param ttl Time-To-Live: número máximo de saltos restantes permitidos antes de descartar el paquete.
 *            Valor 7 permite hasta 7 retransmisiones intermedias (misma política que bitchat),
 *            cubriendo redes mesh de densidad media-alta con alcance máximo.
 * @param saltosRealizados Contador acumulativo de retransmisiones intermedias efectuadas.
 * @param timestamp Marca de tiempo de emisión original en milisegundos.
 */
@Serializable
data class PaqueteMesh(
    val id: String,
    val chatId: String,
    val remitenteId: String,
    val destinatarioId: String? = null,
    val contenido: String,
    val ttl: Int = TTL_POR_DEFECTO,
    val saltosRealizados: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val firma: String? = null,
    val clavePublicaRemitente: String? = null
) {
    /**
     * Bytes que se firman. Cubre TODOS los campos de identidad y enrutamiento (id, chat, remitente,
     * destinatario, timestamp, contenido). La versión anterior firmaba solo `contenido`, así que un
     * atacante podía reutilizar una firma válida cambiando remitenteId/destinatarioId/chatId
     * (suplantación y re-dirección) o reinyectar el mismo contenido con otro id.
     * Cada campo va con prefijo de longitud para que no se puedan desplazar fronteras entre campos.
     * `ttl`, `saltosRealizados`, `firma` y `clavePublicaRemitente` quedan fuera a propósito:
     * los relays los modifican.
     */
    fun datosFirmados(): String = buildString {
        append("antired-mesh-v2|")
        for (campo in listOf(id, chatId, remitenteId, destinatarioId.orEmpty(), timestamp.toString(), contenido)) {
            append(campo.length).append(':').append(campo).append('|')
        }
    }

    companion object {
        const val TTL_POR_DEFECTO = 7

        /** Tope de contenido que viaja por BLE. Lo más grande (p.ej. notas de voz base64) va solo por Wi-Fi LAN / Wi-Fi Direct. */
        const val MAX_CONTENIDO_MESH_CHARS = 16_000

        /** Tolerancia a relojes adelantados entre dispositivos. */
        const val TOLERANCIA_FUTURO_MS = 10 * 60_000L
    }
}

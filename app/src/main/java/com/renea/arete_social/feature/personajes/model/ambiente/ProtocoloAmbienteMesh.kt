package com.renea.arete_social.feature.personajes.model.ambiente

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Protocolo de serialización de eventos de ambiente compartido para la red Mesh local.
 */
object ProtocoloAmbienteMesh {
    const val CHAT_ID = "ambiente-sync"
    private const val PREFIJO_EVENTO = "[AMBIENTE_EVT:v1:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun codificarEvento(evento: EventoAmbiente): String =
        "$PREFIJO_EVENTO${json.encodeToString(evento)}]"

    fun parsearEvento(contenido: String): EventoAmbiente? {
        if (!contenido.startsWith(PREFIJO_EVENTO) || !contenido.endsWith("]")) return null
        val payload = contenido.removePrefix(PREFIJO_EVENTO).removeSuffix("]")
        return runCatching { json.decodeFromString<EventoAmbiente>(payload) }.getOrNull()
    }
}

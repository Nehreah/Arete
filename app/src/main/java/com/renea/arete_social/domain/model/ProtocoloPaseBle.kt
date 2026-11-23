package com.renea.arete_social.domain.model

import kotlinx.serialization.json.Json

/**
 * Formato del pase sobre la malla BLE: `[PASE:v1:<json>]`, con TTL 1 (nunca se retransmite) y dirigido
 * al receptor. Cada paquete ya viaja firmado por el router (ECDSA) con la identidad del emisor, y el
 * pase lleva además su propia firma.
 */
object ProtocoloPaseBle {
    /** Canal propio: la mensajería lo ignora (no son mensajes) y lo atiende GestorPasesBle. */
    const val CHAT_ID = "pase-descubrimiento"

    /** Tope del JSON (el paquete mesh admite 16.000 caracteres en total). */
    const val MAX_JSON_CHARS = 14_000

    const val MAX_ID = 64
    const val MAX_NOMBRE = 120
    const val MAX_DESCRIPCION = 300
    const val MAX_CHAT_ID = 64

    private const val PREFIJO = "[PASE:v1:"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun codificar(pase: PaseDescubrimiento): String = "$PREFIJO${json.encodeToString(PaseDescubrimiento.serializer(), pase)}]"

    /** El pase que contiene [contenido], o null si no lo es o está mal formado. */
    fun decodificar(contenido: String): PaseDescubrimiento? {
        if (!contenido.startsWith(PREFIJO) || !contenido.endsWith("]")) return null
        val cuerpo = contenido.removePrefix(PREFIJO).removeSuffix("]")
        if (cuerpo.length > MAX_JSON_CHARS) return null
        return runCatching { json.decodeFromString(PaseDescubrimiento.serializer(), cuerpo) }.getOrNull()
    }
}

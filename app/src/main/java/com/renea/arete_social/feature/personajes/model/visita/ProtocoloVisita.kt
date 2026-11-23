package com.renea.arete_social.feature.personajes.model.visita

import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Petición enviada por un usuario visitante para explorar la casa de un amigo.
 */
@Serializable
data class VisitaSolicitud(
    val visitaId: String,
    val remitenteOwnerId: String,
    val remitentePersonajeId: String,
    val remitenteNombre: String,
    val timestamp: Long,
    val nonce: String,
    val nota: String? = null
)

/**
 * Respuesta del anfitrión que concede el acceso e incluye la instantánea de su casa firmada.
 */
@Serializable
data class VisitaResultado(
    val visitaId: String,
    val anfitrionOwnerId: String,
    val anfitrionPersonajeId: String,
    val anfitrionNombre: String,
    val casaSnapshot: CasaSnapshot,
    val timestamp: Long,
    val firmaAnfitrion: String? = null
)

/**
 * Recibo criptográfico que certifica que la visita e interacción ocurrieron de mutuo acuerdo.
 * Es indispensable para que ambos participantes puedan registrar el recuerdo y ganar confianza.
 */
@Serializable
data class ReciboVisita(
    val visitaId: String,
    val visitanteOwnerId: String,
    val anfitrionOwnerId: String,
    val accionRealizada: AccionObjeto,
    val objetoCatalogoId: String? = null,
    val notaVisitante: String? = null,
    val timestamp: Long,
    val firmaVisitante: String? = null
) {
    /**
     * Cadena canónica para sellado con firma digital ECDSA del visitante.
     */
    fun datosFirmados(): String = buildString {
        append("antired-recibo-visita-v1|")
        append(visitaId).append('|')
        append(visitanteOwnerId).append('|')
        append(anfitrionOwnerId).append('|')
        append(accionRealizada.name).append('|')
        append(objetoCatalogoId.orEmpty()).append('|')
        append(notaVisitante.orEmpty()).append('|')
        append(timestamp)
    }
}

/**
 * Protocolo de serialización segura y enmarcado de paquetes de visita sobre la red mesh.
 */
object ProtocoloVisita {
    const val CHAT_ID = "visita-sync"

    private const val PREFIJO_SOLICITUD = "[VISITA_REQ:v1:"
    private const val PREFIJO_RESULTADO = "[VISITA_RES:v1:"
    private const val PREFIJO_RECIBO = "[VISITA_RECIBO:v1:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun codificarSolicitud(s: VisitaSolicitud): String =
        "$PREFIJO_SOLICITUD${json.encodeToString(s)}]"

    fun parsearSolicitud(contenido: String): VisitaSolicitud? {
        if (!contenido.startsWith(PREFIJO_SOLICITUD) || !contenido.endsWith("]")) return null
        val payload = contenido.removePrefix(PREFIJO_SOLICITUD).removeSuffix("]")
        return runCatching { json.decodeFromString<VisitaSolicitud>(payload) }.getOrNull()
    }

    fun codificarResultado(r: VisitaResultado): String =
        "$PREFIJO_RESULTADO${json.encodeToString(r)}]"

    fun parsearResultado(contenido: String): VisitaResultado? {
        if (!contenido.startsWith(PREFIJO_RESULTADO) || !contenido.endsWith("]")) return null
        val payload = contenido.removePrefix(PREFIJO_RESULTADO).removeSuffix("]")
        return runCatching { json.decodeFromString<VisitaResultado>(payload) }.getOrNull()
    }

    fun codificarRecibo(r: ReciboVisita): String =
        "$PREFIJO_RECIBO${json.encodeToString(r)}]"

    fun parsearRecibo(contenido: String): ReciboVisita? {
        if (!contenido.startsWith(PREFIJO_RECIBO) || !contenido.endsWith("]")) return null
        val payload = contenido.removePrefix(PREFIJO_RECIBO).removeSuffix("]")
        return runCatching { json.decodeFromString<ReciboVisita>(payload) }.getOrNull()
    }
}

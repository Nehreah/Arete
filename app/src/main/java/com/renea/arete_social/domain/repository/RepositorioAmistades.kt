package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.SolicitudAmistad

/** Foto completa de las amistades de una persona, tal como las guarda el servidor. */
data class AmistadesServidor(
    val amigosIds: Set<String>,
    /** Solicitudes que me enviaron y aún no respondí. */
    val solicitudesRecibidas: List<SolicitudAmistad>,
    /** Personas a las que yo les envié solicitud y siguen sin responder. */
    val solicitudesEnviadasIds: Set<String>
)

/**
 * Las amistades viven en el servidor (tabla "amistades"), no en el teléfono: así las ve
 * cualquier dispositivo y el servidor puede decidir quién ve qué (perfil privado, cartas).
 */
interface RepositorioAmistades {
    suspend fun obtenerTodas(miId: String): AmistadesServidor

    /**
     * Envía una solicitud a [otroId]. Si esa persona ya me había enviado una, la acepta.
     * Devuelve true cuando quedaron como amistades, false cuando la solicitud quedó pendiente.
     */
    suspend fun solicitar(miId: String, otroId: String): Boolean

    /** Acepta la solicitud que [otroId] me envió. */
    suspend fun aceptar(miId: String, otroId: String)

    /** Cancela una solicitud enviada, rechaza una recibida o termina una amistad. */
    suspend fun eliminar(miId: String, otroId: String)
}

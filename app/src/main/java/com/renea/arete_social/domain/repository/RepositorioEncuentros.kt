package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.EncuentroRemoto
import com.renea.arete_social.domain.model.EstadoEncuentro
import com.renea.arete_social.domain.model.Ubicacion

/**
 * Encuentros entre amistades. Todo el flujo vive en el servidor para que el consentimiento
 * lo imponga la base de datos y no la app: la ubicación de la otra persona solo se puede leer
 * mientras el encuentro esté ACTIVO, es decir, después de que esa persona lo aceptó.
 */
interface RepositorioEncuentros {
    /** Pide un encuentro a una amistad y devuelve el id. Lanza si no son amistades. */
    suspend fun solicitar(amigoId: String): String

    /** Responde una invitación recibida. Devuelve el estado resultante (ACTIVO, RECHAZADO o EXPIRADO). */
    suspend fun responder(encuentroId: String, acepta: Boolean): EstadoEncuentro

    /** Termina un encuentro activo o cancela una solicitud sin respuesta. Es idempotente. */
    suspend fun terminar(encuentroId: String)

    /** Publica mi posición dentro de un encuentro ACTIVO. Lanza si ya no está activo. */
    suspend fun publicarUbicacion(encuentroId: String, ubicacion: Ubicacion)

    /** Mis encuentros PENDIENTE o ACTIVO (como solicitante o como invitado/a), incluidos los ya vencidos. */
    suspend fun obtenerAbiertos(): List<EncuentroRemoto>

    suspend fun obtener(encuentroId: String): EncuentroRemoto?

    /** Última posición que [usuarioId] publicó en el encuentro; null si no ha publicado o el encuentro no está activo. */
    suspend fun ubicacionDe(encuentroId: String, usuarioId: String): Ubicacion?
}

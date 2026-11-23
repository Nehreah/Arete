package com.renea.arete_social.data.event

import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.entity.EventEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.util.UUID
import com.renea.arete_social.domain.util.toHex

/**
 * Modelo de dominio para un evento local P2P.
 */
data class LocalEvent(
    val id: String,
    val organizerIkHex: String,
    val title: String,
    val description: String,
    val dateTimeMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "",
    val privacy: String = "COMMUNITY",
    val communityIdHex: String? = null,
    val isCancelled: Boolean = false,
    val fotoUrl: String? = null,
    val fotoHash: String? = null
)

/**
 * Repositorio de eventos locales P2P según la sección 11 de la auditoría v3.
 * Gestiona el ciclo de vida local y la persistencia en Room para eventos descentralizados.
 */
class EventRepositoryLocal(
    private val database: AppLocalDatabase,
    private val identityManager: IdentityManager
) {
    private val eventDao = database.eventDao()

    /**
     * Observa eventos próximos que no han sido cancelados.
     */
    fun observeUpcomingEvents(fromTimeMs: Long = System.currentTimeMillis()): Flow<List<EventEntity>> {
        return eventDao.observeUpcomingEvents(fromTimeMs)
    }

    /**
     * Obtiene un evento por su identificador.
     */
    suspend fun getEvent(id: String): LocalEvent? {
        val entity = eventDao.getEvent(id) ?: return null
        return entityToDomain(entity)
    }

    /**
     * Obtiene los eventos asociados a una comunidad local.
     */
    suspend fun getEventsForCommunity(communityIdHex: String): List<LocalEvent> {
        return eventDao.getEventsForCommunity(communityIdHex).map { entityToDomain(it) }
    }

    /**
     * Crea un evento local firmado por la IK del organizador.
     */
    suspend fun createEvent(
        title: String,
        description: String,
        dateTimeMs: Long,
        latitude: Double? = null,
        longitude: Double? = null,
        locationName: String = "",
        privacy: String = "COMMUNITY",
        communityIdHex: String? = null,
        fotoUrl: String? = null,
        fotoHash: String? = null
    ): LocalEvent {
        if (!identityManager.isLoaded) {
            identityManager.generateAndLoad()
        }

        val organizerIkBytes = identityManager.ikPublicBytes
        val organizerIkHex = organizerIkBytes.toHex()

        // ID determinista = SHA-256(organizerIk || title || dateTimeMs)
        val seed = "$organizerIkHex:$title:$dateTimeMs".toByteArray(Charsets.UTF_8)
        val eventId = MessageDigest.getInstance("SHA-256")
            .digest(seed)
            .toHex()

        val entity = EventEntity(
            id = eventId,
            organizerIkHex = organizerIkHex,
            title = title,
            description = description,
            dateTimeMs = dateTimeMs,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            privacy = privacy,
            communityIdHex = communityIdHex,
            isCancelled = false,
            fotoUrl = fotoUrl,
            fotoHash = fotoHash
        )

        eventDao.upsertEvent(entity)
        return entityToDomain(entity)
    }

    /**
     * Cancela un evento (solo si el solicitante es el organizador).
     */
    suspend fun cancelEvent(id: String, requesterIkHex: String): Boolean {
        val event = eventDao.getEvent(id) ?: return false
        if (event.organizerIkHex != requesterIkHex) {
            return false // No tiene autoridad para cancelar el evento
        }
        eventDao.cancelEvent(id)
        return true
    }

    private fun entityToDomain(entity: EventEntity): LocalEvent {
        return LocalEvent(
            id = entity.id,
            organizerIkHex = entity.organizerIkHex,
            title = entity.title,
            description = entity.description,
            dateTimeMs = entity.dateTimeMs,
            latitude = entity.latitude,
            longitude = entity.longitude,
            locationName = entity.locationName,
            privacy = entity.privacy,
            communityIdHex = entity.communityIdHex,
            isCancelled = entity.isCancelled,
            fotoUrl = entity.fotoUrl,
            fotoHash = entity.fotoHash
        )
    }
}

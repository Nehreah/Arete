package com.renea.arete_social.data.mesh

import android.annotation.SuppressLint
import android.util.Base64
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.entity.EventEntity
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.FeatureFlags
import com.renea.arete_social.domain.model.ProtocoloCanalesMesh
import com.renea.arete_social.domain.util.SyncLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de sincronización P2P local de eventos sobre la red mallada (Fase 6).
 *
 * Utiliza el canal dedicado "event-sync" y aplica reglas de visibilidad soberana en origen:
 * - PUBLIC: Compartido con cualquier nodo cercano.
 * - FRIENDS: Compartido únicamente si el nodo solicitante es amigo conocido.
 * - COMMUNITY: Compartido únicamente si el nodo solicitante es miembro activo de dicha comunidad.
 */
@SuppressLint("NewApi")
class GestorEventosBle(
    private val database: AppLocalDatabase,
    private val router: MessageRouter,
    private val miIdProveedor: () -> String?,
    private val esAmigo: (String) -> Boolean,
    private val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val TAG = "GestorEventosBle"
    private var job: Job? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ultimaRespuestaPorPeer = ConcurrentHashMap<String, Long>()
    /** Último `emittedAtMs` procesado por peer remitente. Permite detectar resúmenes frescos
     *  aunque el evento ya exista localmente con la misma versión. */
    private val ultimoEmittedAtMsPorPeer = ConcurrentHashMap<String, Long>()

    /**
     * Se invoca cada vez que un evento es guardado/actualizado en Room por sincronización P2P.
     * Conectar a [RepositorioEventosLocalFirst.notificarActualizado] para propagar el cambio
     * reactivamente a MapaViewModel y ComunidadDetalleViewModel sin necesitar un movimiento GPS.
     */
    @Volatile
    var onEventoActualizado: (() -> Unit)? = null

    @Serializable
    data class EventoSummary(
        val id: String,
        val version: Long,
        val updatedAtMs: Long,
        val privacy: String,
        val communityIdHex: String? = null,
        val isCancelled: Boolean = false,
        /** Timestamp del momento de emisión (no del evento). Siempre avanza con cada `emitirResumenEventos()`.
         *  Permite al receptor detectar actualizaciones aunque `version` y `updatedAtMs` no cambien. */
        val emittedAtMs: Long = 0L
    )

    @Serializable
    data class EventoPayload(
        val id: String,
        val organizerIkHex: String,
        val title: String,
        val description: String,
        val dateTimeMs: Long,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val locationName: String = "",
        val privacy: String = "PUBLIC",
        val communityIdHex: String? = null,
        val isCancelled: Boolean = false,
        val updatedAtMs: Long,
        val firmaOrganizador: String? = null,
        val version: Long = 1L,
        val fotoUrl: String? = null,
        val fotoHash: String? = null,
        val participantes: List<String> = emptyList()
    )

    companion object {
        const val PREFIX_SUMMARY = "EVT_SUMMARY:"
        const val PREFIX_REQ = "EVT_REQ:"
        const val PREFIX_RESP = "EVT_RESP:"
        const val PREFIX_RSVP = "EVT_RSVP:"
        private const val COOLDOWN_RESP_MS = 5_000L
    }

    fun iniciar() {
        if (job?.isActive == true) return
        job = scope.launch {
            router.todosLosPaquetesEntrantes()
                .filter { it.chatId == ProtocoloCanalesMesh.CANAL_EVENT_SYNC }
                .collect { paquete ->
                    runCatching { procesar(paquete) }
                        .onFailure { SyncLogger.w(TAG, "Error procesando paquete de eventos: ${it.message}") }
                }
        }
    }

    fun detener() {
        job?.cancel()
        job = null
    }

    /**
     * Emite el inventario de eventos locales disponibles para sincronizar con pares cercanos.
     */
    fun emitirResumenEventos(destinatarioDirecto: String? = null) {
        val miId = miIdProveedor() ?: return
        scope.launch {
            runCatching {
                val eventos = database.eventDao().getAllEventsIncludingCancelled()
                if (eventos.isEmpty()) return@launch

                val ahoraMs = System.currentTimeMillis()
                val summaries = eventos.map { e ->
                    EventoSummary(
                        id = e.id,
                        version = e.version,
                        updatedAtMs = e.updatedAtMs,
                        privacy = e.privacy,
                        communityIdHex = e.communityIdHex,
                        isCancelled = e.isCancelled,
                        emittedAtMs = ahoraMs
                    )
                }

                val payload = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EventoSummary.serializer()), summaries)
                val base64 = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                val paquete = PaqueteMesh(
                    id = "evt-sum-${UUID.randomUUID()}",
                    chatId = ProtocoloCanalesMesh.CANAL_EVENT_SYNC,
                    remitenteId = miId,
                    destinatarioId = destinatarioDirecto,
                    contenido = "$PREFIX_SUMMARY$base64",
                    ttl = 1
                )

                if (destinatarioDirecto != null) {
                    val directo = router.enviarADestino(destinatarioDirecto, paquete)
                    if (!directo) router.reenviar(paquete)
                } else {
                    router.reenviar(paquete)
                }
                SyncLogger.metrica("ENVIAR_RESUMEN", destinatarioDirecto ?: "ALL", ProtocoloCanalesMesh.CANAL_EVENT_SYNC, "${summaries.size} eventos")
            }
        }
    }

    private suspend fun procesar(paquete: PaqueteMesh) {
        val miId = miIdProveedor() ?: return
        if (paquete.remitenteId == miId) return

        when {
            paquete.contenido.startsWith(PREFIX_SUMMARY) -> {
                procesarResumen(paquete)
            }
            paquete.contenido.startsWith(PREFIX_REQ) -> {
                procesarPeticion(paquete)
            }
            paquete.contenido.startsWith(PREFIX_RESP) -> {
                procesarRespuesta(paquete)
            }
            paquete.contenido.startsWith(PREFIX_RSVP) -> {
                procesarRsvp(paquete)
            }
        }
    }

    private suspend fun procesarRsvp(paquete: PaqueteMesh) {
        val cuerpo = paquete.contenido.removePrefix(PREFIX_RSVP).trim()
        val partes = cuerpo.split(":")
        if (partes.size < 2) return
        val eventoId = partes[0]
        val asistenteId = partes[1]
        val evento = database.eventDao().getEvent(eventoId) ?: return

        // Solo el organizador fusiona y redistribuye la asistencia
        val miId = miIdProveedor() ?: return
        val esOrganizador = resolverIdentidad?.esMio(evento.organizerIkHex) == true ||
            evento.organizerIkHex.equals(miId, ignoreCase = true)
        if (esOrganizador) {
            // Persistir al asistente de forma idempotente
            val participante = com.renea.arete_social.data.local.room.entity.EventParticipantEntity(
                eventoId = eventoId,
                usuarioId = asistenteId,
                rol = "ASISTENTE",
                updatedAtMs = System.currentTimeMillis()
            )
            database.eventParticipantDao().upsertParticipant(participante)

            val nuevaVersion = evento.version + 1
            val actualizado = evento.copy(
                version = nuevaVersion,
                updatedAtMs = System.currentTimeMillis()
            )
            database.eventDao().upsertEvent(actualizado)
            SyncLogger.i(TAG, "RSVP confirmado de $asistenteId para evento $eventoId (nueva version: $nuevaVersion)")
            emitirResumenEventos()
        }
    }

    /** Emite confirmación de asistencia P2P firmada hacia el organizador del evento */
    fun emitirRsvp(eventoId: String, organizadorId: String) {
        val miId = miIdProveedor() ?: return
        scope.launch {
            val paquete = PaqueteMesh(
                id = "evt-rsvp-${UUID.randomUUID()}",
                chatId = ProtocoloCanalesMesh.CANAL_EVENT_SYNC,
                remitenteId = miId,
                destinatarioId = organizadorId,
                contenido = "$PREFIX_RSVP$eventoId:$miId",
                ttl = 1
            )
            val directo = router.enviarADestino(organizadorId, paquete)
            if (!directo) router.reenviar(paquete)
            SyncLogger.i(TAG, "RSVP emitido para evento $eventoId hacia organizador $organizadorId")
        }
    }

    private suspend fun procesarResumen(paquete: PaqueteMesh) {
        val rawBase64 = paquete.contenido.removePrefix(PREFIX_SUMMARY)
        val jsonStr = String(Base64.decode(rawBase64, Base64.NO_WRAP), Charsets.UTF_8)
        val summaries = runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(EventoSummary.serializer()), jsonStr)
        }.getOrNull() ?: return

        // emittedAtMs del batch: si es mayor que el último que procesamos de este peer,
        // el remitente tiene datos frescos que debemos revisar aunque ya tengamos el evento.
        val emittedAtMsBatch = summaries.firstOrNull()?.emittedAtMs ?: 0L
        val ultimoProcesado = ultimoEmittedAtMsPorPeer[paquete.remitenteId] ?: 0L
        val esBatchFresco = emittedAtMsBatch > ultimoProcesado

        for (summary in summaries) {
            val local = database.eventDao().getEvent(summary.id)
            val necesitaSolicitar = when {
                // Evento nuevo: no existe localmente
                local == null -> true
                // Versión o timestamp superior en el remitente
                summary.version > local.version || summary.updatedAtMs > local.updatedAtMs -> true
                // Mismo estado, pero el remitente emitió un batch más reciente que el último
                // que procesamos → puede que el evento no hubiera llegado la vez anterior.
                esBatchFresco && local.version == summary.version -> true
                else -> false
            }
            if (necesitaSolicitar) {
                solicitarEvento(summary.id, paquete.remitenteId)
            }
        }

        // Registrar el emittedAtMs de este batch para no volver a pedirlo en el próximo escaneo
        if (emittedAtMsBatch > ultimoProcesado) {
            ultimoEmittedAtMsPorPeer[paquete.remitenteId] = emittedAtMsBatch
        }
    }

    private suspend fun solicitarEvento(eventoId: String, remitentePeer: String) {
        val miId = miIdProveedor() ?: return
        val paquete = PaqueteMesh(
            id = "evt-req-${UUID.randomUUID()}",
            chatId = ProtocoloCanalesMesh.CANAL_EVENT_SYNC,
            remitenteId = miId,
            destinatarioId = remitentePeer,
            contenido = "$PREFIX_REQ$eventoId",
            ttl = 1
        )
        val directo = router.enviarADestino(remitentePeer, paquete)
        if (!directo) router.reenviar(paquete)
    }

    private suspend fun procesarPeticion(paquete: PaqueteMesh) {
        val eventoId = paquete.contenido.removePrefix(PREFIX_REQ).trim()
        val evento = database.eventDao().getEvent(eventoId) ?: return

        // Validación de visibilidad en origen — se normalizan los valores en español e inglés
        val permitido = when (evento.privacy.uppercase()) {
            "PUBLIC", "PUBLICO" -> true
            "FRIENDS", "AMIGOS" -> {
                // Intentar con el id tal cual, y también resolviendo el UUID → IK hex
                val ikRemitente = resolverIdentidad?.ikDe(paquete.remitenteId) ?: paquete.remitenteId
                esAmigo(paquete.remitenteId) || esAmigo(ikRemitente)
            }
            "COMMUNITY", "COMUNIDAD" -> {
                val commId = evento.communityIdHex
                if (commId.isNullOrBlank()) false
                else {
                    // paquete.remitenteId puede ser UUID de sesión; los miembros se guardan como IK hex.
                    // Resolver el UUID → IK hex antes de comparar; sin esto, la lista de miembros
                    // nunca coincidía y la petición se rechazaba silenciosamente.
                    val ikRemitente = resolverIdentidad?.ikDe(paquete.remitenteId) ?: paquete.remitenteId
                    val miembros = database.communityDao().getActiveMembers(commId)
                    miembros.any { member ->
                        member.authorIkHex == paquete.remitenteId ||
                        member.authorIkHex == ikRemitente ||
                        resolverIdentidad?.usuarioDe(member.authorIkHex) == paquete.remitenteId
                    }
                }
            }
            else -> false
        }

        if (!permitido) {
            SyncLogger.d(TAG, "Petición de evento $eventoId rechazada para ${paquete.remitenteId} por política de privacidad")
            return
        }

        val miId = miIdProveedor() ?: return
        val participantesActuales = database.eventParticipantDao().getParticipants(evento.id).map { it.usuarioId }
        val payload = EventoPayload(
            id = evento.id,
            organizerIkHex = evento.organizerIkHex,
            title = evento.title,
            description = evento.description,
            dateTimeMs = evento.dateTimeMs,
            latitude = evento.latitude,
            longitude = evento.longitude,
            locationName = evento.locationName,
            privacy = evento.privacy,
            communityIdHex = evento.communityIdHex,
            isCancelled = evento.isCancelled,
            updatedAtMs = evento.updatedAtMs,
            firmaOrganizador = evento.firmaOrganizador,
            version = evento.version,
            fotoUrl = evento.fotoUrl,
            fotoHash = evento.fotoHash,
            participantes = participantesActuales
        )

        val jsonStr = json.encodeToString(EventoPayload.serializer(), payload)
        val base64 = Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val resp = PaqueteMesh(
            id = "evt-resp-${UUID.randomUUID()}",
            chatId = ProtocoloCanalesMesh.CANAL_EVENT_SYNC,
            remitenteId = miId,
            destinatarioId = paquete.remitenteId,
            contenido = "$PREFIX_RESP$base64",
            ttl = 1
        )
        val directo = router.enviarADestino(paquete.remitenteId, resp)
        if (!directo) router.reenviar(resp)
    }

    private suspend fun procesarRespuesta(paquete: PaqueteMesh) {
        val base64 = paquete.contenido.removePrefix(PREFIX_RESP)
        val jsonStr = String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8)
        val payload = runCatching {
            json.decodeFromString(EventoPayload.serializer(), jsonStr)
        }.getOrNull() ?: return

        val local = database.eventDao().getEvent(payload.id)
        // Blindaje contra secuestro/falsificación de eventos: Si el evento ya existe, el organizador
        // DEBE ser idéntico al registrado originalmente. Nadie más puede actualizar o cancelar un evento ajeno.
        if (local != null && !local.organizerIkHex.equals(payload.organizerIkHex, ignoreCase = true)) {
            SyncLogger.w(TAG, "Rechazada actualización de evento ${payload.id}: organizador no coincide (${payload.organizerIkHex} != ${local.organizerIkHex})")
            return
        }

        if (local == null || payload.version > local.version || payload.updatedAtMs > local.updatedAtMs) {
            val entity = EventEntity(
                id = payload.id,
                organizerIkHex = payload.organizerIkHex,
                title = payload.title,
                description = payload.description,
                dateTimeMs = payload.dateTimeMs,
                latitude = payload.latitude,
                longitude = payload.longitude,
                locationName = payload.locationName,
                privacy = payload.privacy,
                communityIdHex = payload.communityIdHex,
                isCancelled = payload.isCancelled,
                updatedAtMs = payload.updatedAtMs,
                firmaOrganizador = payload.firmaOrganizador,
                version = payload.version,
                fotoUrl = payload.fotoUrl,
                fotoHash = payload.fotoHash
            )
            database.eventDao().upsertEvent(entity)

            // Sincronizar participantes si el organizador los envió
            if (payload.participantes.isNotEmpty()) {
                val existentes = database.eventParticipantDao().getParticipants(payload.id)
                // Eliminar participantes retirados/expulsados
                existentes.forEach { p ->
                    if (!payload.participantes.contains(p.usuarioId)) {
                        database.eventParticipantDao().removeParticipant(payload.id, p.usuarioId)
                    }
                }
                // Añadir participantes activos
                payload.participantes.forEach { uid ->
                    if (existentes.none { it.usuarioId == uid }) {
                        database.eventParticipantDao().upsertParticipant(
                            com.renea.arete_social.data.local.room.entity.EventParticipantEntity(
                                eventoId = payload.id,
                                usuarioId = uid,
                                rol = "ASISTENTE",
                                updatedAtMs = payload.updatedAtMs
                            )
                        )
                    }
                }
            }

            SyncLogger.i(TAG, "Evento ${payload.id} ('${payload.title}') guardado/actualizado vía P2P (cancelado=${payload.isCancelled})")
            // Disparar callback para que el repositorio emita eventosActualizados →
            // MapaViewModel y ComunidadDetalleViewModel se refrescan sin necesitar un
            // cambio de posición GPS.
            onEventoActualizado?.invoke()
        }
    }
}

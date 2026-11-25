package com.renea.arete_social.data.event

import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.room.entity.EventEntity
import com.renea.arete_social.data.local.room.entity.EventParticipantEntity
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.ParticipanteEvento
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.model.ZonaRegional
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.domain.repository.ResolverIdentidad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Implementación Local-First de [RepositorioEventos] (Fase 6 y corrección de auditoría).
 * Combina la persistencia soberana en SQLite/Room con el gestor BLE de eventos,
 * soporte de fotos, roles locales y resolución de identidad.
 */
class RepositorioEventosLocalFirst(
    private val localRepo: EventRepositoryLocal,
    private val database: AppLocalDatabase,
    private val remoteRepo: RepositorioEventos? = null,
    private val gestorEventosBleProveedor: (() -> com.renea.arete_social.data.mesh.GestorEventosBle?)? = null,
    private val resolverIdentidad: ResolverIdentidad? = null,
    private val almacenMedia: AlmacenMediaPerfil? = null,
    private val repositorioUsuarios: RepositorioUsuarios? = null
) : RepositorioEventos {

    private val eventDao = database.eventDao()
    private val participantDao = database.eventParticipantDao()

    // Señal reactiva: se emite cada vez que un evento cambia localmente (crea/actualiza/elimina),
    // incluidos los que llegan vía mesh P2P. MapaViewModel y ComunidadDetalleViewModel se suscriben
    // para refrescar la lista sin necesitar un cambio de posición GPS.
    private val _eventosActualizados = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val eventosActualizados: Flow<Unit> = _eventosActualizados.asSharedFlow()

    override fun notificarActualizado() {
        _eventosActualizados.tryEmit(Unit)
    }

    override suspend fun crear(
        nombre: String,
        descripcion: String,
        fecha: Long,
        lugarId: String?,
        creadorId: String,
        comunidadId: String?,
        ubicacion: Ubicacion?,
        visibilidad: VisibilidadEvento,
        fotoUrl: String?
    ): Evento {
        val visibilidadStr = visibilidad.name
        val localEvent = localRepo.createEvent(
            title = nombre,
            description = descripcion,
            dateTimeMs = fecha,
            latitude = ubicacion?.latitud,
            longitude = ubicacion?.longitud,
            locationName = "",
            privacy = visibilidadStr,
            communityIdHex = comunidadId,
            fotoUrl = fotoUrl
        )

        // Registrar inmediatamente al creador como participante ADMIN
        participantDao.upsertParticipant(
            EventParticipantEntity(
                eventoId = localEvent.id,
                usuarioId = creadorId,
                rol = "ADMIN",
                updatedAtMs = System.currentTimeMillis()
            )
        )

        val eventoLocal = localToDomain(localEvent, creadorId, fotoUrl)

        // Intento remoto secundario en segundo plano si está disponible
        remoteRepo?.let { remote ->
            runCatching {
                withTimeoutOrNull(2000L) {
                    remote.crear(nombre, descripcion, fecha, lugarId, creadorId, comunidadId, ubicacion, visibilidad, fotoUrl)
                }
            }
        }

        // Anunciar evento al mesh
        runCatching {
            gestorEventosBleProveedor?.invoke()?.emitirResumenEventos()
        }

        // Señal reactiva para MapaViewModel y ComunidadDetalleViewModel
        notificarActualizado()

        return eventoLocal
    }

    override suspend fun obtenerPorId(id: String): Evento? {
        val local = localRepo.getEvent(id)
        if (local != null) {
            val creadorResuelto = resolverIdentidad?.usuarioDe(local.organizerIkHex) ?: local.organizerIkHex
            return localToDomain(local, creadorId = creadorResuelto, fotoUrl = local.fotoUrl)
        }
        return remoteRepo?.let { remote ->
            runCatching { withTimeoutOrNull(1500L) { remote.obtenerPorId(id) } }.getOrNull()
        }
    }

    override fun observarEvento(id: String): Flow<Evento?> = flow {
        // Emitir reactivamente desde Room
        emitAll(eventDao.observeEvent(id).map { entity ->
            if (entity != null) entityToDomain(entity) else null
        })
    }

    override suspend fun unirse(eventoId: String, usuarioId: String) {
        val evento = localRepo.getEvent(eventoId)
        // Registrar participación local de inmediato
        participantDao.upsertParticipant(
            EventParticipantEntity(
                eventoId = eventoId,
                usuarioId = usuarioId,
                rol = "ASISTENTE",
                updatedAtMs = System.currentTimeMillis()
            )
        )

        if (evento != null) {
            val organizadorSesion = resolverIdentidad?.usuarioDe(evento.organizerIkHex) ?: evento.organizerIkHex
            gestorEventosBleProveedor?.invoke()?.emitirRsvp(eventoId, organizadorSesion)
        }
    }

    override suspend fun salir(eventoId: String, usuarioId: String) {
        participantDao.removeParticipant(eventoId, usuarioId)
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000L) { it.salir(eventoId, usuarioId) } } }
    }

    override suspend fun expulsarParticipante(eventoId: String, usuarioId: String) {
        participantDao.removeParticipant(eventoId, usuarioId)
        val entity = eventDao.getEvent(eventoId)
        if (entity != null) {
            val updated = entity.copy(
                version = entity.version + 1,
                updatedAtMs = System.currentTimeMillis()
            )
            eventDao.upsertEvent(updated)
            runCatching { gestorEventosBleProveedor?.invoke()?.emitirResumenEventos() }
            notificarActualizado()
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000L) { it.expulsarParticipante(eventoId, usuarioId) } } }
    }

    override suspend fun actualizar(
        id: String,
        nombre: String,
        descripcion: String,
        fecha: Long,
        ubicacion: Ubicacion?,
        visibilidad: VisibilidadEvento,
        fotoUrl: String?
    ) {
        val entity = eventDao.getEvent(id)
        if (entity != null) {
            val updated = entity.copy(
                title = nombre,
                description = descripcion,
                dateTimeMs = fecha,
                latitude = ubicacion?.latitud ?: entity.latitude,
                longitude = ubicacion?.longitud ?: entity.longitude,
                privacy = visibilidad.name,
                fotoUrl = if (fotoUrl.isNullOrBlank()) entity.fotoUrl else fotoUrl,
                updatedAtMs = System.currentTimeMillis(),
                version = entity.version + 1
            )
            eventDao.upsertEvent(updated)
            runCatching { gestorEventosBleProveedor?.invoke()?.emitirResumenEventos() }
            notificarActualizado()
            return
        }
        remoteRepo?.actualizar(id, nombre, descripcion, fecha, ubicacion, visibilidad, fotoUrl)
    }

    override suspend fun eliminar(eventoId: String) {
        val entity = eventDao.getEvent(eventoId)
        if (entity != null) {
            eventDao.cancelEvent(eventoId, updatedAtMs = System.currentTimeMillis())
            runCatching { gestorEventosBleProveedor?.invoke()?.emitirResumenEventos() }
            notificarActualizado()
            return
        }
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000L) { it.eliminar(eventoId) } } }
    }

    override suspend fun asignarRol(eventoId: String, usuarioId: String, rol: String) {
        participantDao.upsertParticipant(
            EventParticipantEntity(
                eventoId = eventoId,
                usuarioId = usuarioId,
                rol = rol.uppercase(),
                updatedAtMs = System.currentTimeMillis()
            )
        )
        remoteRepo?.let { runCatching { withTimeoutOrNull(1000L) { it.asignarRol(eventoId, usuarioId, rol) } } }
    }

    override suspend fun listarParticipantesConRoles(eventoId: String): List<ParticipanteEvento> {
        val participantesLocales = participantDao.getParticipants(eventoId)
        if (participantesLocales.isNotEmpty()) {
            return participantesLocales.map { part ->
                val u = runCatching {
                    withTimeoutOrNull(500L) { repositorioUsuarios?.obtenerPorId(part.usuarioId) }
                }.getOrNull()
                ParticipanteEvento(
                    usuarioId = part.usuarioId,
                    nombre = u?.nombre ?: part.usuarioId.take(8),
                    fotoUrl = u?.foto,
                    rol = part.rol
                )
            }
        }
        val remotos = remoteRepo?.let { runCatching { withTimeoutOrNull(1000L) { it.listarParticipantesConRoles(eventoId) } }.getOrNull() }
        return remotos ?: emptyList()
    }

    override suspend fun subirPortada(eventoId: String, bytes: ByteArray, extension: String): String {
        if (almacenMedia != null && bytes.isNotEmpty()) {
            val guardado = almacenMedia.guardarImagen(bytes, esAvatar = false, extension = extension.ifBlank { "jpg" })
            val path = guardado.rutaArchivo ?: guardado.rutaMiniatura ?: ""
            if (path.isNotBlank()) {
                val entity = eventDao.getEvent(eventoId)
                if (entity != null) {
                    eventDao.upsertEvent(entity.copy(fotoUrl = path, fotoHash = guardado.hashSha256, updatedAtMs = System.currentTimeMillis(), version = entity.version + 1))
                    runCatching { gestorEventosBleProveedor?.invoke()?.emitirResumenEventos() }
                }
                return path
            }
        }
        return remoteRepo?.let { runCatching { withTimeoutOrNull(2000L) { it.subirPortada(eventoId, bytes, extension) } }.getOrNull() } ?: ""
    }

    override suspend fun eventosCercanos(origen: Ubicacion, radioMetros: Double): List<Evento> {
        val locales = eventDao.getAllEvents().map { entityToDomain(it) }.filter { ev ->
            val dist = distanciaMetros(origen.latitud, origen.longitud, ev.ubicacion.latitud, ev.ubicacion.longitud)
            dist <= radioMetros
        }
        val remotos = remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.eventosCercanos(origen, radioMetros) } }.getOrNull()
        } ?: emptyList()

        return (locales + remotos).distinctBy { it.id }.sortedBy { it.fecha }
    }

    override suspend fun listarPorComunidad(comunidadId: String): List<Evento> {
        val locales = localRepo.getEventsForCommunity(comunidadId).map {
            val creador = resolverIdentidad?.usuarioDe(it.organizerIkHex) ?: it.organizerIkHex
            localToDomain(it, creadorId = creador, fotoUrl = it.fotoUrl)
        }
        val remotos = remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.listarPorComunidad(comunidadId) } }.getOrNull()
        } ?: emptyList()

        return (locales + remotos).distinctBy { it.id }
    }

    override suspend fun listarMisEventos(usuarioId: String): List<Evento> {
        val idsBusqueda = mutableListOf(usuarioId)
        resolverIdentidad?.let { res ->
            res.ikDe(usuarioId)?.let { idsBusqueda.add(it) }
            if (res.esMio(usuarioId)) {
                idsBusqueda.add(res.miIkHex())
            }
        }
        val locales = eventDao.getEventsByOrganizerIds(idsBusqueda.distinct()).map { entityToDomain(it) }
        val remotos = remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.listarMisEventos(usuarioId) } }.getOrNull()
        } ?: emptyList()

        return (locales + remotos).distinctBy { it.id }
    }

    override suspend fun eventosPublicosEnZona(zona: ZonaRegional, desdeMillis: Long, limite: Int): List<Evento> {
        val locales = eventDao.getAllEvents()
            .filter { it.dateTimeMs >= desdeMillis && it.privacy == VisibilidadEvento.PUBLICO.name }
            .map { entityToDomain(it) }
        val remotos = remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.eventosPublicosEnZona(zona, desdeMillis, limite) } }.getOrNull()
        } ?: emptyList()

        return (locales + remotos).distinctBy { it.id }.take(limite)
    }

    // ─── Helpers de conversión y cálculo geográfico ───

    private suspend fun localToDomain(local: LocalEvent, creadorId: String, fotoUrl: String?): Evento {
        val participantes = participantDao.getParticipants(local.id)
        val participantesIds = if (participantes.isEmpty()) listOf(creadorId) else participantes.map { it.usuarioId }
        val admins = participantes.filter { it.rol.equals("ADMIN", ignoreCase = true) }.map { it.usuarioId }

        return Evento(
            id = local.id,
            nombre = local.title,
            descripcion = local.description,
            fecha = local.dateTimeMs,
            ubicacion = Ubicacion(
                latitud = local.latitude ?: 0.0,
                longitud = local.longitude ?: 0.0
            ),
            lugarId = null,
            creadorId = creadorId,
            comunidadId = local.communityIdHex,
            participantesIds = participantesIds,
            chatId = local.id,
            visibilidad = normalizarVisibilidad(local.privacy),
            fotoUrl = fotoUrl ?: local.fotoUrl,
            adminsIds = admins
        )
    }

    private suspend fun entityToDomain(entity: EventEntity): Evento {
        val participantes = participantDao.getParticipants(entity.id)
        val creadorId = resolverIdentidad?.usuarioDe(entity.organizerIkHex) ?: entity.organizerIkHex
        val participantesIds = if (participantes.isEmpty()) listOf(creadorId) else participantes.map { it.usuarioId }
        val admins = participantes.filter { it.rol.equals("ADMIN", ignoreCase = true) }.map { it.usuarioId }

        return Evento(
            id = entity.id,
            nombre = entity.title,
            descripcion = entity.description,
            fecha = entity.dateTimeMs,
            ubicacion = Ubicacion(
                latitud = entity.latitude ?: 0.0,
                longitud = entity.longitude ?: 0.0
            ),
            lugarId = null,
            creadorId = creadorId,
            comunidadId = entity.communityIdHex,
            participantesIds = participantesIds,
            chatId = entity.id,
            visibilidad = normalizarVisibilidad(entity.privacy),
            fotoUrl = entity.fotoUrl,
            adminsIds = admins
        )
    }

    /** Normaliza valores de privacidad en inglés o español al enum canónico. */
    private fun normalizarVisibilidad(privacy: String): VisibilidadEvento =
        when (privacy.uppercase()) {
            "PUBLIC", "PUBLICO" -> VisibilidadEvento.PUBLICO
            "FRIENDS", "AMIGOS" -> VisibilidadEvento.AMIGOS
            "COMMUNITY", "COMUNIDAD" -> VisibilidadEvento.COMUNIDAD
            else -> runCatching { VisibilidadEvento.valueOf(privacy) }.getOrDefault(VisibilidadEvento.PUBLICO)
        }

    private fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

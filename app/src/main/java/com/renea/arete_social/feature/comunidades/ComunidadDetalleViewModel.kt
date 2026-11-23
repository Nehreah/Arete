package com.renea.arete_social.feature.comunidades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.RolComunidad
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.domain.repository.RepositorioComunidades
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ComunidadDetalleViewModel(
    private val repositorioComunidades: RepositorioComunidades,
    private val repositorioEventos: RepositorioEventos,
    private val repositorioUsuarios: RepositorioUsuarios,
    private val comunidadId: String,
    val usuarioId: String,
    private val syncManager: com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager? = null
) : ViewModel() {

    private val _estado = MutableStateFlow<ComunidadDetalleUiState>(ComunidadDetalleUiState.Cargando)
    val estado: StateFlow<ComunidadDetalleUiState> = _estado.asStateFlow()

    private var publicacionesMuro: List<PublicacionMuro> = emptyList()

    init {
        cargar()
        if (syncManager != null) {
            viewModelScope.launch {
                syncManager.eventosSincronizacion.collect { evento ->
                    when (evento) {
                        is com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager.SyncEvent.MiembrosActualizados -> {
                            if (evento.communityIdHex.equals(comunidadId, ignoreCase = true)) {
                                cargar()
                            }
                        }
                        is com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager.SyncEvent.OperacionesRecibidas -> {
                            if (evento.communityIdHex.equals(comunidadId, ignoreCase = true)) {
                                cargar()
                            }
                        }
                        is com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager.SyncEvent.PublicacionesActualizadas -> {
                            if (evento.communityIdHex.equals(comunidadId, ignoreCase = true)) {
                                cargar()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        viewModelScope.launch {
            repositorioEventos.eventosEliminados.collect { idEliminado ->
                val actual = _estado.value
                if (actual is ComunidadDetalleUiState.Listo) {
                    _estado.value = actual.copy(
                        eventos = actual.eventos.filterNot { it.id == idEliminado }
                    )
                }
            }
        }
        // Recargar eventos de esta comunidad cuando llega un evento por BLE/mesh,
        // sin esperar a que el usuario haga pull-to-refresh o navegue a otra pantalla.
        viewModelScope.launch {
            repositorioEventos.eventosActualizados.collect {
                val actual = _estado.value
                if (actual is ComunidadDetalleUiState.Listo) {
                    val eventosReales = runCatching {
                        withTimeoutOrNull(1000L) { repositorioEventos.listarPorComunidad(comunidadId) }
                    }.getOrNull() ?: return@collect
                    val listaEventos = eventosReales.map { ev ->
                        EventoComunidad(
                            id = ev.id,
                            titulo = ev.nombre,
                            fecha = formatearFecha(ev.fecha),
                            ubicacion = "📍 Lat: ${String.format(Locale.ROOT, "%.4f", ev.ubicacion.latitud)}, Lon: ${String.format(Locale.ROOT, "%.4f", ev.ubicacion.longitud)}",
                            descripcion = ev.descripcion,
                            asistentes = ev.participantesIds.size,
                            asistire = ev.participantesIds.contains(usuarioId),
                            latitud = ev.ubicacion.latitud,
                            longitud = ev.ubicacion.longitud,
                            eventoReal = ev
                        )
                    }
                    _estado.value = actual.copy(eventos = listaEventos)
                }
            }
        }
    }

    fun recargar() {
        cargar()
    }

    private fun cargar() {
        runCatching {
            syncManager?.emitirTodosLosMiembros(comunidadId)
            syncManager?.emitirTodasLasPublicaciones(comunidadId)
        }
        viewModelScope.launch {
            val comunidad = runCatching { repositorioComunidades.obtenerComunidad(comunidadId) }.getOrNull()
            if (comunidad == null) {
                _estado.value = ComunidadDetalleUiState.Error("No se encontró la comunidad solicitada")
                return@launch
            }

            val esMiembro = runCatching { repositorioComunidades.esMiembro(comunidadId, usuarioId) }.getOrDefault(false)

            // Detectar si el propio usuario ya envió solicitud pero está pendiente de aprobación
            val solicitudEnviada = if (!esMiembro) {
                runCatching {
                    repositorioComunidades.listarSolicitudesPendientes(comunidadId).contains(usuarioId)
                }.getOrDefault(false)
            } else false

            // Carga de roles de miembros desde el repositorio
            val rolesMap = runCatching { repositorioComunidades.listarMiembrosConRoles(comunidadId) }.getOrDefault(emptyMap())
            val miRol = RolComunidad.desdeCodigo(
                rolesMap[usuarioId] ?: if (usuarioId == comunidad.liderId) "LEADER" else if (esMiembro) "MEMBER" else ""
            ).codigo

            // Carga de miembros reales desde el repositorio
            val miembrosIds = runCatching { repositorioComunidades.listarMiembrosIds(comunidadId) }.getOrDefault(emptyList())
            val miembrosReales = miembrosIds.map { id ->
                val usuario = runCatching {
                    withTimeoutOrNull(500L) { repositorioUsuarios.obtenerPorId(id) }
                }.getOrNull()

                val rolObj = RolComunidad.desdeCodigo(rolesMap[id] ?: if (id == comunidad.liderId) "LEADER" else "MEMBER")
                val rolTexto = rolObj.texto
                val emoji = rolObj.emoji
                val nombreFallback = if (id == usuarioId) "Yo (${id.take(6)})" else if (id == comunidad.liderId) "Líder (${id.take(6)})" else "Miembro (${id.take(6)})"

                MiembroComunidad(
                    id = id,
                    nombre = usuario?.nombre?.ifBlank { null } ?: nombreFallback,
                    rol = rolTexto,
                    online = usuario?.estadoVisibilidad != Visibilidad.INVISIBLE,
                    avatarEmoji = emoji,
                    fotoUrl = usuario?.foto
                )
            }

            val listaMiembros = miembrosReales.ifEmpty {
                listOf(
                    MiembroComunidad(id = comunidad.liderId.ifBlank { "lider" }, nombre = "Comandante Zero", rol = "Líder", online = true, avatarEmoji = "🛡️")
                )
            }

            // Carga de publicaciones reales del muro desde el repositorio
            publicacionesMuro = runCatching { repositorioComunidades.listarPublicacionesMuro(comunidadId) }.getOrDefault(emptyList())

            // Carga de eventos comunitarios reales geolocalizados
            val eventosReales = runCatching {
                withTimeoutOrNull(1000L) { repositorioEventos.listarPorComunidad(comunidadId) }
            }.getOrNull() ?: emptyList()
            val listaEventos = eventosReales.map { ev ->
                EventoComunidad(
                    id = ev.id,
                    titulo = ev.nombre,
                    fecha = formatearFecha(ev.fecha),
                    ubicacion = "📍 Lat: ${String.format(Locale.ROOT, "%.4f", ev.ubicacion.latitud)}, Lon: ${String.format(Locale.ROOT, "%.4f", ev.ubicacion.longitud)}",
                    descripcion = ev.descripcion,
                    asistentes = ev.participantesIds.size,
                    asistire = ev.participantesIds.contains(usuarioId),
                    latitud = ev.ubicacion.latitud,
                    longitud = ev.ubicacion.longitud,
                    eventoReal = ev
                )
            }

            // Carga de archivos reales de la comunidad
            val listaArchivos = runCatching { repositorioComunidades.listarArchivosComunidad(comunidadId) }.getOrDefault(emptyList())

            // Carga de solicitudes pendientes (solo relevante para líder/admin de comunidad privada)
            val solicitudes = if (comunidad.tipo == "PRIVADA" && RolComunidad.desdeCodigo(miRol).puedeGestionar()) {
                runCatching { repositorioComunidades.listarSolicitudesPendientes(comunidadId) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val anterior = _estado.value
            val silenciada = if (anterior is ComunidadDetalleUiState.Listo) anterior.notificacionesSilenciadas else false

            _estado.value = ComunidadDetalleUiState.Listo(
                comunidad = comunidad,
                uniendose = false,
                esMiembro = esMiembro,
                miRol = miRol,
                notificacionesSilenciadas = silenciada,
                publicaciones = publicacionesMuro,
                miembros = listaMiembros,
                eventos = listaEventos,
                archivos = listaArchivos,
                solicitudesPendientes = solicitudes,
                solicitudEnviada = solicitudEnviada
            )

        }
    }

    fun subirArchivo(context: android.content.Context, uri: android.net.Uri) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return

        viewModelScope.launch {
            com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.inicializar(context)

            val miUsuario = runCatching { repositorioUsuarios.obtenerPorId(usuarioId) }.getOrNull()
            val miNombre = miUsuario?.nombre?.ifBlank { "Usuario" } ?: "Usuario"

            var bytes: ByteArray? = null
            var nombreArchivo = "Archivo_${System.currentTimeMillis()}"
            var tipoMime = context.contentResolver.getType(uri) ?: "*/*"

            runCatching {
                bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            nombreArchivo = it.getString(nameIndex)
                        }
                    }
                }
            }

            val datos = bytes
            if (datos != null && datos.isNotEmpty()) {
                repositorioComunidades.subirArchivoComunidad(
                    comunidadId = comunidadId,
                    autorId = usuarioId,
                    autorNombre = miNombre,
                    nombreArchivo = nombreArchivo,
                    bytes = datos,
                    tipoMime = tipoMime
                )
                cargar()
            }
        }
    }

    fun solicitarArchivosLan(destinoPeerId: String) {
        repositorioComunidades.solicitarArchivosLan(comunidadId, destinoPeerId)
    }

    fun descargarArchivoLan(archivoId: String, destinoPeerId: String) {
        repositorioComunidades.descargarArchivoLan(comunidadId, archivoId, destinoPeerId)
    }

    fun toggleMembresia() {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return
        val nuevoEstado = !actual.esMiembro
        _estado.value = actual.copy(uniendose = true)

        viewModelScope.launch {
            runCatching {
                if (nuevoEstado) {
                    repositorioComunidades.unirseAComunidad(comunidadId, usuarioId)
                } else {
                    repositorioComunidades.salirDeComunidad(comunidadId, usuarioId)
                }
            }.onSuccess {
                cargar()
            }.onFailure {
                val actualTrasFallo = _estado.value
                if (actualTrasFallo is ComunidadDetalleUiState.Listo) {
                    _estado.value = actualTrasFallo.copy(uniendose = false)
                }
            }
        }
    }

    fun publicar(
        context: android.content.Context,
        texto: String,
        imagenUri: String? = null,
        categoria: String = "Todos"
    ) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return
        val textoLimpio = texto.trim()
        if (textoLimpio.isEmpty() && imagenUri == null) return

        viewModelScope.launch {
            com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.inicializar(context)

            val miUsuario = runCatching { repositorioUsuarios.obtenerPorId(usuarioId) }.getOrNull()
            val miNombre = miUsuario?.nombre?.ifBlank { "Tú" } ?: "Tú"

            var bytes: ByteArray? = null
            var ext = "jpg"
            if (!imagenUri.isNullOrBlank()) {
                runCatching {
                    val uri = android.net.Uri.parse(imagenUri)
                    bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val mime = context.contentResolver.getType(uri)
                    if (mime?.contains("png") == true) ext = "png"
                }
            }

            repositorioComunidades.crearPublicacionMuro(
                comunidadId = comunidadId,
                autorId = usuarioId,
                autorNombre = miNombre,
                contenido = textoLimpio,
                imagenBytes = bytes,
                extension = ext,
                categoria = categoria
            )

            cargar()
        }
    }

    fun toggleLike(pubId: String) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return

        viewModelScope.launch {
            repositorioComunidades.toggleLikePublicacion(comunidadId, pubId, usuarioId)
            cargar()
        }
    }

    fun toggleAsistir(eventoId: String) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return

        val evento = actual.eventos.find { it.id == eventoId } ?: return
        val vaAAsistir = !evento.asistire

        val actualizados = actual.eventos.map { ev ->
            if (ev.id == eventoId) {
                val nuevosAsistentes = if (vaAAsistir) ev.asistentes + 1 else (ev.asistentes - 1).coerceAtLeast(0)
                ev.copy(asistire = vaAAsistir, asistentes = nuevosAsistentes)
            } else {
                ev
            }
        }
        _estado.value = actual.copy(eventos = actualizados)

        viewModelScope.launch {
            runCatching {
                if (vaAAsistir) {
                    repositorioEventos.unirse(eventoId, usuarioId)
                } else {
                    repositorioEventos.salir(eventoId, usuarioId)
                }
            }
        }
    }

    fun alternarNotificaciones(context: android.content.Context) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return
        val nuevoSilenciado = com.renea.arete_social.data.local.ajustes.AjustesNotificacionesComunidad.alternarSilenciado(context, comunidadId)
        _estado.value = actual.copy(notificacionesSilenciadas = nuevoSilenciado)
    }

    fun sincronizarNotificaciones(context: android.content.Context) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return
        val silenciada = com.renea.arete_social.data.local.ajustes.AjustesNotificacionesComunidad.estaSilenciada(context, comunidadId)
        if (actual.notificacionesSilenciadas != silenciada) {
            _estado.value = actual.copy(notificacionesSilenciadas = silenciada)
        }
    }

    fun actualizarComunidad(
        nombre: String,
        descripcion: String,
        tipo: String,
        fotoBytes: ByteArray? = null,
        extension: String = "jpg",
        onExito: () -> Unit = {}
    ) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return

        viewModelScope.launch {
            runCatching {
                var urlFoto = actual.comunidad.fotoUrl
                if (fotoBytes != null && fotoBytes.isNotEmpty()) {
                    urlFoto = repositorioComunidades.subirAvatarComunidad(comunidadId, fotoBytes, extension)
                }
                repositorioComunidades.actualizarComunidad(comunidadId, nombre, descripcion, tipo, urlFoto)
            }.onSuccess {
                cargar()
                onExito()
            }
        }
    }

    fun asignarRol(usuarioDestinoId: String, nuevoRol: String) {
        viewModelScope.launch {
            runCatching {
                repositorioComunidades.asignarRol(comunidadId, usuarioDestinoId, nuevoRol)
            }.onSuccess {
                cargar()
            }
        }
    }

    fun transferirLiderazgo(nuevoLiderId: String) {
        viewModelScope.launch {
            runCatching {
                repositorioComunidades.transferirLiderazgo(comunidadId, nuevoLiderId)
            }.onSuccess {
                cargar()
            }
        }
    }

    fun eliminarComunidad(onEliminada: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                repositorioComunidades.eliminarComunidad(comunidadId)
            }.onSuccess {
                onEliminada()
            }
        }
    }

    /**
     * Aprueba la solicitud de membresía de [solicitanteId].
     * Solo debe llamarse cuando el usuario actual es LIDER, OWNER o MODERADOR.
     * Tras aprobarse, recarga el estado (el solicitante desaparece de la bandeja y
     * aparece en la lista de miembros en cuanto el ledger se sincroniza).
     */
    fun aprobarSolicitud(solicitanteId: String) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return
        if (!actual.puedeGestionar) return

        viewModelScope.launch {
            runCatching {
                repositorioComunidades.aprobarMiembro(comunidadId, solicitanteId)
            }.onSuccess {
                cargar()
            }
        }
    }

    /**
     * Rechaza la solicitud de membresía de [solicitanteId].
     * Actualiza el estado del miembro a EXPELLED en Room sin emitir una operación de ledger
     * (el rechazo no necesita propagarse por mesh — simplemente dejamos de considerar la solicitud).
     */
    fun rechazarSolicitud(solicitanteId: String) {
        val actual = _estado.value
        if (actual !is ComunidadDetalleUiState.Listo) return
        if (!actual.puedeGestionar) return

        // Optimistic UI: eliminar de la lista localmente
        _estado.value = actual.copy(
            solicitudesPendientes = actual.solicitudesPendientes.filterNot { it == solicitanteId }
        )

        viewModelScope.launch {
            // Marcar al solicitante como EXPELLED en Room para evitar que reaparezca
            runCatching {
                repositorioComunidades.salirDeComunidad(comunidadId, solicitanteId)
            }
        }
    }


    private fun formatearFecha(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            sdf.format(Date(timestamp))
        } catch (_: Exception) {
            "Fecha programada"
        }
    }
}

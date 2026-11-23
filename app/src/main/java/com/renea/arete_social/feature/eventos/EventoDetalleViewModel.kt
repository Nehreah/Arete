package com.renea.arete_social.feature.eventos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.RolComunidad
import com.renea.arete_social.domain.repository.RepositorioEventos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventoDetalleViewModel(
    private val repositorioEventos: RepositorioEventos,
    private val eventoId: String,
    val usuarioId: String,
    private val repositorioComunidades: com.renea.arete_social.domain.repository.RepositorioComunidades? = null
) : ViewModel() {

    private val _estado = MutableStateFlow<EventoDetalleUiState>(EventoDetalleUiState.Cargando)
    val estado: StateFlow<EventoDetalleUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun recargar() {
        cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            val evento = repositorioEventos.obtenerPorId(eventoId)
            if (evento == null) {
                repositorioEventos.notificarEliminado(eventoId)
                _estado.value = EventoDetalleUiState.Error("No se encontró el evento")
                return@launch
            }

            val participantesConRoles = repositorioEventos.listarParticipantesConRoles(eventoId)
            val esCreador = evento.creadorId == usuarioId
            val esAdminEvento = evento.adminsIds.contains(usuarioId)
            var esAdminComunidad = false
            if (!evento.comunidadId.isNullOrBlank() && repositorioComunidades != null) {
                val rol = repositorioComunidades.obtenerRolMiembro(evento.comunidadId, usuarioId)
                esAdminComunidad = RolComunidad.desdeCodigo(rol).puedeGestionar()
            }
            val puedeGestionar = esCreador || esAdminEvento || esAdminComunidad

            _estado.value = EventoDetalleUiState.Listo(
                evento = evento,
                uniendose = false,
                participantesConRoles = participantesConRoles,
                puedeGestionar = puedeGestionar,
                esCreador = esCreador
            )
        }
    }

    fun unirse() {
        val actual = _estado.value
        if (actual !is EventoDetalleUiState.Listo) return
        _estado.value = actual.copy(uniendose = true)
        viewModelScope.launch {
            runCatching { repositorioEventos.unirse(eventoId, usuarioId) }
                .onSuccess { cargar() }
                .onFailure {
                    val trasFallo = _estado.value
                    if (trasFallo is EventoDetalleUiState.Listo) {
                        _estado.value = trasFallo.copy(uniendose = false)
                    }
                }
        }
    }

    fun salir() {
        val actual = _estado.value
        if (actual !is EventoDetalleUiState.Listo) return
        _estado.value = actual.copy(uniendose = true)
        viewModelScope.launch {
            runCatching { repositorioEventos.salir(eventoId, usuarioId) }
                .onSuccess { cargar() }
                .onFailure {
                    val trasFallo = _estado.value
                    if (trasFallo is EventoDetalleUiState.Listo) {
                        _estado.value = trasFallo.copy(uniendose = false)
                    }
                }
        }
    }

    fun actualizar(
        nombre: String,
        descripcion: String,
        fecha: Long,
        ubicacion: com.renea.arete_social.domain.model.Ubicacion?,
        visibilidad: com.renea.arete_social.domain.model.VisibilidadEvento,
        portadaBytes: ByteArray? = null,
        extension: String = "jpg",
        onExito: () -> Unit = {}
    ) {
        val actual = _estado.value
        if (actual !is EventoDetalleUiState.Listo) return

        viewModelScope.launch {
            runCatching {
                var urlPortada = actual.evento.fotoUrl
                if (portadaBytes != null && portadaBytes.isNotEmpty()) {
                    urlPortada = repositorioEventos.subirPortada(eventoId, portadaBytes, extension)
                }
                repositorioEventos.actualizar(
                    id = eventoId,
                    nombre = nombre,
                    descripcion = descripcion,
                    fecha = fecha,
                    ubicacion = ubicacion,
                    visibilidad = visibilidad,
                    fotoUrl = urlPortada
                )
            }.onSuccess {
                cargar()
                onExito()
            }
        }
    }

    fun asignarRol(usuarioDestinoId: String, nuevoRol: String) {
        viewModelScope.launch {
            runCatching {
                repositorioEventos.asignarRol(eventoId, usuarioDestinoId, nuevoRol)
            }.onSuccess {
                cargar()
            }
        }
    }

    fun eliminar(onEliminado: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                repositorioEventos.eliminar(eventoId)
            }.onSuccess {
                onEliminado()
            }
        }
    }
}

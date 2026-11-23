package com.renea.arete_social.feature.eventos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.repository.RepositorioEventos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * lugarId llega ya resuelto (desde LugarDetalleScreen — un evento "ocurre en" un
 * Lugar existente, a diferencia de un Lugar que se crea en cualquier punto del mapa).
 * comunidadId es opcional: si viene, el evento se crea desde esa comunidad y puede ser
 * público para todos o privado para sus miembros. Sin comunidad, aplica la privacidad personal.
 */
class CrearEventoViewModel(
    private val repositorioEventos: RepositorioEventos,
    private val lugarId: String? = null,
    private val creadorId: String,
    private val comunidadId: String? = null
) : ViewModel() {

    /** Indica que el evento se está creando desde una comunidad; puede ser público o privado. */
    val esComunal: Boolean = !comunidadId.isNullOrBlank()

    private val _estado = MutableStateFlow<CrearEventoUiState>(CrearEventoUiState.Editando)
    val estado: StateFlow<CrearEventoUiState> = _estado.asStateFlow()

    fun crear(
        nombre: String,
        descripcion: String,
        fechaDiaMillis: Long,
        hora: HoraEvento,
        ubicacion: com.renea.arete_social.domain.model.Ubicacion? = null,
        visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO,
        portadaBytes: ByteArray? = null,
        extension: String = "jpg"
    ) {
        if (nombre.isBlank()) {
            _estado.value = CrearEventoUiState.Error("Ponle un nombre al evento")
            return
        }
        _estado.value = CrearEventoUiState.Guardando
        viewModelScope.launch {
            val fecha = combinarFechaYHora(fechaDiaMillis, hora.horaDelDia)
            runCatching {
                val ev = repositorioEventos.crear(
                    nombre = nombre,
                    descripcion = descripcion,
                    fecha = fecha,
                    lugarId = lugarId,
                    creadorId = creadorId,
                    comunidadId = comunidadId,
                    ubicacion = ubicacion,
                    visibilidad = VisibilidadEvento.efectiva(comunidadId, visibilidad)
                )

                if (portadaBytes != null && portadaBytes.isNotEmpty()) {
                    val urlPortada = runCatching {
                        repositorioEventos.subirPortada(ev.id, portadaBytes, extension)
                    }.getOrNull()
                    if (urlPortada != null) {
                        runCatching {
                            repositorioEventos.actualizar(
                                id = ev.id,
                                nombre = ev.nombre,
                                descripcion = ev.descripcion,
                                fecha = ev.fecha,
                                ubicacion = ev.ubicacion,
                                visibilidad = ev.visibilidad,
                                fotoUrl = urlPortada
                            )
                        }
                    }
                }
                ev
            }
                .onSuccess { evento -> _estado.value = CrearEventoUiState.Creado(evento.id) }
                .onFailure { error ->
                    _estado.value = CrearEventoUiState.Error(error.message ?: "No se pudo crear el evento")
                }
        }
    }

    /**
     * Calcula la marca temporal UTC combinando el día del DatePicker (UTC) con la
     * hora en la zona horaria del dispositivo, garantizando que un evento a las
     * 4:00 PM local se conserve y visualice exactamente como 4:00 PM (§5B.3).
     */
    fun combinarFechaYHora(fechaDiaMillis: Long, horaDelDia: Int, minuto: Int = 0): Long {
        val fechaLocal = java.time.Instant.ofEpochMilli(fechaDiaMillis)
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
        val fechaHoraLocal = java.time.LocalDateTime.of(fechaLocal, java.time.LocalTime.of(horaDelDia, minuto))
        return fechaHoraLocal.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

package com.renea.arete_social.feature.libreria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.ArchivoPdf
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.TipoObra
import com.renea.arete_social.domain.repository.RepositorioLibreria
import com.renea.arete_social.domain.util.ResultadoObra
import com.renea.arete_social.domain.util.validarNuevaObra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Librería personal: cuentos y poemarios escritos y/o subidos como PDF. */
class LibreriaViewModel(
    private val repositorio: RepositorioLibreria,
    private val usuarioId: String,
    private val esInvitado: Boolean = false,
    // Copia en texto de mi librería para entregarla por Bluetooth (público a cualquiera cercano; privado solo a mis amistades).
    private val cachePerfil: com.renea.arete_social.data.local.perfil.CachePerfilLocal? = null
) : ViewModel() {

    private val _estado = MutableStateFlow(LibreriaUiState(disponible = !esInvitado))
    val estado: StateFlow<LibreriaUiState> = _estado.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        if (esInvitado) return
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true) }
            runCatching { repositorio.listar(usuarioId) }
                .onSuccess { obras ->
                    _estado.update { it.copy(cargando = false, obras = obras) }
                    guardarCopiaBluetooth()
                }
                .onFailure { _estado.update { it.copy(cargando = false, mensaje = "No se pudo cargar tu librería.") } }
        }
    }

    fun publicar(
        tipo: TipoObra,
        titulo: String,
        contenido: String,
        enlace: String,
        pdf: ArchivoPdf?,
        alTerminar: (error: String?, obra: ObraLibreria?) -> Unit
    ) {
        if (esInvitado) return alTerminar("Crea una cuenta para publicar obras.", null)
        val nueva = when (val r = validarNuevaObra(tipo, titulo, contenido, enlace, pdf)) {
            is ResultadoObra.Error -> return alTerminar(r.mensaje, null)
            is ResultadoObra.Correcta -> r.obra
        }
        viewModelScope.launch {
            _estado.update { it.copy(guardando = true) }
            runCatching { repositorio.crear(usuarioId, nueva) }
                .onSuccess { obra ->
                    _estado.update { it.copy(guardando = false, obras = listOf(obra) + it.obras) }
                    guardarCopiaBluetooth()
                    alTerminar(null, obra)
                }
                .onFailure {
                    _estado.update { it.copy(guardando = false) }
                    alTerminar("No se pudo guardar. Revisa tu conexión e inténtalo de nuevo.", null)
                }
        }
    }

    fun compartirEnMapa(obra: ObraLibreria, alTerminar: (error: String?) -> Unit = {}) {
        if (esInvitado) return alTerminar("Crea una cuenta para compartir en el mapa.")
        viewModelScope.launch {
            val resultado = runCatching { repositorio.compartirEnMapa(obra.id, usuarioId) }
                .getOrElse { com.renea.arete_social.domain.model.ResultadoCompartirMapa.Error("Error al conectar con el servidor.") }

            when (resultado) {
                is com.renea.arete_social.domain.model.ResultadoCompartirMapa.Exito -> {
                    val ahora = System.currentTimeMillis()
                    _estado.update { s ->
                        s.copy(
                            obras = s.obras.map { if (it.id == obra.id) it.copy(compartidaMapaEn = ahora) else it },
                            mensaje = "¡Tu Michi ahora está leyendo «${obra.titulo}» en el mapa!"
                        )
                    }
                    guardarCopiaBluetooth()
                    alTerminar(null)
                }
                is com.renea.arete_social.domain.model.ResultadoCompartirMapa.EnEspera -> {
                    val msg = "Tu Michi está leyendo otra obra. Podrás compartir otra en ${resultado.minutosRestantes} min."
                    _estado.update { it.copy(mensaje = msg) }
                    alTerminar(msg)
                }
                is com.renea.arete_social.domain.model.ResultadoCompartirMapa.Error -> {
                    _estado.update { it.copy(mensaje = resultado.mensaje) }
                    alTerminar(resultado.mensaje)
                }
            }
        }
    }

    fun eliminar(obra: ObraLibreria) {
        viewModelScope.launch {
            runCatching { repositorio.eliminar(obra) }
                .onSuccess {
                    _estado.update { s -> s.copy(obras = s.obras.filterNot { it.id == obra.id }) }
                    guardarCopiaBluetooth()
                }
                .onFailure { _estado.update { it.copy(mensaje = "No se pudo eliminar la obra.") } }
        }
    }

    fun abrirPdf(obra: ObraLibreria, alObtener: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repositorio.urlTemporalPdf(obra) }
                .onSuccess(alObtener)
                .onFailure { _estado.update { it.copy(mensaje = "No se pudo abrir el PDF.") } }
        }
    }

    private fun guardarCopiaBluetooth() {
        if (esInvitado) return
        runCatching { cachePerfil?.actualizarMisObras(usuarioId, _estado.value.obras) }
    }

    fun consumirMensaje() = _estado.update { it.copy(mensaje = null) }
}

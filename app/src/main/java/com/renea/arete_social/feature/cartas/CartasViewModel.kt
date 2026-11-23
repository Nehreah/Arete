package com.renea.arete_social.feature.cartas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.NuevaCarta
import com.renea.arete_social.domain.repository.RepositorioCartas
import com.renea.arete_social.domain.util.validarTextoCarta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bandeja de cartas entre amistades. Solo se puede escribir a quien [esAmigo] confirme.
 * [alTerminar] recibe null si todo salió bien o el mensaje de error para mostrarlo en el redactor.
 */
class CartasViewModel(
    private val repositorio: RepositorioCartas,
    private val usuarioId: String,
    private val esInvitado: Boolean = false,
    private val esAmigo: (String) -> Boolean = { true },
    private val cachePerfil: com.renea.arete_social.data.local.perfil.CachePerfilLocal? = null
) : ViewModel() {

    private val _estado = MutableStateFlow(CartasUiState(disponible = !esInvitado))
    val estado: StateFlow<CartasUiState> = _estado.asStateFlow()

    init {
        recargar()
        (repositorio as? com.renea.arete_social.data.local.cartas.RepositorioCartasLocalFirst)?.let { localCartas ->
            viewModelScope.launch {
                localCartas.cartasActualizadas.collect {
                    recargar()
                }
            }
        }
    }

    fun recargar() {
        if (esInvitado) return
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true) }
            runCatching { repositorio.recibidas(usuarioId) to repositorio.enviadas(usuarioId) }
                .onSuccess { (rec, env) ->
                    _estado.update { it.copy(cargando = false, recibidas = rec, enviadas = env) }
                }
                .onFailure {
                    _estado.update { it.copy(cargando = false, mensaje = "No se pudieron cargar las cartas.") }
                }
        }
    }

    fun enviar(
        remitenteNombre: String,
        destinatarioId: String,
        destinatarioNombre: String,
        texto: String,
        alTerminar: (error: String?) -> Unit
    ) {
        if (esInvitado) return alTerminar("Crea una cuenta para enviar cartas.")
        val destinatarioPrivado = cachePerfil?.recibido(destinatarioId)?.privado ?: true
        if (destinatarioPrivado && !esAmigo(destinatarioId)) {
            return alTerminar("Solo puedes escribirle a tus amistades si su perfil es privado.")
        }
        val (limpio, error) = validarTextoCarta(texto)
        if (limpio == null) return alTerminar(error)

        viewModelScope.launch {
            _estado.update { it.copy(enviando = true) }
            runCatching {
                repositorio.enviar(usuarioId, remitenteNombre, NuevaCarta(destinatarioId, destinatarioNombre, limpio))
            }.onSuccess { carta ->
                _estado.update {
                    it.copy(enviando = false, enviadas = listOf(carta) + it.enviadas, mensaje = "Carta enviada a $destinatarioNombre")
                }
                alTerminar(null)
            }.onFailure {
                _estado.update { it.copy(enviando = false) }
                alTerminar("No se pudo enviar la carta. Revisa tu conexión.")
            }
        }
    }

    /** Marca como leída (optimista) al abrir una carta recibida. */
    fun abrir(carta: Carta) {
        if (carta.destinatarioId != usuarioId || carta.leida) return
        _estado.update { s -> s.copy(recibidas = s.recibidas.map { if (it.id == carta.id) it.copy(leida = true) else it }) }
        viewModelScope.launch { runCatching { repositorio.marcarLeida(carta.id) } }
    }

    fun eliminar(carta: Carta) {
        viewModelScope.launch {
            runCatching { repositorio.eliminar(carta.id) }
                .onSuccess {
                    _estado.update { s ->
                        s.copy(
                            recibidas = s.recibidas.filterNot { it.id == carta.id },
                            enviadas = s.enviadas.filterNot { it.id == carta.id }
                        )
                    }
                }
                .onFailure { _estado.update { it.copy(mensaje = "No se pudo eliminar la carta.") } }
        }
    }

    fun consumirMensaje() = _estado.update { it.copy(mensaje = null) }
}

package com.renea.arete_social.feature.identidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.domain.util.toHex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de la pantalla de identidad. */
sealed class IdentidadUiState {
    /** No hay identidad cargada en memoria. */
    data object Vacio : IdentidadUiState()

    /** Operación en curso (generar / restaurar). */
    data object Cargando : IdentidadUiState()

    /**
     * Identidad cargada correctamente.
     * @param ikPublicHex Clave pública IK en hex (65 bytes, 130 hex chars: 04||X||Y).
     * @param palabras Frase mnemotécnica. Se expone UNA SOLA VEZ tras la creación;
     *                 en restauraciones es siempre [emptyList] para no re-mostrar la frase.
     */
    data class Cargada(val ikPublicHex: String, val palabras: List<String>) : IdentidadUiState()

    /** Error con mensaje descriptivo. */
    data class Error(val mensaje: String) : IdentidadUiState()
}

/**
 * ViewModel que media entre [IdentityManager] y la UI.
 *
 * Reglas de seguridad que debe respetar este ViewModel:
 * - **Nunca** almacena ni expone la clave privada IK.
 * - La frase mnemotécnica se expone en [IdentidadUiState.Cargada.palabras] únicamente
 *   en el instante de la creación; tras confirmación del usuario se borra de [_estado].
 * - Todas las operaciones costosas se ejecutan en [Dispatchers.Default].
 */
class IdentityViewModel(
    private val identityManager: IdentityManager,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _estado = MutableStateFlow<IdentidadUiState>(
        if (identityManager.isLoaded) {
            IdentidadUiState.Cargada(
                ikPublicHex = identityManager.ikPublicBytes.toHex(),
                palabras = emptyList()
            )
        } else {
            IdentidadUiState.Vacio
        }
    )
    val estado: StateFlow<IdentidadUiState> = _estado.asStateFlow()

    // ─── Acciones públicas ────────────────────────────────────────────────────

    /**
     * Genera una nueva identidad con 12 palabras BIP39 y la carga en memoria.
     * El estado resultante incluye las palabras para que el usuario las anote.
     */
    fun generarNuevaIdentidad() {
        if (_estado.value is IdentidadUiState.Cargando) return
        _estado.value = IdentidadUiState.Cargando
        viewModelScope.launch {
            runCatching {
                withContext(defaultDispatcher) {
                    identityManager.generateAndLoad()
                }
            }.onSuccess { palabras ->
                val ikHex = withContext(defaultDispatcher) {
                    identityManager.ikPublicBytes.toHex()
                }
                _estado.value = IdentidadUiState.Cargada(ikHex, palabras)
            }.onFailure { e ->
                _estado.value = IdentidadUiState.Error("Error generando identidad: ${e.message}")
            }
        }
    }

    /**
     * Restaura la identidad a partir de las [palabras] proporcionadas.
     * Si la frase es inválida o no coincide con la IK esperada, se emite [IdentidadUiState.Error].
     */
    fun restaurarDesde(palabras: List<String>) {
        if (palabras.size != 12) {
            _estado.value = IdentidadUiState.Error("La frase debe tener exactamente 12 palabras.")
            return
        }
        if (_estado.value is IdentidadUiState.Cargando) return
        _estado.value = IdentidadUiState.Cargando
        viewModelScope.launch {
            runCatching {
                withContext(defaultDispatcher) {
                    identityManager.loadFromPhrase(palabras)
                }
            }.onSuccess {
                val ikHex = withContext(defaultDispatcher) {
                    identityManager.ikPublicBytes.toHex()
                }
                // No re-mostramos la frase en restauraciones.
                _estado.value = IdentidadUiState.Cargada(ikHex, emptyList())
            }.onFailure { e ->
                _estado.value = IdentidadUiState.Error("Frase inválida o clave no derivable: ${e.message}")
            }
        }
    }

    /**
     * Confirma que el usuario ya anotó la frase y la borra del estado UI.
     * Debe llamarse tras mostrar las 12 palabras y recibir confirmación del usuario.
     */
    fun confirmarFraseAnotada() {
        val actual = _estado.value
        if (actual is IdentidadUiState.Cargada) {
            _estado.value = actual.copy(palabras = emptyList())
        }
    }

    /** Cierra la identidad en memoria (para modo dev / cambio de cuenta). */
    fun olvidarIdentidad() {
        identityManager.wipeMemory()
        _estado.value = IdentidadUiState.Vacio
    }

    override fun onCleared() {
        super.onCleared()
        // No borramos la IK aquí: el ViewModel puede recrearse por rotación de pantalla.
        // La IK solo se borra explícitamente con [olvidarIdentidad].
    }
}

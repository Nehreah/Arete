package com.renea.arete_social.feature.comunidades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.repository.RepositorioComunidades
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla intermedia "Comunidades": solo carga el listado
 * de comunidades ya creadas. Crear una nueva sigue siendo responsabilidad
 * exclusiva de CrearComunidadViewModel.
 */
class ComunidadesViewModel(
    private val repositorioComunidades: RepositorioComunidades,
    private val syncManager: com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager? = null
) : ViewModel() {

    private val _estado = MutableStateFlow<ComunidadesUiState>(ComunidadesUiState.Cargando)
    val estado: StateFlow<ComunidadesUiState> = _estado.asStateFlow()

    init {
        cargar()
        if (syncManager != null) {
            viewModelScope.launch {
                syncManager.eventosSincronizacion.collect { evento ->
                    if (evento is com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager.SyncEvent.OperacionesRecibidas) {
                        cargar()
                    }
                }
            }
        }
    }

    /** Público a propósito: la pantalla lo llama al volver de crear una comunidad,
     * para que el listado se refresque sin salir y volver a entrar. */
    fun recargar() {
        cargar()
    }

    private fun cargar() {
        _estado.value = ComunidadesUiState.Cargando
        viewModelScope.launch {
            runCatching { repositorioComunidades.listarComunidades() }
                .onSuccess { comunidades -> _estado.value = ComunidadesUiState.Listo(comunidades) }
                .onFailure { error ->
                    val mensaje = when {
                        error is java.net.UnknownHostException || error.message?.contains("No address associated with hostname", ignoreCase = true) == true ->
                            "Sin conexión de red o no se pudo sincronizar. Comprueba la conectividad de tu dispositivo."
                        error.message?.contains("timeout", ignoreCase = true) == true ->
                            "Tiempo de espera agotado al conectar con el servidor."
                        else -> error.message ?: "No se pudieron cargar las comunidades"
                    }
                    _estado.value = ComunidadesUiState.Error(mensaje)
                }
        }
    }
}

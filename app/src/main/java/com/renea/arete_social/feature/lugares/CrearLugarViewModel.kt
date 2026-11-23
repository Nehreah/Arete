package com.renea.arete_social.feature.lugares

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.repository.RepositorioLugares
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * La ubicación llega ya resuelta desde MapaScreen (el punto donde el usuario tocó
 * el mapa en "modo agregar lugar") — este ViewModel no sabe nada de GPS ni de mapas.
 */
class CrearLugarViewModel(
    private val repositorioLugares: RepositorioLugares,
    private val ubicacion: Ubicacion
) : ViewModel() {

    private val _estado = MutableStateFlow<CrearLugarUiState>(CrearLugarUiState.Editando)
    val estado: StateFlow<CrearLugarUiState> = _estado.asStateFlow()

    fun crear(nombre: String, descripcion: String) {
        if (nombre.isBlank()) {
            _estado.value = CrearLugarUiState.Error("Ponle un nombre al lugar")
            return
        }
        _estado.value = CrearLugarUiState.Guardando
        viewModelScope.launch {
            runCatching { repositorioLugares.crear(nombre, descripcion, ubicacion) }
                .onSuccess { lugar -> _estado.value = CrearLugarUiState.Creado(lugar.id) }
                .onFailure { error ->
                    _estado.value = CrearLugarUiState.Error(error.message ?: "No se pudo crear el lugar")
                }
        }
    }
}

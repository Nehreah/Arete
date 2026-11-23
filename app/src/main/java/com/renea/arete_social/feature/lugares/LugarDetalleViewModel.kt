package com.renea.arete_social.feature.lugares

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.repository.RepositorioLugares
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LugarDetalleViewModel(
    private val repositorioLugares: RepositorioLugares,
    private val lugarId: String
) : ViewModel() {

    private val _estado = MutableStateFlow<LugarDetalleUiState>(LugarDetalleUiState.Cargando)
    val estado: StateFlow<LugarDetalleUiState> = _estado.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        _estado.value = LugarDetalleUiState.Cargando
        viewModelScope.launch {
            val lugar = repositorioLugares.obtenerPorId(lugarId)
            _estado.value = lugar
                ?.let { LugarDetalleUiState.Listo(it) }
                ?: LugarDetalleUiState.Error("No se encontró el lugar")
        }
    }
}

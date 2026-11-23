package com.renea.arete_social.feature.comunidades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.repository.RepositorioComunidades
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Crea una Comunidad sola, sin exigir ningún Grupo asociado — una comunidad es
 * válida y completa por sí misma (ver RepositorioComunidades.crearComunidad).
 */
class CrearComunidadViewModel(
    private val repositorioComunidades: RepositorioComunidades
) : ViewModel() {

    private val _estado = MutableStateFlow<CrearComunidadUiState>(CrearComunidadUiState.Editando)
    val estado: StateFlow<CrearComunidadUiState> = _estado.asStateFlow()

    fun crear(
        nombre: String,
        descripcion: String,
        tipo: String,
        fotoBytes: ByteArray? = null,
        extension: String = "jpg"
    ) {
        if (nombre.isBlank()) {
            _estado.value = CrearComunidadUiState.Error("Ponle un nombre a la comunidad")
            return
        }
        _estado.value = CrearComunidadUiState.Guardando
        viewModelScope.launch {
            runCatching { repositorioComunidades.crearComunidad(nombre, descripcion, tipo, fotoBytes, extension) }
                .onSuccess { comunidad -> _estado.value = CrearComunidadUiState.Creada(comunidad.id) }
                .onFailure { error ->
                    _estado.value = CrearComunidadUiState.Error(error.message ?: "No se pudo crear la comunidad")
                }
        }
    }
}

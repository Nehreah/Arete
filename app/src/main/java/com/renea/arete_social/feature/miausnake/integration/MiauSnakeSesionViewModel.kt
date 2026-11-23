package com.renea.arete_social.feature.miausnake.integration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

/**
 * Guarda el [SnakeSessionCoordinator] de una mascota mientras dure su pantalla, de modo que una
 * rotación o recomposición no pierda la sesión abierta ni mande el mismo resultado dos veces.
 */
class MiauSnakeSesionViewModel(bridge: MichiPlayBridge) : ViewModel() {
    val coordinator = SnakeSessionCoordinator(bridge, viewModelScope)
}

class MiauSnakeSesionViewModelFactory(private val bridge: MichiPlayBridge) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MiauSnakeSesionViewModel(bridge) as T
    }
}

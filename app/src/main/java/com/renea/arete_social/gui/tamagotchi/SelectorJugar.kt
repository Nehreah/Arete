package com.renea.arete_social.gui.tamagotchi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado del selector «¿Cómo quieres jugar?» que se abre al pulsar JUGAR. Sin lógica de juego:
 *  - [elegirAutomatico] llama al callback de siempre (`vm.play`) y NADA más.
 *  - [elegirMiauSnake] solo abre el juego; no consume nada (eso lo decide el puente al terminar).
 *  - [cancelar] cierra el selector sin consumir recursos ni iniciar espera.
 * El selector se cierra ANTES de ejecutar la elección: un doble toque no repite la acción.
 */
class SelectorJugar(
    private val automatico: () -> Unit,
    private val miauSnake: () -> Unit
) {
    var abierto by mutableStateOf(false)
        private set

    fun abrir() {
        abierto = true
    }

    fun cancelar() {
        abierto = false
    }

    fun elegirAutomatico() {
        if (!abierto) return
        abierto = false
        automatico()
    }

    fun elegirMiauSnake() {
        if (!abierto) return
        abierto = false
        miauSnake()
    }
}

package com.renea.arete_social.feature.miausnake.levels

/**
 * Modos de juego de Miau Snake.
 * [META]: meta fija de bocados, semilla fija del mapa, medallas y mejor tiempo virtual.
 * [INFINITE]: sin meta, semilla aleatoria por partida, récord de puntuación.
 */
enum class GameMode(val code: Char, val title: String) {
    META('M', "Modo Meta"),
    INFINITE('I', "Modo Infinito");

    companion object {
        fun fromCode(c: Char): GameMode? = entries.firstOrNull { it.code == c }
    }
}

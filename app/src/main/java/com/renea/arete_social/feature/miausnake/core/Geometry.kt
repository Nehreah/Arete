package com.renea.arete_social.feature.miausnake.core

/**
 * Geometría básica del núcleo de Miau Snake (Fase 1).
 *
 * REGLA DE AISLAMIENTO: todo este paquete es Kotlin puro. No importa Android, Compose, Room,
 * corrutinas ni nada de Michi/Aréte. Lo vigila `CoreAislamientoTest`.
 */
data class Cell(val x: Int, val y: Int)

enum class Side {
    TOP, BOTTOM, LEFT, RIGHT;

    val opposite: Side
        get() = when (this) {
            TOP -> BOTTOM
            BOTTOM -> TOP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

/** El orden de declaración es parte del formato de repeticiones (2 bits = ordinal). No reordenar. */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }

    fun exitSide(): Side = when (this) {
        UP -> Side.TOP
        DOWN -> Side.BOTTOM
        LEFT -> Side.LEFT
        RIGHT -> Side.RIGHT
    }
}

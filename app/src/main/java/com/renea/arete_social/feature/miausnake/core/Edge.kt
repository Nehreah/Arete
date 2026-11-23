package com.renea.arete_social.feature.miausnake.core

enum class EdgeMode { WRAP, SOLID }

/** Apertura de túnel en un lado. El lado opuesto se abre solo, en las mismas posiciones. */
data class Gap(val side: Side, val from: Int, val to: Int)

/**
 * Reglas de borde de un mapa. Una sola estructura de datos cubre los cuatro modos
 * (sin borde, borde completo, borde parcial y túneles): el motor no tiene casos por pack.
 */
data class Edge(
    val default: EdgeMode,
    val wrapSides: Set<Side> = emptySet(),
    val gaps: List<Gap> = emptyList()
) {
    /**
     * @param side lado por el que sale la cabeza.
     * @param outside celda virtual, fuera del tablero, a la que apuntaría la cabeza.
     */
    fun isOpen(side: Side, outside: Cell): Boolean {
        if (default == EdgeMode.WRAP) return true
        if (side in wrapSides || side.opposite in wrapSides) return true
        val pos = if (side == Side.TOP || side == Side.BOTTOM) outside.x else outside.y
        return gaps.any { (it.side == side || it.side == side.opposite) && pos in it.from..it.to }
    }
}

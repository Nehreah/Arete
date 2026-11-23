package com.renea.arete_social.feature.miausnake.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeTest {
    private fun solid(edge: Edge) = TestLevels.open(cols = 20, rows = 28, edgeRules = edge)

    @Test fun `wrap en los cuatro lados`() {
        val lv = TestLevels.open(edge = EdgeMode.WRAP)
        assertEquals(Cell(19, 5), step(Cell(0, 5), Direction.LEFT, lv))
        assertEquals(Cell(0, 5), step(Cell(19, 5), Direction.RIGHT, lv))
        assertEquals(Cell(4, 27), step(Cell(4, 0), Direction.UP, lv))
        assertEquals(Cell(4, 0), step(Cell(4, 27), Direction.DOWN, lv))
    }

    @Test fun `solido bloquea los cuatro lados`() {
        val lv = TestLevels.open(edge = EdgeMode.SOLID)
        assertNull(step(Cell(0, 5), Direction.LEFT, lv))
        assertNull(step(Cell(19, 5), Direction.RIGHT, lv))
        assertNull(step(Cell(4, 0), Direction.UP, lv))
        assertNull(step(Cell(4, 27), Direction.DOWN, lv))
        assertEquals(Cell(1, 5), step(Cell(0, 5), Direction.RIGHT, lv))
    }

    @Test fun `parcial deja abiertos solo los lados indicados`() {
        val lv = solid(Edge(EdgeMode.SOLID, wrapSides = setOf(Side.LEFT, Side.RIGHT)))
        assertEquals(Cell(19, 5), step(Cell(0, 5), Direction.LEFT, lv))
        assertEquals(Cell(0, 5), step(Cell(19, 5), Direction.RIGHT, lv))
        assertNull(step(Cell(4, 0), Direction.UP, lv))
        assertNull(step(Cell(4, 27), Direction.DOWN, lv))
    }

    @Test fun `un lado parcial abre tambien su opuesto`() {
        val lv = solid(Edge(EdgeMode.SOLID, wrapSides = setOf(Side.LEFT)))
        assertEquals(Cell(0, 5), step(Cell(19, 5), Direction.RIGHT, lv))
    }

    @Test fun `tuneles abren el lado opuesto en las mismas posiciones`() {
        val lv = solid(Edge(EdgeMode.SOLID, gaps = listOf(Gap(Side.TOP, 9, 10))))
        assertEquals(Cell(9, 27), step(Cell(9, 0), Direction.UP, lv))
        assertEquals(Cell(10, 27), step(Cell(10, 0), Direction.UP, lv))
        assertNull(step(Cell(8, 0), Direction.UP, lv))
        assertEquals(Cell(10, 0), step(Cell(10, 27), Direction.DOWN, lv))
        assertNull(step(Cell(11, 27), Direction.DOWN, lv))
    }

    @Test fun `tuneles laterales usan la fila`() {
        val lv = solid(Edge(EdgeMode.SOLID, gaps = listOf(Gap(Side.LEFT, 13, 14))))
        assertEquals(Cell(19, 13), step(Cell(0, 13), Direction.LEFT, lv))
        assertNull(step(Cell(0, 12), Direction.LEFT, lv))
        assertEquals(Cell(0, 14), step(Cell(19, 14), Direction.RIGHT, lv))
    }

    @Test fun `aterrizar en una pared mata`() {
        val lv = TestLevels.open(edge = EdgeMode.WRAP, walls = setOf(Cell(19, 5)))
        assertNull(step(Cell(0, 5), Direction.LEFT, lv))
    }
}

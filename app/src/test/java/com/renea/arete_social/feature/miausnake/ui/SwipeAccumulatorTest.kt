package com.renea.arete_social.feature.miausnake.ui

import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.ui.input.SwipeAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeAccumulatorTest {
    @Test fun `no emite hasta alcanzar el umbral`() {
        val s = SwipeAccumulator(10f)
        assertNull(s.add(4f, 0f))
        assertNull(s.add(3f, 1f))
        assertEquals(Direction.RIGHT, s.add(4f, 0f))
    }

    @Test fun `los giros se encadenan sin levantar el dedo`() {
        val s = SwipeAccumulator(10f)
        assertEquals(Direction.RIGHT, s.add(12f, 1f))
        assertEquals(Direction.DOWN, s.add(1f, 12f))
        assertEquals(Direction.LEFT, s.add(-12f, -2f))
        assertEquals(Direction.UP, s.add(0f, -11f))
    }

    @Test fun `domina el eje con mas desplazamiento`() {
        val s = SwipeAccumulator(10f)
        assertEquals(Direction.UP, s.add(3f, -9f))
    }

    @Test fun `tras emitir el acumulado vuelve a cero`() {
        val s = SwipeAccumulator(10f)
        assertEquals(Direction.RIGHT, s.add(15f, 0f))
        assertNull(s.add(0f, 5f)) // no arrastra el exceso anterior
    }

    @Test fun `reset descarta el recorrido parcial`() {
        val s = SwipeAccumulator(10f)
        s.add(8f, 0f)
        s.reset()
        assertNull(s.add(0f, 8f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `umbral invalido`() {
        SwipeAccumulator(0f)
    }
}

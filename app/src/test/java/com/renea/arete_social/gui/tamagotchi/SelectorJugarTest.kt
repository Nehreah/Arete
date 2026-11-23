package com.renea.arete_social.gui.tamagotchi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorJugarTest {
    private var auto = 0
    private var snake = 0
    private val s = SelectorJugar({ auto++ }, { snake++ })

    @Test fun `cancelar no ejecuta nada`() {
        s.abrir(); s.cancelar()
        assertFalse(s.abierto); assertEquals(0, auto); assertEquals(0, snake)
    }

    @Test fun `automatico llama solo al callback de siempre y una vez aunque se toque dos veces`() {
        s.abrir(); s.elegirAutomatico(); s.elegirAutomatico()
        assertEquals(1, auto); assertEquals(0, snake); assertFalse(s.abierto)
    }

    @Test fun `miau snake solo abre el juego y una vez aunque se toque dos veces`() {
        s.abrir(); s.elegirMiauSnake(); s.elegirMiauSnake()
        assertEquals(0, auto); assertEquals(1, snake)
    }

    @Test fun `sin abrir el selector no se ejecuta ninguna eleccion`() {
        s.elegirAutomatico(); s.elegirMiauSnake()
        assertEquals(0, auto + snake); assertTrue(!s.abierto)
    }
}

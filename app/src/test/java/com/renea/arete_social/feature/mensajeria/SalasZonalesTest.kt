package com.renea.arete_social.feature.mensajeria

import com.renea.arete_social.domain.model.ElementoVisible
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisionRegional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalasZonalesTest {

    private val a = Region("a", "Armenia", Ubicacion(0.0, 0.0, 0L), "chat-a")
    private val b = Region("b", "Bogotá", Ubicacion(0.0, 1.0, 0L), "chat-b")
    private val c = Region("c", "Cali", Ubicacion(0.0, 2.0, 0L), "chat-c")

    private fun lugar(id: String, nombre: String = "Lugar $id", chat: String = "chat-$id") =
        EntidadSnapshot(TipoEntidadMapa.LUGAR, id, nombre, latitud = 0.0, longitud = 0.0, chatId = chat)

    private fun evento(id: String, chat: String = "chat-$id") =
        EntidadSnapshot(TipoEntidadMapa.EVENTO, id, "Evento $id", latitud = 0.0, longitud = 0.0, chatId = chat, fechaEvento = 1L)

    @Test
    fun `todas las regiones aparecen y la destacada va primero`() {
        val salas = SalasZonales.salasRegionales(listOf(a, b, c), destacada = c)
        assertEquals(listOf("chat-c", "chat-a", "chat-b"), salas.map { it.chatId })
        assertTrue(salas.all { it.tipo == TipoSala.REGIONAL && it.esPublica })
    }

    @Test
    fun `sin region destacada se ordenan por nombre`() {
        val salas = SalasZonales.salasRegionales(listOf(c, a, b), destacada = null)
        assertEquals(listOf("chat-a", "chat-b", "chat-c"), salas.map { it.chatId })
    }

    @Test
    fun `la sala regional usa el chat de la region y un nombre reconocible`() {
        val s = SalasZonales.salaRegional(b)
        assertEquals("chat-b", s.chatId)
        assertEquals("Regional · Bogotá", s.nombre)
        assertEquals("b", s.entidadId)
    }

    @Test
    fun `un lugar y un evento se convierten en sala con su tipo`() {
        val sl = SalasZonales.salaDe(lugar("l1"))!!
        val se = SalasZonales.salaDe(evento("e1"))!!
        assertEquals(TipoSala.LUGAR, sl.tipo)
        assertEquals(TipoSala.EVENTO, se.tipo)
        assertEquals("chat-l1", sl.chatId)
        assertEquals("l1", sl.entidadId)
    }

    @Test
    fun `sin chat no hay sala`() {
        assertNull(SalasZonales.salaDe(lugar("l1", chat = "")))
    }

    @Test
    fun `lo visible genera una sala por chat con su origen`() {
        val r = SalasZonales.salasDeElementos(
            listOf(
                ElementoVisible(lugar("a"), OrigenVisibilidad.CERCA),
                ElementoVisible(evento("b"), OrigenVisibilidad.RECIENTE, venceEn = 50L)
            ),
            vision = null
        )
        assertEquals(listOf("chat-a", "chat-b"), r.salas.map { it.chatId })
        assertEquals(OrigenVisibilidad.CERCA, r.visibilidadPorChat["chat-a"]?.origen)
        assertEquals(50L, r.visibilidadPorChat["chat-b"]?.venceEn)
    }

    @Test
    fun `el Super agrega los chats de la zona marcados como super`() {
        val region = Region("r", "R", Ubicacion(0.0, 0.0, 0L), "chat-r")
        val vision = VisionRegional(region, listOf(lugar("lejos")), listOf(evento("fiesta")), 0L)
        val r = SalasZonales.salasDeElementos(emptyList(), vision, superVenceEn = 900L)
        assertEquals(setOf("chat-lejos", "chat-fiesta"), r.salas.map { it.chatId }.toSet())
        assertEquals(OrigenVisibilidad.SUPER, r.visibilidadPorChat["chat-lejos"]?.origen)
        assertEquals(900L, r.visibilidadPorChat["chat-fiesta"]?.venceEn)
    }

    @Test
    fun `si un chat llega por varios origenes gana el de mayor prioridad y no se duplica`() {
        val region = Region("r", "R", Ubicacion(0.0, 0.0, 0L), "chat-r")
        val vision = VisionRegional(region, listOf(lugar("a")), emptyList(), 0L)
        val r = SalasZonales.salasDeElementos(
            listOf(ElementoVisible(lugar("a"), OrigenVisibilidad.PASE, 10L)), vision, superVenceEn = 99L
        )
        assertEquals(1, r.salas.size)
        assertEquals(OrigenVisibilidad.PASE, r.visibilidadPorChat["chat-a"]?.origen)
        assertEquals(10L, r.visibilidadPorChat["chat-a"]?.venceEn)
    }

    @Test
    fun `el orden es por prioridad de origen y luego por nombre`() {
        val region = Region("r", "R", Ubicacion(0.0, 0.0, 0L), "chat-r")
        val vision = VisionRegional(region, listOf(lugar("s", "Aaa super")), emptyList(), 0L)
        val r = SalasZonales.salasDeElementos(
            listOf(
                ElementoVisible(lugar("r1", "Zeta reciente"), OrigenVisibilidad.RECIENTE, 5L),
                ElementoVisible(lugar("c1", "Mmm cerca"), OrigenVisibilidad.CERCA)
            ),
            vision
        )
        assertEquals(listOf("Mmm cerca", "Zeta reciente", "Aaa super"), r.salas.map { it.nombre })
    }

    @Test
    fun `lo que no tiene chat se omite sin romper lo demas`() {
        val r = SalasZonales.salasDeElementos(
            listOf(
                ElementoVisible(lugar("sin", chat = ""), OrigenVisibilidad.CERCA),
                ElementoVisible(lugar("con"), OrigenVisibilidad.CERCA)
            ),
            null
        )
        assertEquals(listOf("chat-con"), r.salas.map { it.chatId })
    }
}

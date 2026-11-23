package com.renea.arete_social.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formato `[PASE:v1:<json>]` del pase de descubrimiento sobre la malla BLE. */
class ProtocoloPaseBleTest {

    private val pase = PaseDescubrimiento(
        id = "pase-1", emisorId = "ana", receptorId = "beto", emitidoEn = 1_700_000_000_000L,
        entidades = listOf(
            EntidadSnapshot(TipoEntidadMapa.LUGAR, "l1", "Biblioteca", "Con café", 3.4520001, -76.5320002, "chat-l1"),
            EntidadSnapshot(
                TipoEntidadMapa.EVENTO, "e1", "Feria del libro", "", 3.4518, -76.5321, "chat-e1",
                fechaEvento = 1_700_100_000_000L, visibilidad = VisibilidadEvento.PUBLICO
            )
        ),
        firma = "firma-base64==", clavePublicaEmisor = "clave-base64=="
    )

    @Test
    fun `ida y vuelta conserva todo el pase`() {
        val texto = ProtocoloPaseBle.codificar(pase)
        assertTrue(texto.startsWith("[PASE:v1:") && texto.endsWith("]"))
        assertEquals(pase, ProtocoloPaseBle.decodificar(texto))
    }

    @Test
    fun `los bytes firmados son identicos antes y despues de viajar`() {
        // Si cambiara la forma de escribir un número, la firma del receptor no coincidiría.
        val recibido = ProtocoloPaseBle.decodificar(ProtocoloPaseBle.codificar(pase))
        assertNotNull(recibido)
        assertEquals(pase.datosFirmados(), recibido!!.datosFirmados())
    }

    @Test
    fun `las decodificaciones invalidas devuelven null`() {
        assertNull(ProtocoloPaseBle.decodificar("hola"))
        assertNull(ProtocoloPaseBle.decodificar("[PASE:v1:{no es json}]"))
        assertNull(ProtocoloPaseBle.decodificar("[PASE:v1:{\"id\":\"x\"}"))                     // sin corchete final
        assertNull(ProtocoloPaseBle.decodificar("[PERFIL_DATOS:v1:{}]"))                         // otro protocolo
        assertNull(ProtocoloPaseBle.decodificar("[PASE:v1:" + "x".repeat(ProtocoloPaseBle.MAX_JSON_CHARS + 1) + "]"))
    }

    @Test
    fun `campos desconocidos de versiones futuras se ignoran`() {
        val texto = ProtocoloPaseBle.codificar(pase).replace("\"id\":\"pase-1\"", "\"id\":\"pase-1\",\"campoNuevo\":42")
        assertEquals(pase, ProtocoloPaseBle.decodificar(texto))
    }

    @Test
    fun `cambiar cualquier dato cambia los bytes firmados`() {
        val base = pase.datosFirmados()
        assertTrue(base != pase.copy(receptorId = "carlos").datosFirmados())
        assertTrue(base != pase.copy(emitidoEn = pase.emitidoEn + 1).datosFirmados())
        assertTrue(base != pase.copy(entidades = pase.entidades.map { it.copy(chatId = "otro") }).datosFirmados())
        assertTrue(base != pase.copy(entidades = pase.entidades.dropLast(1)).datosFirmados())
    }

    @Test
    fun `la firma y la clave no forman parte de lo que se firma`() {
        assertEquals(pase.datosFirmados(), pase.copy(firma = "otra", clavePublicaEmisor = "otra").datosFirmados())
    }

    @Test
    fun `mover texto entre campos no produce los mismos bytes`() {
        val a = pase.copy(emisorId = "ab", receptorId = "c")
        val b = pase.copy(emisorId = "a", receptorId = "bc")
        assertTrue(a.datosFirmados() != b.datosFirmados())
    }
}

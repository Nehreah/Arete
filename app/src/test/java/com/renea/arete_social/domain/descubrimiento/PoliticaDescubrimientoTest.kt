package com.renea.arete_social.domain.descubrimiento

import com.renea.arete_social.domain.model.Descubrimiento
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.EstadoDescubrimiento
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.testutils.RelojFalso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de descubrimiento de lugares y eventos (500 m, 24 h, pases). */
class PoliticaDescubrimientoTest {

    private val reloj = RelojFalso()
    private val politica = PoliticaDescubrimiento(reloj)
    private val hora = 3_600_000L

    private fun lugar(id: String = "l1") = EntidadSnapshot(
        tipo = TipoEntidadMapa.LUGAR, id = id, nombre = "Biblioteca",
        latitud = 3.45, longitud = -76.53, chatId = "chat-$id"
    )

    private fun evento(fecha: Long? = null, visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO) = EntidadSnapshot(
        tipo = TipoEntidadMapa.EVENTO, id = "e1", nombre = "Feria", latitud = 3.45, longitud = -76.53,
        chatId = "chat-e1", fechaEvento = fecha, visibilidad = visibilidad
    )

    @Test
    fun `dentro del radio esta cerca aunque nunca se haya registrado`() {
        assertEquals(EstadoDescubrimiento.Cerca, politica.estadoDe(null, 120.0))
    }

    @Test
    fun `en el limite exacto de 500 m esta cerca y a 501 m ya no`() {
        assertEquals(EstadoDescubrimiento.Cerca, politica.estadoDe(null, 500.0))
        assertEquals(EstadoDescubrimiento.NoDescubierto, politica.estadoDe(null, 500.01))
    }

    @Test
    fun `sin ubicacion ni registro no se descubre nada`() {
        assertEquals(EstadoDescubrimiento.NoDescubierto, politica.estadoDe(null, null))
    }

    @Test
    fun `al alejarse sigue reciente 24 horas desde la ultima vez que estuvo cerca`() {
        val visto = reloj.ahora
        val d = Descubrimiento(lugar(), ultimoVistoCerca = visto)
        reloj.avanzarHoras(23.9)
        val estado = politica.estadoDe(d, 5_000.0)
        assertEquals(EstadoDescubrimiento.Reciente(visto + 24 * hora), estado)
    }

    @Test
    fun `a las 24 horas exactas ya se oculta`() {
        val d = Descubrimiento(lugar(), ultimoVistoCerca = reloj.ahora)
        reloj.avanzarHoras(24.0)
        assertEquals(EstadoDescubrimiento.NoDescubierto, politica.estadoDe(d, 5_000.0))
        assertTrue(politica.estaVencido(d))
    }

    @Test
    fun `volver a estar cerca reinicia las 24 horas`() {
        var d = Descubrimiento(lugar(), ultimoVistoCerca = reloj.ahora)
        reloj.avanzarHoras(20.0)
        d = politica.marcarVistoCerca(d)
        reloj.avanzarHoras(20.0)
        assertTrue(politica.estadoDe(d, 5_000.0) is EstadoDescubrimiento.Reciente)
    }

    @Test
    fun `un pase dura 24 horas desde que se recibe`() {
        val recibido = reloj.ahora
        val d = Descubrimiento(lugar(), paseRecibidoEn = recibido, paseEmisorId = "ana")
        reloj.avanzarHoras(10.0)
        assertEquals(EstadoDescubrimiento.PorPase(recibido + 24 * hora), politica.estadoDe(d, 9_000.0))
        reloj.avanzarHoras(14.0)
        assertEquals(EstadoDescubrimiento.NoDescubierto, politica.estadoDe(d, 9_000.0))
    }

    @Test
    fun `estar cerca tiene prioridad sobre un pase vigente`() {
        val d = Descubrimiento(lugar(), paseRecibidoEn = reloj.ahora, paseEmisorId = "ana")
        assertEquals(EstadoDescubrimiento.Cerca, politica.estadoDe(d, 100.0))
    }

    @Test
    fun `reciente tiene prioridad sobre pase y por eso se puede compartir`() {
        val d = Descubrimiento(lugar(), ultimoVistoCerca = reloj.ahora, paseRecibidoEn = reloj.ahora, paseEmisorId = "ana")
        val estado = politica.estadoDe(d, 3_000.0)
        assertTrue(estado is EstadoDescubrimiento.Reciente)
        assertTrue(politica.puedeCompartir(estado))
    }

    @Test
    fun `solo se comparte lo cercano o reciente nunca lo recibido por pase`() {
        assertTrue(politica.puedeCompartir(EstadoDescubrimiento.Cerca))
        assertTrue(politica.puedeCompartir(EstadoDescubrimiento.Reciente(1L)))
        assertFalse(politica.puedeCompartir(EstadoDescubrimiento.PorPase(1L)))
        assertFalse(politica.puedeCompartir(EstadoDescubrimiento.NoDescubierto))
    }

    @Test
    fun `un pase no se renueva solo se acepta cuando no se ve por ningun origen`() {
        assertTrue(politica.puedeAceptarPase(EstadoDescubrimiento.NoDescubierto))
        assertFalse(politica.puedeAceptarPase(EstadoDescubrimiento.Cerca))
        assertFalse(politica.puedeAceptarPase(EstadoDescubrimiento.Reciente(1L)))
        assertFalse(politica.puedeAceptarPase(EstadoDescubrimiento.PorPase(1L)))
    }

    @Test
    fun `tras vencer un pase se puede aceptar otro`() {
        val d = Descubrimiento(lugar(), paseRecibidoEn = reloj.ahora, paseEmisorId = "ana")
        assertFalse(politica.puedeAceptarPase(politica.estadoDe(d, 9_000.0)))
        reloj.avanzarHoras(24.0)
        assertTrue(politica.puedeAceptarPase(politica.estadoDe(d, 9_000.0)))
        val nuevo = politica.registrarPase(d, lugar(), "beto")
        assertEquals("beto", nuevo.paseEmisorId)
        assertTrue(politica.estadoDe(nuevo, 9_000.0) is EstadoDescubrimiento.PorPase)
    }

    @Test
    fun `un evento terminado hace mas de un dia no se recuerda como reciente`() {
        val fecha = reloj.ahora - 3 * 24 * hora
        val d = Descubrimiento(evento(fecha), ultimoVistoCerca = reloj.ahora - hora)
        assertEquals(EstadoDescubrimiento.NoDescubierto, politica.estadoDe(d, 5_000.0))
    }

    @Test
    fun `un evento terminado sigue viendose mientras estes cerca como hacia el mapa`() {
        val fecha = reloj.ahora - 3 * 24 * hora
        val d = Descubrimiento(evento(fecha), ultimoVistoCerca = reloj.ahora)
        assertEquals(EstadoDescubrimiento.Cerca, politica.estadoDe(d, 50.0))
    }

    @Test
    fun `un evento de hoy se recuerda con normalidad`() {
        val d = Descubrimiento(evento(reloj.ahora + hora), ultimoVistoCerca = reloj.ahora)
        assertTrue(politica.estadoDe(d, 5_000.0) is EstadoDescubrimiento.Reciente)
    }

    @Test
    fun `solo lo publico es compartible`() {
        assertTrue(politica.esCompartible(lugar()))
        assertTrue(politica.esCompartible(evento(visibilidad = VisibilidadEvento.PUBLICO)))
        assertFalse(politica.esCompartible(evento(visibilidad = VisibilidadEvento.AMIGOS)))
        assertFalse(politica.esCompartible(evento(visibilidad = VisibilidadEvento.COMUNIDAD)))
    }

    @Test
    fun `el origen y el vencimiento se traducen para la interfaz`() {
        assertNull(politica.origenDe(EstadoDescubrimiento.NoDescubierto))
        assertEquals(OrigenVisibilidad.CERCA, politica.origenDe(EstadoDescubrimiento.Cerca))
        assertEquals(OrigenVisibilidad.RECIENTE, politica.origenDe(EstadoDescubrimiento.Reciente(5L)))
        assertEquals(OrigenVisibilidad.PASE, politica.origenDe(EstadoDescubrimiento.PorPase(5L)))
        assertNull(politica.venceEnDe(EstadoDescubrimiento.Cerca))
        assertEquals(5L, politica.venceEnDe(EstadoDescubrimiento.Reciente(5L)))
        assertEquals(7L, politica.venceEnDe(EstadoDescubrimiento.PorPase(7L)))
    }
}

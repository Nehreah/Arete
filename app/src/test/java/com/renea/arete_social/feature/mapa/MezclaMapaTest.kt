package com.renea.arete_social.feature.mapa

import com.renea.arete_social.domain.model.ElementoVisible
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.model.VisionRegional
import com.renea.arete_social.domain.model.claveEntidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MezclaMapaTest {

    private fun lugar(id: String) = Lugar(id, "Lugar $id", "", Ubicacion(3.45, -76.53, 0L), "chat-$id")
    private fun evento(id: String) = Evento(
        id, "Evento $id", "", 1L, Ubicacion(3.45, -76.53, 0L), null, "x", null, emptyList(), "chat-$id", VisibilidadEvento.PUBLICO
    )
    private fun snapLugar(id: String) = EntidadSnapshot(TipoEntidadMapa.LUGAR, id, "Copia $id", latitud = 3.4, longitud = -76.5, chatId = "chat-$id")
    private fun snapEvento(id: String) = EntidadSnapshot(TipoEntidadMapa.EVENTO, id, "Copia $id", latitud = 3.4, longitud = -76.5, chatId = "chat-$id", fechaEvento = 5L)
    private val region = Region("r", "R", Ubicacion(3.4, -76.5, 0L), "chat-r")

    private fun clave(t: TipoEntidadMapa, id: String) = claveEntidad(t, id)

    @Test
    fun `sin servicios se comporta como antes solo lo cercano`() {
        val r = MezclaMapa.mezclar(listOf(lugar("a")), listOf(evento("e")), emptyList(), null)
        assertEquals(listOf("a"), r.lugares.map { it.id })
        assertEquals(listOf("e"), r.eventos.map { it.id })
        assertEquals(OrigenVisibilidad.CERCA, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "a")]?.origen)
    }

    @Test
    fun `lo descubierto se suma con su origen y vencimiento`() {
        val d = listOf(
            ElementoVisible(snapLugar("viejo"), OrigenVisibilidad.RECIENTE, venceEn = 777L),
            ElementoVisible(snapEvento("pase"), OrigenVisibilidad.PASE, venceEn = 888L)
        )
        val r = MezclaMapa.mezclar(emptyList(), emptyList(), d, null)
        assertEquals(listOf("viejo"), r.lugares.map { it.id })
        assertEquals(listOf("pase"), r.eventos.map { it.id })
        assertEquals(777L, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "viejo")]?.venceEn)
        assertEquals(OrigenVisibilidad.PASE, r.visibilidad[clave(TipoEntidadMapa.EVENTO, "pase")]?.origen)
    }

    @Test
    fun `si algo esta cerca y descubierto se muestra una sola vez y gana cerca`() {
        val d = listOf(ElementoVisible(snapLugar("a"), OrigenVisibilidad.RECIENTE, 5L))
        val r = MezclaMapa.mezclar(listOf(lugar("a")), emptyList(), d, null)
        assertEquals(1, r.lugares.size)
        assertEquals("Lugar a", r.lugares.single().nombre) // el objeto completo del servidor, no la copia
        assertEquals(OrigenVisibilidad.CERCA, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "a")]?.origen)
        assertNull(r.visibilidad[clave(TipoEntidadMapa.LUGAR, "a")]?.venceEn)
    }

    @Test
    fun `el origen de mayor prioridad gana entre reciente pase y super`() {
        val d = listOf(
            ElementoVisible(snapLugar("a"), OrigenVisibilidad.PASE, 9L),
            ElementoVisible(snapLugar("a"), OrigenVisibilidad.RECIENTE, 7L)
        )
        val vision = VisionRegional(region, listOf(snapLugar("a")), emptyList(), 0L)
        val r = MezclaMapa.mezclar(emptyList(), emptyList(), d, vision, superVenceEn = 99L)
        assertEquals(1, r.lugares.size)
        assertEquals(OrigenVisibilidad.RECIENTE, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "a")]?.origen)
        assertEquals(7L, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "a")]?.venceEn)
    }

    @Test
    fun `la vision del super agrega lo lejano marcado como super`() {
        val vision = VisionRegional(region, listOf(snapLugar("lejos")), listOf(snapEvento("fiesta")), 0L)
        val r = MezclaMapa.mezclar(listOf(lugar("cerca")), emptyList(), emptyList(), vision, superVenceEn = 99L)
        assertEquals(setOf("cerca", "lejos"), r.lugares.map { it.id }.toSet())
        assertEquals(listOf("fiesta"), r.eventos.map { it.id })
        assertEquals(OrigenVisibilidad.SUPER, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "lejos")]?.origen)
        assertEquals(99L, r.visibilidad[clave(TipoEntidadMapa.EVENTO, "fiesta")]?.venceEn)
        assertEquals(OrigenVisibilidad.CERCA, r.visibilidad[clave(TipoEntidadMapa.LUGAR, "cerca")]?.origen)
    }

    @Test
    fun `un lugar y un evento con el mismo id no se pisan`() {
        val r = MezclaMapa.mezclar(listOf(lugar("x")), listOf(evento("x")), emptyList(), null)
        assertEquals(1, r.lugares.size)
        assertEquals(1, r.eventos.size)
        assertTrue(r.visibilidad.containsKey(clave(TipoEntidadMapa.LUGAR, "x")))
        assertTrue(r.visibilidad.containsKey(clave(TipoEntidadMapa.EVENTO, "x")))
    }

    @Test
    fun `sin vision el super no agrega nada`() {
        val r = MezclaMapa.mezclar(emptyList(), emptyList(), emptyList(), null, superVenceEn = 5L)
        assertTrue(r.lugares.isEmpty() && r.eventos.isEmpty() && r.visibilidad.isEmpty())
    }
}

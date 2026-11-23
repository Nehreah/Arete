package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.RegionesPredeterminadas
import com.renea.arete_social.domain.model.Ubicacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorRegionalTest {

    // Dos regiones sobre el ecuador separadas por 1° de longitud (~111 km).
    private val a = Region("a", "A", Ubicacion(0.0, 0.0, 0L), "chat-a")
    private val b = Region("b", "B", Ubicacion(0.0, 1.0, 0L), "chat-b")
    private val selector = SelectorRegional(listOf(a, b))

    private fun punto(lng: Double, lat: Double = 0.0) = Ubicacion(lat, lng, 0L)

    @Test
    fun `la region mas cercana de las ciudades reales es la correcta`() {
        val real = SelectorRegional(RegionesPredeterminadas.todas)
        assertEquals("Cali", real.masCercana(Ubicacion(3.43, -76.52, 0L))?.nombre)
        assertEquals("Bogotá", real.masCercana(Ubicacion(4.65, -74.10, 0L))?.nombre)
        assertEquals("Medellín", real.masCercana(Ubicacion(6.25, -75.57, 0L))?.nombre)
    }

    @Test
    fun `sin regiones no hay region`() {
        val vacio = SelectorRegional(emptyList())
        assertNull(vacio.masCercana(punto(0.0)))
        assertNull(vacio.elegir(punto(0.0), null))
        assertNull(vacio.regionDeContenido(punto(0.0)))
    }

    @Test
    fun `la primera vez se elige la mas cercana`() {
        assertEquals(b, selector.elegir(punto(0.9), null))
    }

    @Test
    fun `no cambia de region por una ventaja pequena`() {
        // A 0.52° está un poco más cerca de B (~4 km de ventaja) pero no «claramente».
        assertEquals(a, selector.elegir(punto(0.52), actual = a))
    }

    @Test
    fun `cambia de region cuando la otra queda claramente mas cerca`() {
        assertEquals(b, selector.elegir(punto(0.56), actual = a))
    }

    @Test
    fun `no parpadea al cruzar la zona intermedia de ida y vuelta`() {
        var actual: Region? = a
        val recorrido = listOf(0.30, 0.45, 0.50, 0.52, 0.50, 0.48, 0.30)
        for (lng in recorrido) actual = selector.elegir(punto(lng), actual)
        assertEquals(a, actual)
    }

    @Test
    fun `una region desconocida para el selector se trata como primera vez`() {
        val fantasma = Region("x", "X", Ubicacion(50.0, 50.0, 0L), "chat-x")
        assertEquals(a, selector.elegir(punto(0.1), fantasma))
    }

    @Test
    fun `un punto pertenece a la region cuyo centro le queda mas cerca`() {
        assertEquals(a, selector.regionDeContenido(punto(0.3)))
        assertEquals(b, selector.regionDeContenido(punto(0.7)))
        assertTrue(selector.perteneceA(a, punto(0.3)))
        assertFalse(selector.perteneceA(b, punto(0.3)))
    }

    @Test
    fun `un punto a mas de 100 km del centro mas cercano no pertenece a ninguna zona`() {
        // 3° de longitud ≈ 333 km del centro de A y 222 km del de B.
        assertNull(selector.regionDeContenido(punto(3.0)))
        assertFalse(selector.perteneceA(b, punto(3.0)))
    }

    @Test
    fun `el tope de la zona se puede cambiar`() {
        val amplio = SelectorRegional(listOf(a, b), radioMaxMetros = 400_000.0)
        assertEquals(b, amplio.regionDeContenido(punto(3.0)))
    }

    @Test
    fun `las regiones predeterminadas tienen ids y chats unicos con formato uuid`() {
        val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val todas = RegionesPredeterminadas.todas
        assertEquals(6, todas.size)
        assertEquals(todas.size, todas.map { it.id }.toSet().size)
        assertEquals(todas.size, todas.map { it.chatId }.toSet().size)
        assertTrue(todas.all { uuid.matches(it.id) && uuid.matches(it.chatId) })
    }
}

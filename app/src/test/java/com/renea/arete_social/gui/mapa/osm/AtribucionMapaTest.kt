package com.renea.arete_social.gui.mapa.osm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtribucionMapaTest {
    private fun luminancia(c: Int) =
        0.213 * ((c shr 16) and 0xFF) + 0.715 * ((c shr 8) and 0xFF) + 0.072 * (c and 0xFF)

    @Test
    fun laAtribucionDeMapnikEsLaQueExigeOpenStreetMap() {
        assertEquals("© OpenStreetMap contributors", ProveedorTeselasMapnik.atribucion)
        assertEquals("https://www.openstreetmap.org/copyright", ProveedorTeselasMapnik.urlAtribucion)
    }

    @Test
    fun enTemasOscurosElTextoEsClaroSobreFondoOscuro() {
        listOf(TemaMapa.OSCURO_DIA, TemaMapa.OSCURO_NOCHE).forEach {
            val p = paletaAtribucion(it)
            assertTrue("$it", luminancia(p.texto) > luminancia(p.fondo) + 100)
        }
    }

    @Test
    fun enTemasClarosElTextoEsOscuroSobreFondoClaro() {
        listOf(TemaMapa.CLARO_DIA, TemaMapa.CLARO_NOCHE).forEach {
            val p = paletaAtribucion(it)
            assertTrue("$it", luminancia(p.fondo) > luminancia(p.texto) + 100)
        }
    }

    @Test
    fun elFondoEsSemitransparente() {
        TemaMapa.entries.forEach {
            val alfa = (paletaAtribucion(it).fondo ushr 24) and 0xFF
            assertTrue("$it", alfa in 1..254)
        }
    }
}

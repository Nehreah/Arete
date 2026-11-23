package com.renea.arete_social.gui.mapa

import com.renea.arete_social.gui.mapa.osm.ProyeccionMapa
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntornoPaseoMapaTest {

    // En el ecuador, 1 px = 1 m.
    private fun proyeccion(orientacion: Float = 0f) = ProyeccionMapa(
        latCentro = 0.0, lngCentro = -76.5, pxPorGradoLng = 111_320.0,
        orientacionGrados = orientacion, anchoPx = 1000, altoPx = 2000
    )

    @Test
    fun noEstaListoHastaTenerTamano() {
        val entorno = EntornoPaseoMapa()
        assertFalse(entorno.actualizar(proyeccion(), 0, 2000, 3f))
        assertNull(entorno.puntoEnZona(Random(1)))
    }

    @Test
    fun elCentroDelMapaEsElOrigenYCaeEnElCentroDeLaPantalla() {
        val entorno = EntornoPaseoMapa()
        val proy = proyeccion()
        assertTrue(entorno.actualizar(proy, 1000, 2000, 3f))
        assertEquals(0f, entorno.centro.x, 0.01f)
        assertEquals(0f, entorno.centro.y, 0.01f)
        val p = entorno.aPantalla(proy, 0f, 0f)!!
        assertEquals(500f, p.x, 0.05f)
        assertEquals(1000f, p.y, 0.05f)
        // 100 m al norte: 100 px más arriba.
        assertEquals(900f, entorno.aPantalla(proy, 0f, 100f)!!.y, 0.05f)
    }

    @Test
    fun elBearingSaleDeLaOrientacionDeLaProyeccion() {
        val entorno = EntornoPaseoMapa()
        entorno.actualizar(proyeccion(orientacion = -90f), 1000, 2000, 3f)   // bearing 90°
        assertEquals((PI / 2).toFloat(), entorno.bearingRad, 1e-4f)
        entorno.actualizar(proyeccion(orientacion = 0f), 1000, 2000, 3f)
        assertEquals(0f, entorno.bearingRad, 1e-4f)
    }

    @Test
    fun velocidadYSeparacionEscalanConLaDensidadYLosMetrosPorPixel() {
        val entorno = EntornoPaseoMapa()
        entorno.actualizar(proyeccion(), 1000, 2000, 2f)
        assertEquals(30f * 2f * 1f, entorno.velocidadBase, 1e-3f)
        assertEquals(40f * 2f * 1f, entorno.separacionMinima, 1e-3f)
    }

    @Test
    fun laZonaYLaVisibilidadSiguenAlMapaConCualquierGiro() {
        listOf(0f, 45f, -90f, 180f).forEach { giro ->
            val entorno = EntornoPaseoMapa()
            entorno.actualizar(proyeccion(giro), 1000, 2000, 3f)
            assertTrue("centro en zona con giro $giro", entorno.estaEnZona(0f, 0f))
            assertTrue(entorno.estaVisible(0f, 0f))
            // 5 km lejos: ni en zona ni visible.
            assertFalse(entorno.estaEnZona(5000f, 0f))
            assertFalse(entorno.estaVisible(0f, 5000f))
        }
    }

    @Test
    fun losPuntosAleatoriosCaenDentroDeLaZonaPreferida() {
        listOf(0f, 70f, -135f).forEach { giro ->
            val entorno = EntornoPaseoMapa()
            val proy = proyeccion(giro)
            entorno.actualizar(proy, 1000, 2000, 3f)
            val azar = Random(7)
            repeat(50) {
                val p = entorno.puntoEnZona(azar)
                assertNotNull(p)
                p!!
                val pantalla = entorno.aPantalla(proy, p.x, p.y)!!
                assertTrue("x ${pantalla.x}", pantalla.x / 1000f in 0.069f..0.931f)
                assertTrue("y ${pantalla.y}", pantalla.y / 2000f in 0.199f..0.801f)
                assertTrue(entorno.estaEnZona(p.x, p.y))
            }
        }
    }

    @Test
    fun elOrigenNoSeMuevePeroElCentroSiguePorElMapa() {
        val entorno = EntornoPaseoMapa()
        entorno.actualizar(proyeccion(), 1000, 2000, 3f)
        // La cámara se desplaza 200 m al este (0,0018°): el origen queda fijo, el centro se mueve.
        val movida = ProyeccionMapa(0.0, -76.5 + 200.0 / 111_320.0, 111_320.0, 0f, 1000, 2000)
        entorno.actualizar(movida, 1000, 2000, 3f)
        assertTrue(abs(entorno.centro.x - 200f) < 0.5f)
        assertEquals(0f, entorno.centro.y, 0.5f)
    }
}

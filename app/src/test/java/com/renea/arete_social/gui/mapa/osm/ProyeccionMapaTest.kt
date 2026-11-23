package com.renea.arete_social.gui.mapa.osm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProyeccionMapaTest {

    // 1 px = 1 m en el ecuador: pxPorGradoLng = 111_320 px/grado.
    private fun proyeccion(orientacion: Float = 0f, lat: Double = 0.0) = ProyeccionMapa(
        latCentro = lat, lngCentro = -76.5, pxPorGradoLng = 111_320.0 * kotlin.math.cos(Math.toRadians(lat)),
        orientacionGrados = orientacion, anchoPx = 1000, altoPx = 2000
    )

    @Test
    fun elCentroCaeEnElCentroDeLaPantalla() {
        val p = proyeccion().aPantalla(0.0, -76.5)
        assertEquals(500f, p.x, 0.01f)
        assertEquals(1000f, p.y, 0.01f)
    }

    @Test
    fun conOrientacionCeroElNorteQuedaArribaYElEsteALaDerecha() {
        val norte = proyeccion().aPantalla(100.0 / 111_320.0, -76.5)
        assertEquals(500f, norte.x, 0.05f)
        assertEquals(900f, norte.y, 0.05f)
        val este = proyeccion().aPantalla(0.0, -76.5 + 100.0 / 111_320.0)
        assertEquals(600f, este.x, 0.05f)
        assertEquals(1000f, este.y, 0.05f)
    }

    @Test
    fun conBearing90LaCamaraMiraAlEsteYElEsteQuedaArriba() {
        val orientacion = AngulosMapa.orientacionDesdeBearing(90f)
        val este = proyeccion(orientacion).aPantalla(0.0, -76.5 + 100.0 / 111_320.0)
        assertEquals(500f, este.x, 0.05f)
        assertEquals(900f, este.y, 0.05f)
    }

    @Test
    fun aGeoEsLaInversaDeAPantallaConCualquierGiro() {
        listOf(0f, 37f, -90f, 180f, 270f).forEach { giro ->
            val p = proyeccion(giro, lat = 3.4516)
            val geo = PuntoMapa(3.4530, -76.5110)
            val pantalla = p.aPantalla(geo.latitud, geo.longitud)
            val vuelta = p.aGeo(pantalla.x, pantalla.y)
            assertEquals("lat con giro $giro", geo.latitud, vuelta.latitud, 1e-6)
            assertEquals("lng con giro $giro", geo.longitud, vuelta.longitud, 1e-6)
        }
    }

    @Test
    fun metrosPorPixelCoincideConLaEscala() {
        assertEquals(1.0, proyeccion().metrosPorPixel, 1e-6)
        assertTrue(proyeccion(lat = 60.0).metrosPorPixel > 0.0)
    }

    @Test
    fun cruzarElAntimeridianoNoDaSaltosEnormes() {
        val p = ProyeccionMapa(0.0, 179.999, 111_320.0, 0f, 1000, 1000)
        val al_otro_lado = p.aPantalla(0.0, -179.999)
        assertTrue(al_otro_lado.x in 500f..800f)
    }
}

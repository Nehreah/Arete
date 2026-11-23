package com.renea.arete_social.gui.mapa.osm

import org.junit.Assert.assertEquals
import org.junit.Test

class AngulosMapaTest {
    @Test
    fun orientacionEsElOpuestoDelBearing() {
        assertEquals(-45f, AngulosMapa.orientacionDesdeBearing(45f), 0f)
        assertEquals(45f, AngulosMapa.bearingDesdeOrientacion(-45f), 0f)
        assertEquals(315f, AngulosMapa.bearingDesdeOrientacion(45f), 0f)
    }

    @Test
    fun normalizaAlRangoCeroTrescientosSesenta() {
        assertEquals(350f, AngulosMapa.normalizar360(-10f), 0f)
        assertEquals(10f, AngulosMapa.normalizar360(370f), 0f)
        assertEquals(0f, AngulosMapa.normalizar360(360f), 0f)
    }

    @Test
    fun elGiroMasCortoCruzaElNorte() {
        assertEquals(20f, AngulosMapa.diferenciaMasCorta(350f, 10f), 0f)
        assertEquals(-20f, AngulosMapa.diferenciaMasCorta(10f, 350f), 0f)
        assertEquals(90f, AngulosMapa.diferenciaMasCorta(0f, 90f), 0f)
    }
}

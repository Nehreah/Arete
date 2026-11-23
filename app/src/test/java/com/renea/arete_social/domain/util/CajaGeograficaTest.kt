package com.renea.arete_social.domain.util

import com.renea.arete_social.domain.model.RegionesPredeterminadas
import com.renea.arete_social.domain.model.ZonaRegional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CajaGeograficaTest {

    @Test
    fun `la caja contiene el circulo completo`() {
        val caja = cajaAlrededorDe(3.4516, -76.5320, 100_000.0)
        // Un punto a ~99 km al norte, sur, este y oeste siempre queda dentro de la caja.
        for ((dLat, dLng) in listOf(0.89 to 0.0, -0.89 to 0.0, 0.0 to 0.89, 0.0 to -0.89)) {
            val lat = 3.4516 + dLat
            val lng = -76.5320 + dLng
            assertTrue("($lat, $lng)", lat in caja.latMin..caja.latMax && lng in caja.lngMin..caja.lngMax)
        }
    }

    @Test
    fun `la caja de latitud mide unos 0,9 grados para 100 km`() {
        val caja = cajaAlrededorDe(0.0, 0.0, 100_000.0)
        assertEquals(0.898, caja.latMax, 0.01)
        assertEquals(-0.898, caja.latMin, 0.01)
    }

    @Test
    fun `cerca de los polos los grados de longitud no se disparan`() {
        val caja = cajaAlrededorDe(89.9, 0.0, 100_000.0)
        assertTrue(caja.latMax <= 90.0)
        assertTrue(caja.lngMin >= -180.0 && caja.lngMax <= 180.0)
    }

    @Test
    fun `la caja de una zona incluye un margen para no perder lo del borde`() {
        val cali = RegionesPredeterminadas.todas.first { it.nombre == "Cali" }
        val zona = ZonaRegional(cali)
        val sinMargen = cajaAlrededorDe(cali.ubicacion.latitud, cali.ubicacion.longitud, zona.radioMaxMetros)
        val conMargen = zona.caja()
        assertTrue(conMargen.latMax > sinMargen.latMax && conMargen.lngMin < sinMargen.lngMin)
    }
}

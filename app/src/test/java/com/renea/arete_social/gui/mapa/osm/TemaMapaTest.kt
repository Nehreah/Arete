package com.renea.arete_social.gui.mapa.osm

import com.renea.arete_social.gui.theme.CicloDia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemaMapaTest {
    @Test
    fun resuelveLasCuatroCombinaciones() {
        assertEquals(TemaMapa.CLARO_DIA, TemaMapa.resolver(false, CicloDia.DIA))
        assertEquals(TemaMapa.CLARO_NOCHE, TemaMapa.resolver(false, CicloDia.NOCHE))
        assertEquals(TemaMapa.OSCURO_DIA, TemaMapa.resolver(true, CicloDia.DIA))
        assertEquals(TemaMapa.OSCURO_NOCHE, TemaMapa.resolver(true, CicloDia.NOCHE))
    }

    @Test
    fun todosLosTemasNeutralizanElColorDeLasTeselas() {
        // Fase 5: incluso CLARO_DIA lleva filtro (desaturación suave) para que parques y agua de MAPNIK
        // no compitan con los pines de marca. Si algún día se pasa a un proveedor con estilo propio,
        // este es el test que hay que revisar.
        TemaMapa.entries.forEach {
            assertNotNull("$it debe tener filtro", EstilosTemaMapa.de(it).filtro)
        }
    }

    @Test
    fun losTemasOscurosInviertenYLosClarosNo() {
        assertTrue(EstilosTemaMapa.de(TemaMapa.OSCURO_DIA).filtro!!.invertir)
        assertTrue(EstilosTemaMapa.de(TemaMapa.OSCURO_NOCHE).filtro!!.invertir)
        assertTrue(!EstilosTemaMapa.de(TemaMapa.CLARO_NOCHE).filtro!!.invertir)
    }

    @Test
    fun laNocheOscuraEsMasOscuraQueElDiaOscuro() {
        val dia = EstilosTemaMapa.de(TemaMapa.OSCURO_DIA).filtro!!
        val noche = EstilosTemaMapa.de(TemaMapa.OSCURO_NOCHE).filtro!!
        assertTrue(noche.desplazamiento < dia.desplazamiento)
    }
}

package com.renea.arete_social.gui.mapa.osm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Calibración de los filtros de color (Fase 5) sin dispositivo: se pasan los colores estándar de
 * MAPNIK por [ParametrosFiltro.aplicar] (misma matriz que usa la pantalla) y se comparan con los
 * hex de los 4 estilos JSON de Google que sustituyen.
 */
class CalibracionTemaTest {
    private companion object {
        const val TIERRA = 0xF2EFE9      // fondo de MAPNIK
        const val CALLE = 0xFFFFFF       // calles residenciales
        const val PARQUE = 0xC8FACC      // verde de parques
        const val AGUA = 0xAAD3DF        // azul de agua
        const val TEXTO = 0x000000       // etiquetas
        const val TOLERANCIA = 4         // por canal, sobre 255
    }

    private data class Objetivo(val tierra: Int, val texto: Int)

    // Hex de `EstiloMapaFactory` (Google): landscape y labels.text.fill de cada variante.
    private val objetivos = mapOf(
        TemaMapa.CLARO_DIA to Objetivo(0xF0F0F0, 0x171717),
        TemaMapa.CLARO_NOCHE to Objetivo(0xE8E8E8, 0x292929),
        TemaMapa.OSCURO_DIA to Objetivo(0x303030, 0xF5F5F5),
        TemaMapa.OSCURO_NOCHE to Objetivo(0x252525, 0xE8E8E8)
    )

    private fun filtro(t: TemaMapa) = EstilosTemaMapa.de(t).filtro!!
    private fun canal(c: Int, desplazamiento: Int) = (c shr desplazamiento) and 0xFF
    private fun luminancia(c: Int) =
        0.213 * canal(c, 16) + 0.715 * canal(c, 8) + 0.072 * canal(c, 0)
    private fun dispersion(c: Int): Int {
        val v = listOf(canal(c, 16), canal(c, 8), canal(c, 0))
        return v.max() - v.min()
    }

    private fun assertCercano(mensaje: String, esperado: Int, real: Int) {
        for (s in intArrayOf(16, 8, 0)) {
            assertTrue(
                "$mensaje: esperado #%06X, obtenido #%06X".format(esperado, real and 0xFFFFFF),
                abs(canal(esperado, s) - canal(real, s)) <= TOLERANCIA
            )
        }
    }

    @Test
    fun laTierraDeMapnikQuedaEnElGrisDeCadaEstilo() {
        objetivos.forEach { (tema, o) -> assertCercano("tierra $tema", o.tierra, filtro(tema).aplicar(TIERRA)) }
    }

    @Test
    fun lasEtiquetasQuedanEnElColorDeTextoDeCadaEstilo() {
        objetivos.forEach { (tema, o) -> assertCercano("texto $tema", o.texto, filtro(tema).aplicar(TEXTO)) }
    }

    @Test
    fun lasCallesSeDistinguenDeLaTierra() {
        TemaMapa.entries.forEach { tema ->
            val f = filtro(tema)
            val dif = abs(luminancia(f.aplicar(CALLE)) - luminancia(f.aplicar(TIERRA)))
            assertTrue("$tema: calles y tierra casi iguales (Δ=$dif)", dif >= 10.0)
        }
    }

    @Test
    fun parquesYAguaQuedanNeutros() {
        TemaMapa.entries.forEach { tema ->
            val f = filtro(tema)
            assertTrue("$tema: parque aún muy verde", dispersion(f.aplicar(PARQUE)) <= 12)
            assertTrue("$tema: agua aún muy azul", dispersion(f.aplicar(AGUA)) <= 12)
        }
    }

    @Test
    fun elFondoDeCargaCoincideConLaTierraFiltrada() {
        // Evita el destello de otro color mientras llegan las teselas.
        TemaMapa.entries.forEach { tema ->
            assertCercano("fondoCarga $tema", EstilosTemaMapa.de(tema).fondoCarga, filtro(tema).aplicar(TIERRA))
        }
    }

    @Test
    fun matrizNeutraEsLaIdentidad() {
        val identidad = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        assertArrayEquals(identidad, ParametrosFiltro(false, 1f, 1f, 0f).matriz(), 1e-4f)
    }

    @Test
    fun invertirDaElComplementoYSaturacionCeroDaGrises() {
        assertEquals(0xFFEFDFCF.toInt(), ParametrosFiltro(true, 1f, 1f, 0f).aplicar(0x102030))
        val gris = ParametrosFiltro(false, 0f, 1f, 0f).aplicar(0xC8FACC)
        assertEquals(0, dispersion(gris))
    }

    @Test
    fun escalaYDesplazamientoSeLimitanA0y255() {
        val f = ParametrosFiltro(false, 1f, 2f, 50f)
        assertEquals(0xFFFFFFFF.toInt(), f.aplicar(0xFFFFFF))
        assertEquals(0xFF323232.toInt(), f.aplicar(0x000000))
    }
}

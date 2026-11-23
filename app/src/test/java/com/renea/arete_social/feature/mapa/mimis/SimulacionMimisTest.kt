package com.renea.arete_social.feature.mapa.mimis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Mundo plano de prueba: la zona de paseo es un cuadrado de 80 m (de [origen]+10 a [origen]+90)
 * y se considera "visible" un cuadrado algo mayor. Mover [origen] equivale a mover el mapa.
 */
private class EntornoPlano(var origen: Float = 0f) : EntornoPaseo {
    override val velocidadBase = 5f
    override var bearingRad = 0f
    override val centro get() = Punto(origen + 50f, origen + 50f)
    override val separacionMinima = 3f

    override fun estaEnZona(x: Float, y: Float) =
        x in (origen + 10f)..(origen + 90f) && y in (origen + 10f)..(origen + 90f)

    override fun estaVisible(x: Float, y: Float) =
        x in (origen - 20f)..(origen + 120f) && y in (origen - 20f)..(origen + 120f)

    override fun puntoEnZona(random: Random) = Punto(
        origen + 10f + random.nextFloat() * 80f,
        origen + 10f + random.nextFloat() * 80f
    )
}

class SimulacionMimisTest {

    private val cuadro = 1f / 30f

    @Test
    fun `sincronizar crea y quita mimis segun los ids visibles`() {
        val simulacion = SimulacionMimis(Random(1))
        val entorno = EntornoPlano()

        simulacion.sincronizar(listOf("a", "b", "c"))
        simulacion.avanzar(cuadro, entorno)
        assertEquals(3, simulacion.cantidad)
        assertNotNull(simulacion.mimi("b"))

        simulacion.sincronizar(listOf("a", "c", "d"))
        assertNull(simulacion.mimi("b"))
        simulacion.avanzar(cuadro, entorno)
        assertEquals(3, simulacion.cantidad)
        assertNotNull(simulacion.mimi("d"))
    }

    @Test
    fun `un mimi que sigue visible conserva su identidad entre sincronizaciones`() {
        val simulacion = SimulacionMimis(Random(2))
        val entorno = EntornoPlano()
        simulacion.sincronizar(listOf("a"))
        simulacion.avanzar(cuadro, entorno)
        val antes = simulacion.mimi("a")

        simulacion.sincronizar(listOf("a", "b"))
        simulacion.avanzar(cuadro, entorno)

        assertTrue(antes === simulacion.mimi("a"))
    }

    @Test
    fun `los mimis nacen dentro de la zona de paseo`() {
        val simulacion = SimulacionMimis(Random(3))
        val entorno = EntornoPlano()
        simulacion.sincronizar((1..14).map { "p$it" })
        simulacion.avanzar(0f, entorno)

        (1..14).forEach {
            val m = simulacion.mimi("p$it")!!
            assertTrue(entorno.estaEnZona(m.x, m.y))
        }
    }

    @Test
    fun `siguen visibles aunque paseen mucho tiempo`() {
        val simulacion = SimulacionMimis(Random(4))
        val entorno = EntornoPlano()
        simulacion.sincronizar((1..14).map { "p$it" })

        // 10 minutos simulados a 30 cuadros por segundo.
        repeat(18_000) { simulacion.avanzar(cuadro, entorno) }

        (1..14).forEach {
            val m = simulacion.mimi("p$it")!!
            assertTrue("fuera de vista: ${m.x}, ${m.y}", entorno.estaVisible(m.x, m.y))
        }
    }

    @Test
    fun `los mimis se mueven con el tiempo`() {
        val simulacion = SimulacionMimis(Random(5))
        val entorno = EntornoPlano()
        simulacion.sincronizar(listOf("a"))
        simulacion.avanzar(0f, entorno)
        val m = simulacion.mimi("a")!!
        val x0 = m.x
        val y0 = m.y

        repeat(300) { simulacion.avanzar(cuadro, entorno) }

        assertTrue(m.x != x0 || m.y != y0)
    }

    @Test
    fun `si el mapa se mueve y el mimi queda fuera de pantalla reaparece en la nueva zona`() {
        val simulacion = SimulacionMimis(Random(6))
        val entorno = EntornoPlano(origen = 0f)
        simulacion.sincronizar(listOf("a"))
        simulacion.avanzar(cuadro, entorno)

        // El usuario arrastra el mapa muy lejos: la zona visible ahora está 1 km más allá.
        entorno.origen = 1000f
        simulacion.avanzar(cuadro, entorno)

        val m = simulacion.mimi("a")!!
        assertTrue("no se reubicó: ${m.x}, ${m.y}", entorno.estaVisible(m.x, m.y))
        assertEquals(0f, m.edadSegundos, 0.05f)
    }

    @Test
    fun `si el mapa se mueve poco el mimi se queda en su sitio del mapa`() {
        val simulacion = SimulacionMimis(Random(7))
        val entorno = EntornoPlano(origen = 0f)
        simulacion.sincronizar(listOf("a"))
        simulacion.avanzar(0f, entorno)
        val m = simulacion.mimi("a")!!
        val x0 = m.x
        val y0 = m.y

        // Un desplazamiento pequeño del mapa no debe teletransportarlo: sigue anclado al suelo.
        entorno.origen = 5f
        simulacion.avanzar(0f, entorno)

        assertEquals(x0, m.x, 0.001f)
        assertEquals(y0, m.y, 0.001f)
    }

    @Test
    fun `un salto de tiempo enorme no lo teletransporta fuera de la vista`() {
        val simulacion = SimulacionMimis(Random(8))
        val entorno = EntornoPlano()
        simulacion.sincronizar(listOf("a"))

        simulacion.avanzar(dtSegundos = 60f, entorno = entorno)

        val m = simulacion.mimi("a")!!
        assertTrue(entorno.estaVisible(m.x, m.y))
    }
}

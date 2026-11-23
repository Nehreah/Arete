package com.renea.arete_social.data.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GattFramerTest {

    private val macTest = "AA:BB:CC:DD:EE:01"

    @Before
    fun setup() {
        GattFramer.limpiarTodo()
    }

    @Test
    fun `ensamblar - mensaje de un solo paquete se reensambla directamente`() {
        val payloadOriginal = "Hola mesh!".toByteArray(Charsets.UTF_8)
        val frames = GattFramer.fragmentar(payloadOriginal, mtu = 512)

        assertEquals(1, frames.size)
        val resultado = GattFramer.ensamblar(macTest, frames[0])
        assertNotNull(resultado)
        assertArrayEquals(payloadOriginal, resultado)
    }

    @Test
    fun `ensamblar - mensaje multipartes ordenado se reensambla correctamente`() {
        val textoLargo = "A".repeat(500)
        val payloadOriginal = textoLargo.toByteArray(Charsets.UTF_8)
        // Con MTU=50 y HEADER=7, cada fragmento lleva max 43 bytes -> ~12 fragmentos
        val frames = GattFramer.fragmentar(payloadOriginal, mtu = 50)
        assertEquals(12, frames.size)

        var resultadoFinal: ByteArray? = null
        for (i in 0 until frames.size - 1) {
            val parcial = GattFramer.ensamblar(macTest, frames[i])
            assertNull("El fragmento intermedio $i no debe completar el mensaje", parcial)
        }

        resultadoFinal = GattFramer.ensamblar(macTest, frames.last())
        assertNotNull("El último fragmento debe completar el mensaje", resultadoFinal)
        assertArrayEquals(payloadOriginal, resultadoFinal)
    }

    @Test
    fun `ensamblar - mensaje multipartes desordenado se reensambla correctamente`() {
        val payloadOriginal = "Aréte Mesh Decentralized Social Network".toByteArray(Charsets.UTF_8)
        val frames = GattFramer.fragmentar(payloadOriginal, mtu = 20) // Fragmentos pequeños

        // Enviar primero el frame 0 (que abre la sesión), luego los demás en desorden
        val frame0 = frames[0]
        val restoDesordenado = frames.drop(1).shuffled()

        var resultado: ByteArray? = null
        val r0 = GattFramer.ensamblar(macTest, frame0)
        if (frames.size == 1) {
            assertNotNull(r0)
            return
        }

        for (f in restoDesordenado) {
            val r = GattFramer.ensamblar(macTest, f)
            if (r != null) {
                resultado = r
            }
        }

        assertNotNull("El mensaje debió completarse a pesar del desorden", resultado)
        assertArrayEquals(payloadOriginal, resultado)
    }

    @Test
    fun `ensamblar - fragmentos duplicados no corrompen el mensaje`() {
        val payloadOriginal = "Prueba de duplicacion de tramas en GATT".toByteArray(Charsets.UTF_8)
        val frames = GattFramer.fragmentar(payloadOriginal, mtu = 25)

        // Enviar frame 0, repetir frame 0, enviar frame 1, repetir frame 1...
        var resultado: ByteArray? = null
        for (frame in frames) {
            val r1 = GattFramer.ensamblar(macTest, frame)
            if (r1 != null) resultado = r1
            val r2 = GattFramer.ensamblar(macTest, frame) // duplicado intencional
            if (r2 != null) resultado = r2
        }

        assertNotNull("El mensaje debió completarse a pesar de los duplicados", resultado)
        assertArrayEquals(payloadOriginal, resultado)
    }

    @Test
    fun `ensamblar - frame menor a 7 bytes se rechaza como nulo`() {
        val frameInvalido = byteArrayOf(1, 2, 3)
        val resultado = GattFramer.ensamblar(macTest, frameInvalido)
        assertNull(resultado)
    }

    @Test
    fun `limpiarSesion - descarta sesion en curso`() {
        val payloadOriginal = "Mensaje cancelado a mitad de camino".toByteArray(Charsets.UTF_8)
        val frames = GattFramer.fragmentar(payloadOriginal, mtu = 20)
        if (frames.size > 1) {
            GattFramer.ensamblar(macTest, frames[0])
            GattFramer.limpiarSesion(macTest)
            // Enviar el resto sin el 0 no debe reensamblar nada
            for (f in frames.drop(1)) {
                val r = GattFramer.ensamblar(macTest, f)
                assertNull(r)
            }
        }
    }
}

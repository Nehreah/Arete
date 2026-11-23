package com.renea.arete_social.feature.mapa.mimis

import com.renea.arete_social.domain.model.Dispositivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorMimisVisiblesTest {

    private fun persona(id: String, distancia: Double? = null, usuarioId: String? = "u-$id") = Dispositivo(
        id = id,
        nombre = "Persona $id",
        señal = null,
        ultimaConexion = 0L,
        usuarioId = usuarioId,
        distanciaMetros = distancia
    )

    @Test
    fun `nunca muestra mas de 14 mimis aunque haya 40 personas cerca`() {
        val personas = (1..40).map { persona("p$it", distancia = it.toDouble()) }

        val visibles = SelectorMimisVisibles().seleccionar(personas)

        assertEquals(MAX_MIMIS_EN_MAPA, visibles.size)
        assertEquals(14, visibles.size)
    }

    @Test
    fun `con pocas personas muestra a todas`() {
        val personas = listOf(persona("a", 10.0), persona("b", 20.0))

        assertEquals(2, SelectorMimisVisibles().seleccionar(personas).size)
    }

    @Test
    fun `llena los cupos con las personas mas cercanas primero`() {
        val personas = listOf(
            persona("lejos", 300.0),
            persona("sin-distancia", null),
            persona("cerca", 5.0),
            persona("medio", 80.0)
        )

        val ids = SelectorMimisVisibles(maximo = 3).seleccionar(personas).map { it.id }

        assertEquals(listOf("cerca", "medio", "lejos"), ids)
    }

    @Test
    fun `conserva los mimis que ya estaban aunque aparezca alguien mas cercano`() {
        val selector = SelectorMimisVisibles(maximo = 2)
        val primera = selector.seleccionar(listOf(persona("a", 50.0), persona("b", 60.0)))
        assertEquals(listOf("a", "b"), primera.map { it.id })

        val segunda = selector.seleccionar(
            personasCercanas = listOf(persona("a", 50.0), persona("b", 60.0), persona("nuevo", 1.0)),
            idsVisiblesActuales = primera.map { it.id }
        )

        assertEquals(listOf("a", "b"), segunda.map { it.id })
    }

    @Test
    fun `cuando alguien se aleja su cupo pasa a la siguiente persona`() {
        val selector = SelectorMimisVisibles(maximo = 2)
        val actuales = listOf("a", "b")

        val resultado = selector.seleccionar(
            personasCercanas = listOf(persona("b", 60.0), persona("c", 70.0), persona("d", 90.0)),
            idsVisiblesActuales = actuales
        )

        assertEquals(listOf("b", "c"), resultado.map { it.id })
    }

    @Test
    fun `no incluye al propio usuario ni repite dispositivos`() {
        val personas = listOf(
            persona("yo", 0.0, usuarioId = "mi-id"),
            persona("a", 10.0),
            persona("a", 12.0)
        )

        val resultado = SelectorMimisVisibles().seleccionar(personas, usuarioIdPropio = "mi-id")

        assertEquals(listOf("a"), resultado.map { it.id })
        assertFalse(resultado.any { it.usuarioId == "mi-id" })
    }

    @Test
    fun `lista vacia no produce mimis`() {
        assertTrue(SelectorMimisVisibles().seleccionar(emptyList()).isEmpty())
    }
}

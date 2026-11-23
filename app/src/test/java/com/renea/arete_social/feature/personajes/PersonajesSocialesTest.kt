package com.renea.arete_social.feature.personajes

import com.renea.arete_social.feature.personajes.data.AjustesPersonajesSociales
import com.renea.arete_social.feature.personajes.logic.ReglasAfinidad
import com.renea.arete_social.feature.personajes.logic.TipoActividad
import com.renea.arete_social.feature.personajes.logic.ValidadorTextoSocial
import com.renea.arete_social.feature.personajes.model.NivelVinculo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.Temperamento
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de caracterización para la Fase 1 del Mundo Social de Personajes:
 * - Compatibilidad y afinidad de temperamentos.
 * - Ciclo de vida y límites diarios de [Relacion].
 * - Validación y saneamiento de textos sociales ([ValidadorTextoSocial]).
 * - Estado seguro del Feature Flag ([AjustesPersonajesSociales]).
 * - Compatibilidad de serialización y migración a [PetEntity] v7.
 */
class PersonajesSocialesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── 1. Temperamentos y Afinidad ──────────────────────────────────────────

    @Test
    fun `matriz de compatibilidad es simetrica para todos los pares`() {
        for (t1 in Temperamento.values()) {
            for (t2 in Temperamento.values()) {
                val c1 = ReglasAfinidad.compatibilidad(t1, t2)
                val c2 = ReglasAfinidad.compatibilidad(t2, t1)
                assertEquals("La compatibilidad entre $t1 y $t2 debe ser simétrica", c1, c2, 0.0001f)
            }
        }
    }

    @Test
    fun `afinidad de actividades bonifica a los temperamentos afines`() {
        assertTrue(
            "El enérgico debe tener alta afinidad con el gimnasio",
            ReglasAfinidad.afinidadActividad(Temperamento.ENERGETICO, TipoActividad.ENTRENAR_GIMNASIO) > 1.2f
        )
        assertTrue(
            "El artista debe tener alta afinidad con la creación de arte",
            ReglasAfinidad.afinidadActividad(Temperamento.ARTISTA, TipoActividad.CREAR_ARTE) > 1.2f
        )
        assertTrue(
            "El glotón debe tener alta afinidad con comer juntos",
            ReglasAfinidad.afinidadActividad(Temperamento.GLOTON, TipoActividad.COMER_JUNTOS) > 1.2f
        )
        assertTrue(
            "El sociable debe tener alta afinidad con charlar en cafetería",
            ReglasAfinidad.afinidadActividad(Temperamento.SOCIABLE, TipoActividad.CHARLAR_CAFETERIA) > 1.2f
        )
    }

    // ── 2. Relaciones y Mitigación de Farming ─────────────────────────────────

    @Test
    fun `interaccion aumenta confianza y avanza niveles de vinculo`() {
        var relacion = Relacion(
            otroPersonajeId = "p-amigo-1",
            otroOwnerId = "u-amigo-1",
            nombreOtro = "MichiAmigo",
            confianza = 18 // Casi nivel COMPAÑERO (umbral 20)
        )

        assertEquals(NivelVinculo.CONOCIDO, relacion.nivelVinculo)

        val resultado = ReglasAfinidad.aplicarInteraccion(
            relacion = relacion,
            actividad = TipoActividad.CHARLAR_CAFETERIA,
            miTemperamento = Temperamento.SOCIABLE,
            otroTemperamento = Temperamento.SOCIABLE
        )

        assertTrue("La interacción debe ser exitosa", resultado.exito)
        assertTrue("Los puntos ganados deben ser positivos", resultado.puntosConfianzaGanados > 0)
        assertTrue("Debe haber subido de nivel al pasar 20 puntos", resultado.subioDeNivel)
        assertEquals(NivelVinculo.COMPANERO, resultado.relacionActualizada.nivelVinculo)
        assertEquals(1, resultado.relacionActualizada.interaccionesHoy)
        assertEquals(1, resultado.relacionActualizada.interaccionesTotales)
    }

    @Test
    fun `respeta estrictamente el limite de 5 interacciones diarias por vinculo`() {
        var relacion = Relacion(
            otroPersonajeId = "p-amigo-2",
            otroOwnerId = "u-amigo-2",
            nombreOtro = "Pelusa"
        )

        val ahora = System.currentTimeMillis()

        // Ejecutar 5 interacciones legítimas
        repeat(5) {
            val res = ReglasAfinidad.aplicarInteraccion(
                relacion = relacion,
                actividad = TipoActividad.PASEAR_PARQUE,
                miTemperamento = Temperamento.CURIOSO,
                otroTemperamento = Temperamento.TRANQUILO,
                ahoraMillis = ahora
            )
            assertTrue("Las primeras 5 interacciones deben ser permitidas", res.exito)
            relacion = res.relacionActualizada
        }

        assertEquals(5, relacion.interaccionesHoy)
        assertFalse(relacion.puedeInteractuarHoy)

        // Intento 6 en el mismo día debe ser rechazado
        val intentoAbusivo = ReglasAfinidad.aplicarInteraccion(
            relacion = relacion,
            actividad = TipoActividad.PASEAR_PARQUE,
            miTemperamento = Temperamento.CURIOSO,
            otroTemperamento = Temperamento.TRANQUILO,
            ahoraMillis = ahora
        )

        assertFalse("La 6ta interacción en el mismo día debe ser rechazada", intentoAbusivo.exito)
        assertNotNull("Debe indicar el motivo de rechazo", intentoAbusivo.motivoRechazo)

        // Avanzar el reloj al día siguiente (después de las 4:00 AM)
        val diaSiguiente = ahora + (25 * 3600 * 1000L)
        val intentoDiaSiguiente = ReglasAfinidad.aplicarInteraccion(
            relacion = relacion,
            actividad = TipoActividad.PASEAR_PARQUE,
            miTemperamento = Temperamento.CURIOSO,
            otroTemperamento = Temperamento.TRANQUILO,
            ahoraMillis = diaSiguiente
        )

        assertTrue("Al día siguiente las interacciones se resetean y se permiten", intentoDiaSiguiente.exito)
        assertEquals(1, intentoDiaSiguiente.relacionActualizada.interaccionesHoy)
        assertEquals(6, intentoDiaSiguiente.relacionActualizada.interaccionesTotales)
    }

    // ── 3. Validación de Textos Sociales ──────────────────────────────────────

    @Test
    fun `validador de texto bloquea intentos de phishing y enlaces web`() {
        val textosMaliciosos = listOf(
            "Visita mi casa en https://antired-fake.com para ver regalos",
            "Entra en http://inseguro.org",
            "Mírame en www.streamingsocial.xyz",
            "Revisa mi perfil en telegram.me/canal",
            "Encuéntrame en bit.ly/acceso"
        )

        for (t in textosMaliciosos) {
            val res = ValidadorTextoSocial.validar(t)
            assertFalse("El texto con URL debe ser rechazado: '$t'", res.esValido)
        }

        val textoLegitimo = "¡Qué gran tarde en el parque con Michi! Jugamos mucho."
        val resLegitimo = ValidadorTextoSocial.validar(textoLegitimo)
        assertTrue("Texto social plano debe ser válido", resLegitimo.esValido)
    }

    @Test
    fun `validador de texto impone longitud maxima de 120 caracteres`() {
        val textoValido120 = "A".repeat(120)
        assertTrue(ValidadorTextoSocial.validar(textoValido120).esValido)

        val textoLargo121 = "A".repeat(121)
        assertFalse(ValidadorTextoSocial.validar(textoLargo121).esValido)

        assertFalse("Texto en blanco debe ser inválido", ValidadorTextoSocial.validar("   ").esValido)
        assertFalse("Texto nulo debe ser inválido", ValidadorTextoSocial.validar(null).esValido)
    }

    // ── 4. Feature Flag ───────────────────────────────────────────────────────

    @Test
    fun `feature flag del mundo social permanece apagado por defecto`() {
        val fakePrefs = FakeSharedPreferences()
        assertFalse(
            "Por defecto en la Fase 1 el mundo social debe estar apagado",
            AjustesPersonajesSociales.estaHabilitado(prefsOverride = fakePrefs)
        )

        AjustesPersonajesSociales.definirHabilitado(habilitado = true, prefsOverride = fakePrefs)
        assertTrue(AjustesPersonajesSociales.estaHabilitado(prefsOverride = fakePrefs))
    }

    // ── 5. Migración de PetEntity a v7 ────────────────────────────────────────

    @Test
    fun `deserializacion de JSON v6 asigna valores por defecto v7 sin perder datos`() {
        // JSON simulado de una mascota guardada en v6
        val jsonV6 = """
            {
                "ownerId": "usuario-123",
                "name": "Michi",
                "petType": "GATO",
                "stage": "ADULTO",
                "hunger": 75,
                "happiness": 85,
                "energy": 90,
                "hygiene": 80,
                "health": 100,
                "schemaVersion": 6
            }
        """.trimIndent()

        val pet = json.decodeFromString<PetEntity>(jsonV6)

        assertEquals("usuario-123", pet.ownerId)
        assertEquals("Michi", pet.name)
        assertEquals(75, pet.hunger)
        // Campos v7 por defecto:
        assertEquals("", pet.personajeId)
        assertEquals(Temperamento.CURIOSO, pet.temperamento)
        assertEquals(6, pet.schemaVersion)
    }
}

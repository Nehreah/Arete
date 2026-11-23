package com.renea.arete_social.gui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ResolutorCicloDiaTest {

    @Test
    fun `resolver - 06 59 es NOCHE`() {
        val hora = LocalTime.of(6, 59)
        assertEquals(CicloDia.NOCHE, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver - 07 00 es DIA`() {
        val hora = LocalTime.of(7, 0)
        assertEquals(CicloDia.DIA, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver - 12 00 mediodia es DIA`() {
        val hora = LocalTime.of(12, 0)
        assertEquals(CicloDia.DIA, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver - 18 59 es DIA`() {
        val hora = LocalTime.of(18, 59)
        assertEquals(CicloDia.DIA, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver - 19 00 es NOCHE`() {
        val hora = LocalTime.of(19, 0)
        assertEquals(CicloDia.NOCHE, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver - 00 00 medianoche es NOCHE`() {
        val hora = LocalTime.of(0, 0)
        assertEquals(CicloDia.NOCHE, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver - 23 59 es NOCHE`() {
        val hora = LocalTime.of(23, 59)
        assertEquals(CicloDia.NOCHE, ResolutorCicloDia.resolver(hora))
    }

    @Test
    fun `resolver con Clock inyectable resuelve correctamente`() {
        val zone = ZoneId.of("UTC")
        val instantDia = LocalDateTime.of(2026, 10, 18, 14, 30).atZone(zone).toInstant()
        val clockDia = Clock.fixed(instantDia, zone)
        assertEquals(CicloDia.DIA, ResolutorCicloDia.resolver(clockDia))

        val instantNoche = LocalDateTime.of(2026, 10, 18, 22, 15).atZone(zone).toInstant()
        val clockNoche = Clock.fixed(instantNoche, zone)
        assertEquals(CicloDia.NOCHE, ResolutorCicloDia.resolver(clockNoche))
    }

    @Test
    fun `duracionHastaProximoCruce desde 06 59 59 es 1 segundo hacia DIA (07 00)`() {
        val hoy = LocalDate.of(2026, 10, 18)
        val ahora = hoy.atTime(6, 59, 59)
        val duracion = ResolutorCicloDia.duracionHastaProximoCruce(ahora)
        assertEquals(Duration.ofSeconds(1), duracion)
    }

    @Test
    fun `duracionHastaProximoCruce desde 07 00 00 es 12 horas hacia NOCHE (19 00)`() {
        val hoy = LocalDate.of(2026, 10, 18)
        val ahora = hoy.atTime(7, 0, 0)
        val duracion = ResolutorCicloDia.duracionHastaProximoCruce(ahora)
        assertEquals(Duration.ofHours(12), duracion)
    }

    @Test
    fun `duracionHastaProximoCruce desde 18 59 59 es 1 segundo hacia NOCHE (19 00)`() {
        val hoy = LocalDate.of(2026, 10, 18)
        val ahora = hoy.atTime(18, 59, 59)
        val duracion = ResolutorCicloDia.duracionHastaProximoCruce(ahora)
        assertEquals(Duration.ofSeconds(1), duracion)
    }

    @Test
    fun `duracionHastaProximoCruce desde 19 00 00 es 12 horas hacia DIA de manana (07 00)`() {
        val hoy = LocalDate.of(2026, 10, 18)
        val ahora = hoy.atTime(19, 0, 0)
        val duracion = ResolutorCicloDia.duracionHastaProximoCruce(ahora)
        assertEquals(Duration.ofHours(12), duracion)
    }

    @Test
    fun `duracionHastaProximoCruce desde medianoche es 7 horas hacia DIA (07 00)`() {
        val hoy = LocalDate.of(2026, 10, 18)
        val ahora = hoy.atTime(0, 0, 0)
        val duracion = ResolutorCicloDia.duracionHastaProximoCruce(ahora)
        assertEquals(Duration.ofHours(7), duracion)
    }
}

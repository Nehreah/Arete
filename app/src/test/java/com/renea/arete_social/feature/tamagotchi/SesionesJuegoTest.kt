package com.renea.arete_social.feature.tamagotchi

import com.renea.arete_social.feature.miausnake.integration.Fixtures.HORA
import com.renea.arete_social.feature.miausnake.integration.Fixtures.adulta
import com.renea.arete_social.feature.miausnake.integration.Fixtures.t0
import com.renea.arete_social.feature.tamagotchi.logic.SesionesJuego
import com.renea.arete_social.feature.tamagotchi.logic.ganarXp
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.model.AperturaSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.EstadoSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.LibroSesionesJuego
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SesionesJuegoTest {
    private val vacio = LibroSesionesJuego()
    private fun abierta(id: String = "s1") = SesionesJuego.abrir(adulta(), vacio, id, t0).libro

    @Test fun `abrir registra una sesion abierta`() {
        val r = SesionesJuego.abrir(adulta(), vacio, "s1", t0)
        assertEquals(AperturaSesionJuego.Abierta, r.resultado)
        assertEquals(EstadoSesionJuego.ABIERTA, r.libro.buscar("s1")?.estado)
    }

    @Test fun `abrir rechaza si Michi no puede jugar y no crea sesion`() {
        val casos = listOf(
            adulta { copy(isSleeping = true) } to ResultadoCuidado.DORMIDA,
            adulta { copy(energy = 5) } to ResultadoCuidado.POCA_ENERGIA,
            adulta { copy(hunger = 5) } to ResultadoCuidado.TIENE_HAMBRE,
            adulta().jugar(t0).pet to ResultadoCuidado.EN_ESPERA
        )
        for ((pet, motivo) in casos) {
            val r = SesionesJuego.abrir(pet, vacio, "s1", t0 + 60_000)
            assertEquals(AperturaSesionJuego.Rechazada(motivo), r.resultado)
            assertEquals(vacio, r.libro)
        }
    }

    @Test fun `un id repetido nunca se reutiliza`() {
        val libro = abierta()
        val r = SesionesJuego.abrir(adulta(), libro, "s1", t0)
        assertEquals(AperturaSesionJuego.IdRepetido, r.resultado)
        assertEquals(libro, r.libro)
    }

    @Test fun `abrir otra sesion descarta la anterior abierta`() {
        val r = SesionesJuego.abrir(adulta(), abierta("a"), "b", t0 + 1)
        assertEquals(EstadoSesionJuego.DESCARTADA, r.libro.buscar("a")?.estado)
        assertEquals(1, r.libro.sesiones.count { it.estado == EstadoSesionJuego.ABIERTA })
    }

    @Test fun `cerrar aplica jugar exactamente una vez`() {
        val base = adulta()
        val c = SesionesJuego.cerrar(base, abierta(), "s1", RecompensaJuego(true), t0)
        assertEquals(CierreSesionJuego.Aplicado(0), c.resultado)
        assertEquals(base.jugar(t0).pet, c.pet)
        assertEquals(EstadoSesionJuego.RECLAMADA, c.libro.buscar("s1")?.estado)
        val otra = SesionesJuego.cerrar(c.pet, c.libro, "s1", RecompensaJuego(true), t0)
        assertEquals(CierreSesionJuego.YaCerrada, otra.resultado)
        assertEquals(c.pet, otra.pet)
        assertEquals(1, otra.pet.vecesJugadoHoy)
    }

    @Test fun `el xp extra se suma a la actividad`() {
        val base = adulta()
        val c = SesionesJuego.cerrar(base, abierta(), "s1", RecompensaJuego(true, 10), t0)
        assertEquals(CierreSesionJuego.Aplicado(10), c.resultado)
        assertEquals(base.jugar(t0).pet.ganarXp(10, t0), c.pet)
    }

    @Test fun `el xp extra tiene tope de seguridad`() {
        val c = SesionesJuego.cerrar(adulta(), abierta(), "s1", RecompensaJuego(true, 9_999), t0)
        assertEquals(CierreSesionJuego.Aplicado(SesionesJuego.XP_EXTRA_MAXIMO), c.resultado)
    }

    @Test fun `pasado el limite diario no hay xp extra`() {
        val dentro = SesionesJuego.cerrar(adulta { copy(vecesJugadoHoy = PetBalance.MAX_PLAYS_PER_DAY - 1) }, abierta(), "s1", RecompensaJuego(true, 10), t0)
        assertEquals(CierreSesionJuego.Aplicado(10), dentro.resultado)
        val fuera = SesionesJuego.cerrar(adulta { copy(vecesJugadoHoy = PetBalance.MAX_PLAYS_PER_DAY) }, abierta(), "s1", RecompensaJuego(true, 10), t0)
        assertEquals(CierreSesionJuego.Aplicado(0), fuera.resultado)
    }

    @Test fun `cerrar sin recompensa descarta y no toca a la mascota`() {
        val base = adulta()
        val c = SesionesJuego.cerrar(base, abierta(), "s1", RecompensaJuego.Ninguna, t0)
        assertEquals(CierreSesionJuego.Descartado, c.resultado)
        assertEquals(base, c.pet)
        assertEquals(EstadoSesionJuego.DESCARTADA, c.libro.buscar("s1")?.estado)
    }

    @Test fun `sesion desconocida no se puede reclamar`() {
        val base = adulta()
        val c = SesionesJuego.cerrar(base, vacio, "fantasma", RecompensaJuego(true, 10), t0)
        assertEquals(CierreSesionJuego.SesionDesconocida, c.resultado)
        assertEquals(base, c.pet)
    }

    @Test fun `si Michi ya no puede jugar al cerrar no se aplica nada`() {
        val dormida = adulta { copy(isSleeping = true) }
        val c = SesionesJuego.cerrar(dormida, abierta(), "s1", RecompensaJuego(true, 10), t0)
        assertEquals(CierreSesionJuego.Rechazado(ResultadoCuidado.DORMIDA), c.resultado)
        assertEquals(dormida, c.pet)
        assertEquals(CierreSesionJuego.YaCerrada, SesionesJuego.cerrar(c.pet, c.libro, "s1", RecompensaJuego(true), t0).resultado)
    }

    @Test fun `una sesion abierta caduca`() {
        val base = adulta()
        val c = SesionesJuego.cerrar(base, abierta(), "s1", RecompensaJuego(true), t0 + 25 * HORA)
        assertEquals(CierreSesionJuego.Descartado, c.resultado)
        assertEquals(base, c.pet)
    }

    @Test fun `el libro esta acotado y una sesion olvidada no se puede reclamar`() {
        var libro = vacio
        var pet = adulta()
        val c = SesionesJuego.cerrar(pet, SesionesJuego.abrir(pet, libro, "vieja", t0).libro, "vieja", RecompensaJuego(true), t0)
        libro = c.libro; pet = c.pet
        repeat(SesionesJuego.MAX_SESIONES + 6) { i -> libro = SesionesJuego.abrir(adulta(), libro, "n$i", t0 + 1 + i).libro }
        assertEquals(SesionesJuego.MAX_SESIONES, libro.sesiones.size)
        assertEquals(1, libro.sesiones.count { it.estado == EstadoSesionJuego.ABIERTA })
        val otra = SesionesJuego.cerrar(pet, libro, "vieja", RecompensaJuego(true), t0 + 100)
        assertEquals(CierreSesionJuego.SesionDesconocida, otra.resultado)
        assertEquals(pet, otra.pet)
    }

    @Test fun `el libro sobrevive a guardar y leer`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val libro = SesionesJuego.cerrar(adulta(), abierta(), "s1", RecompensaJuego(true), t0).libro
        assertEquals(libro, json.decodeFromString<LibroSesionesJuego>(json.encodeToString(libro)))
        assertTrue(libro.sesiones.single().estado == EstadoSesionJuego.RECLAMADA)
    }
}

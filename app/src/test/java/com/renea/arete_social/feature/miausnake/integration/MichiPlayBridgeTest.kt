package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.miausnake.core.Replay
import com.renea.arete_social.feature.miausnake.integration.Fixtures.HORA
import com.renea.arete_social.feature.miausnake.integration.Fixtures.adulta
import com.renea.arete_social.feature.miausnake.integration.Fixtures.t0
import com.renea.arete_social.feature.tamagotchi.logic.ganarXp
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.model.EstadoSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MichiPlayBridgeTest {
    private fun repo() = RepoSesionesFake(adulta(), t0)
    private fun puente(r: RepoSesionesFake) = MichiPlayBridgeLocal(r, "u")
    private suspend fun MichiPlayBridgeLocal.lista(): String = (abrir() as AperturaSnake.Lista).sesionId

    @Test fun `abrir no consume nada`() = runTest {
        val r = repo(); val antes = r.pet
        puente(r).lista()
        assertEquals(antes, r.pet)
        assertEquals(1, r.libro.sesiones.count { it.estado == EstadoSesionJuego.ABIERTA })
    }

    @Test fun `si Michi no puede jugar no hay sesion y se explica`() = runTest {
        val r = RepoSesionesFake(adulta { copy(isSleeping = true) }, t0)
        assertEquals(AperturaSnake.NoDisponible(ResultadoCuidado.DORMIDA), puente(r).abrir())
        assertTrue(r.libro.sesiones.isEmpty())
    }

    @Test fun `victoria da la actividad de Automatico mas el extra de meta`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista()
        val c = b.cerrar(id, SnakeTestGames.victoria())
        assertEquals(CierreSnake.Recompensado(SnakeBalance.XP_WIN), c)
        assertEquals(adulta().jugar(t0).pet.ganarXp(SnakeBalance.XP_WIN, t0), r.pet)
        assertEquals(0, r.playLlamadas) // no pasa por el camino Automatico
    }

    @Test fun `choque con bocados suficientes iguala exactamente a Automatico`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista()
        val c = b.cerrar(id, SnakeTestGames.choque(SnakeBalance.MIN_BITES until SnakeBalance.BITES_FOR_EFFORT_BONUS))
        assertEquals(CierreSnake.Recompensado(0), c)
        assertEquals(adulta().jugar(t0).pet, r.pet)
    }

    @Test fun `partida larga con choque da el extra de esfuerzo`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista()
        val c = b.cerrar(id, SnakeTestGames.choque(SnakeBalance.BITES_FOR_EFFORT_BONUS..40))
        assertEquals(CierreSnake.Recompensado(SnakeBalance.XP_EFFORT), c)
    }

    @Test fun `partida muy corta no cuenta ni inicia espera`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista(); val antes = r.pet
        assertEquals(CierreSnake.PartidaCorta, b.cerrar(id, SnakeTestGames.choque(0 until SnakeBalance.MIN_BITES)))
        assertEquals(antes, r.pet)
        assertTrue(b.abrir() is AperturaSnake.Lista) // sin cooldown: se puede volver a jugar
    }

    @Test fun `abandonar no da recompensa ni coste`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista(); val antes = r.pet
        assertEquals(CierreSnake.Abandonada, b.cerrar(id, ResultadoSnake.Abandonada))
        assertEquals(antes, r.pet)
        assertEquals(EstadoSesionJuego.DESCARTADA, r.libro.buscar(id)?.estado)
    }

    @Test fun `una repeticion que no cuadra no da recompensa`() = runTest {
        val v = SnakeTestGames.victoria()
        val falsas = listOf(
            ResultadoSnake.Terminada(v.level, v.replay, v.score + 10, v.bites, true),
            ResultadoSnake.Terminada(v.level, Replay("otro", 1, v.replay.rulesVersion, v.replay.seed, v.replay.inputs), v.score, v.bites, true),
            ResultadoSnake.Terminada(v.level, v.replay.copy(inputs = emptyList()), v.score, v.bites, true)
        )
        for (f in falsas) {
            val r = repo(); val b = puente(r); val id = b.lista(); val antes = r.pet
            assertEquals(CierreSnake.RepeticionInvalida, b.cerrar(id, f))
            assertEquals(antes, r.pet)
        }
    }

    @Test fun `doble toque secuencial devuelve lo mismo y aplica una vez`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista(); val v = SnakeTestGames.victoria()
        val a = b.cerrar(id, v); val c = b.cerrar(id, v)
        assertEquals(a, c)
        assertEquals(1, r.pet.vecesJugadoHoy)
        assertEquals(1, r.cerrarLlamadas)
    }

    @Test fun `doble toque concurrente aplica una sola vez`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista(); val v = SnakeTestGames.victoria()
        val res = coroutineScope { List(10) { async { b.cerrar(id, v) } }.awaitAll() }
        assertEquals(1, res.toSet().size)
        assertEquals(1, r.pet.vecesJugadoHoy)
    }

    @Test fun `dos puentes cerrando la misma sesion a la vez - solo uno cobra`() = runTest {
        val r = repo(); val id = puente(r).lista(); val v = SnakeTestGames.victoria()
        val res = coroutineScope { List(2) { async { puente(r).cerrar(id, v) } }.awaitAll() }
        assertEquals(1, res.count { it is CierreSnake.Recompensado })
        assertEquals(1, res.count { it == CierreSnake.YaContada })
        assertEquals(1, r.pet.vecesJugadoHoy)
    }

    @Test fun `tras la recompensa rige la espera de Automatico`() = runTest {
        val r = repo(); val b = puente(r); b.cerrar(b.lista(), SnakeTestGames.victoria())
        r.ahora = t0 + HORA
        assertEquals(AperturaSnake.NoDisponible(ResultadoCuidado.EN_ESPERA), b.abrir())
        r.ahora = t0 + 3 * HORA + 1
        assertTrue(b.abrir() is AperturaSnake.Lista)
    }

    @Test fun `si Michi se duerme mientras se juega no se aplica nada`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista()
        r.forzar(r.pet.copy(isSleeping = true)); val antes = r.pet
        assertEquals(CierreSnake.MichiNoPudo(ResultadoCuidado.DORMIDA), b.cerrar(id, SnakeTestGames.victoria()))
        assertEquals(antes, r.pet)
    }

    @Test fun `error de carga se informa y Automatico nunca se ejecuta`() = runTest {
        val r = repo(); r.fallarAbrir = java.io.IOException("disco")
        assertTrue(puente(r).abrir() is AperturaSnake.Error)
        assertEquals(0, r.playLlamadas)
        assertEquals(adulta(), r.pet)
    }

    @Test fun `un fallo al guardar se puede reintentar sin duplicar`() = runTest {
        val r = repo(); val b = puente(r); val id = b.lista(); val v = SnakeTestGames.victoria()
        r.fallosCierrePendientes = 1
        assertTrue(b.cerrar(id, v) is CierreSnake.Error)
        assertEquals(EstadoSesionJuego.ABIERTA, r.libro.buscar(id)?.estado)
        assertTrue(b.cerrar(id, v) is CierreSnake.Recompensado)
        assertEquals(1, r.pet.vecesJugadoHoy)
    }

    @Test fun `un repositorio sin soporte da error y no aplica nada`() = runTest {
        val r = repo(); r.soportaSesiones = false
        assertTrue(puente(r).abrir() is AperturaSnake.Error)
        assertEquals(adulta(), r.pet)
    }
}

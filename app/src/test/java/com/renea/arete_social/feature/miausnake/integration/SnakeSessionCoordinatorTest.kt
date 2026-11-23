package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnakeSessionCoordinatorTest {
    private class Puente : MichiPlayBridge {
        val aperturas = ArrayDeque<AperturaSnake>()
        var espera: CompletableDeferred<Unit>? = null
        var abrirLlamadas = 0
        val cierres = mutableListOf<Pair<String, ResultadoSnake>>()
        var siguienteCierre: CierreSnake = CierreSnake.Recompensado(0)
        override suspend fun abrir(): AperturaSnake {
            abrirLlamadas++
            espera?.await()
            return aperturas.removeFirst()
        }
        override suspend fun cerrar(sesionId: String, resultado: ResultadoSnake): CierreSnake {
            cierres += sesionId to resultado
            return siguienteCierre
        }
    }

    private val v = SnakeTestGames.victoria()
    private fun montar(p: Puente) = SnakeSessionCoordinator(p, TestScope(UnconfinedTestDispatcher()))

    @Test fun `cancelar antes de jugar no abre ni cierra nada`() {
        val p = Puente(); val c = montar(p)
        c.volver()
        assertEquals(SnakeSesionUi.Inactiva, c.estado.value)
        assertEquals(0, p.abrirLlamadas); assertTrue(p.cierres.isEmpty())
    }

    @Test fun `jugar con Michi abre sesion y juega`() {
        val p = Puente().apply { aperturas += AperturaSnake.Lista("s1") }; val c = montar(p)
        c.jugarConMichi()
        assertEquals(SnakeSesionUi.Jugando("s1", 1), c.estado.value)
    }

    @Test fun `doble toque mientras se abre solo abre una vez`() {
        val p = Puente().apply { espera = CompletableDeferred(); aperturas += AperturaSnake.Lista("s1") }
        val c = montar(p)
        c.jugarConMichi(); c.jugarConMichi(); c.jugarPractica()
        assertEquals(SnakeSesionUi.Abriendo, c.estado.value)
        assertEquals(1, p.abrirLlamadas)
        p.espera!!.complete(Unit)
        assertEquals(SnakeSesionUi.Jugando("s1", 1), c.estado.value)
    }

    @Test fun `si Michi no puede jugar se explica y la practica no toca a Michi`() {
        val p = Puente().apply { aperturas += AperturaSnake.NoDisponible(ResultadoCuidado.EN_ESPERA) }; val c = montar(p)
        c.jugarConMichi()
        assertEquals(SnakeSesionUi.NoDisponible(ResultadoCuidado.EN_ESPERA), c.estado.value)
        c.jugarPractica()
        assertEquals(SnakeSesionUi.Jugando(null, 1), c.estado.value)
        c.partidaTerminada(v)
        assertEquals(SnakeSesionUi.Terminada(null, 1), c.estado.value)
        assertTrue(p.cierres.isEmpty())
    }

    @Test fun `error de carga se muestra y no cierra ni abre otra cosa`() {
        val p = Puente().apply { aperturas += AperturaSnake.Error(IllegalStateException("x")) }; val c = montar(p)
        c.jugarConMichi()
        assertTrue(c.estado.value is SnakeSesionUi.ErrorCarga)
        c.volver()
        assertEquals(SnakeSesionUi.Inactiva, c.estado.value)
        c.errorDeCarga(RuntimeException("mapa"))
        assertTrue(c.estado.value is SnakeSesionUi.ErrorCarga)
        assertTrue(p.cierres.isEmpty())
    }

    @Test fun `terminar envia el resultado una sola vez`() {
        val p = Puente().apply { aperturas += AperturaSnake.Lista("s1") }; val c = montar(p)
        c.jugarConMichi(); c.partidaTerminada(v); c.partidaTerminada(v)
        assertEquals(1, p.cierres.size)
        assertEquals(SnakeSesionUi.Terminada(CierreSnake.Recompensado(0), 1), c.estado.value)
    }

    @Test fun `un cierre fallido se reintenta con el mismo resultado`() {
        val p = Puente().apply { aperturas += AperturaSnake.Lista("s1") }; val c = montar(p)
        val err = CierreSnake.Error(RuntimeException("disco")); p.siguienteCierre = err
        c.jugarConMichi(); c.partidaTerminada(v)
        assertEquals(SnakeSesionUi.Terminada(err, 1), c.estado.value)
        p.siguienteCierre = CierreSnake.Recompensado(10)
        c.reintentarCierre()
        assertEquals(SnakeSesionUi.Terminada(CierreSnake.Recompensado(10), 1), c.estado.value)
        assertEquals(2, p.cierres.size)
        assertEquals(p.cierres[0], p.cierres[1])
    }

    @Test fun `abandonar a mitad cierra la sesion sin recompensa`() {
        val p = Puente().apply { aperturas += AperturaSnake.Lista("s1") }; val c = montar(p)
        c.jugarConMichi(); c.abandonar()
        assertEquals(SnakeSesionUi.Inactiva, c.estado.value)
        assertEquals(listOf("s1" to ResultadoSnake.Abandonada), p.cierres)
    }

    @Test fun `irse mientras se abre descarta la sesion que llega tarde`() {
        val p = Puente().apply { espera = CompletableDeferred(); aperturas += AperturaSnake.Lista("s1") }; val c = montar(p)
        c.jugarConMichi(); c.volver()
        p.espera!!.complete(Unit)
        assertEquals(SnakeSesionUi.Inactiva, c.estado.value)
        assertEquals(listOf("s1" to ResultadoSnake.Abandonada), p.cierres)
    }

    @Test fun `reiniciar abandona la partida y abre una nueva`() {
        val p = Puente().apply { aperturas += AperturaSnake.Lista("s1"); aperturas += AperturaSnake.Lista("s2") }; val c = montar(p)
        c.jugarConMichi(); c.reiniciar(conMichi = true)
        assertEquals(SnakeSesionUi.Jugando("s2", 2), c.estado.value)
        assertEquals(listOf("s1" to ResultadoSnake.Abandonada), p.cierres)
    }

    @Test fun `tras recibir recompensa jugar otra vez comprueba de nuevo y respeta la espera`() {
        val p = Puente().apply {
            aperturas += AperturaSnake.Lista("s1"); aperturas += AperturaSnake.NoDisponible(ResultadoCuidado.EN_ESPERA)
        }
        val c = montar(p)
        c.jugarConMichi(); c.partidaTerminada(v); c.reiniciar(conMichi = true)
        assertEquals(SnakeSesionUi.NoDisponible(ResultadoCuidado.EN_ESPERA), c.estado.value)
    }
}

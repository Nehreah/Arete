package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.miausnake.integration.Fixtures.HORA
import com.renea.arete_social.feature.miausnake.integration.Fixtures.adulta
import com.renea.arete_social.feature.miausnake.integration.Fixtures.t0
import com.renea.arete_social.feature.tamagotchi.logic.SesionesJuego
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.LibroSesionesJuego
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de balance de la Fase 3: Snake frente a Automático, a lo largo de un día. */
class SnakeBalanceTest {
    @Test fun `la politica por resultado`() {
        assertEquals(RecompensaJuego.Ninguna, SnakeRewardPolicy.decidir(bites = 2, won = false))
        assertEquals(RecompensaJuego(true, 0), SnakeRewardPolicy.decidir(3, false))
        assertEquals(RecompensaJuego(true, SnakeBalance.XP_EFFORT), SnakeRewardPolicy.decidir(10, false))
        assertEquals(RecompensaJuego(true, SnakeBalance.XP_WIN), SnakeRewardPolicy.decidir(0, true))
    }

    @Test fun `el extra maximo nunca supera la mitad del xp base`() {
        assertTrue(SnakeBalance.XP_WIN * 2 <= PetBalance.PLAY_XP)
        assertTrue(SnakeBalance.XP_EFFORT < SnakeBalance.XP_WIN)
    }

    /** Juega cada 3 h (la espera) durante 18 h y devuelve la mascota. */
    private fun dia(snake: Boolean): PetEntity {
        var pet = adulta { copy(hunger = 100, energy = 100, hygiene = 100) }
        var libro = LibroSesionesJuego()
        for (i in 0..5) {
            val ahora = t0 + i * 3 * HORA + i
            pet = pet.copy(hunger = 100, energy = 100) // aislamos la actividad: el desgaste no es lo que se compara
            if (!snake) {
                pet = pet.jugar(ahora).pet
            } else {
                val a = SesionesJuego.abrir(pet, libro, "s$i", ahora)
                libro = a.libro
                val c = SesionesJuego.cerrar(pet, libro, "s$i", RecompensaJuego(true, SnakeBalance.XP_WIN), ahora)
                pet = c.pet; libro = c.libro
                assertTrue(c.resultado is CierreSesionJuego.Aplicado)
            }
        }
        return pet
    }

    @Test fun `un dia de victorias de Snake cuesta lo mismo y da poco mas xp que Automatico`() {
        val auto = dia(snake = false); val snake = dia(snake = true)
        assertEquals(auto.vecesJugadoHoy, snake.vecesJugadoHoy)
        assertEquals(auto.hygiene, snake.hygiene)
        assertEquals(auto.happiness, snake.happiness)
        val extra = totalXp(snake) - totalXp(auto)
        // Solo las 4 primeras partidas del día (dentro del límite) llevan extra.
        assertEquals(PetBalance.MAX_PLAYS_PER_DAY * SnakeBalance.XP_WIN, extra)
    }

    private fun totalXp(p: PetEntity): Int =
        (1 until p.nivel).sumOf { com.renea.arete_social.feature.tamagotchi.logic.xpParaSubir(it) } + p.experiencia
}

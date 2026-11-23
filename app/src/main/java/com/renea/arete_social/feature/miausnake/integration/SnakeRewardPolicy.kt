package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego

/**
 * Balance de Miau Snake frente a «Automático» (decisión PROVISIONAL de la Fase 3, a confirmar con
 * playtest; los números viven aquí y solo aquí):
 *
 *  - COSTE: igual que Automático. Una partida que cuenta ejecuta exactamente `jugar()`: misma energía,
 *    hambre, higiene, ánimo, XP base, espera de 3 h y límite de 4 al día. Snake no abre una economía
 *    paralela ni hace obsoleto a Automático.
 *  - RECOMPENSA: la base es la de Automático; el esfuerzo solo añade un pequeño extra de XP
 *    (nunca más del 50 % del XP base) y no se acumula pasado el límite diario.
 *  - Partidas que NO cuentan: abandonadas, con repetición no válida o con menos de [MIN_BITES] bocados.
 *    Quien no juega de verdad no gasta la espera de Michi.
 */
object SnakeBalance {
    /** Bocados mínimos para que una partida terminada (sin victoria) cuente como jugar. */
    const val MIN_BITES = 3

    /** Con tantos bocados o más hay extra por esfuerzo. */
    const val BITES_FOR_EFFORT_BONUS = 10

    /** XP extra por una partida larga que acabó en choque. */
    const val XP_EFFORT = 5

    /** XP extra por lograr la meta del mapa (victoria). */
    const val XP_WIN = 10
}

/** Política pura: de un resultado verificado a lo que se le pide a Michi. Sin Android ni E/S. */
object SnakeRewardPolicy {
    fun decidir(bites: Int, won: Boolean): RecompensaJuego = when {
        won -> RecompensaJuego(aplicarJugar = true, xpExtra = SnakeBalance.XP_WIN)
        bites < SnakeBalance.MIN_BITES -> RecompensaJuego.Ninguna
        bites >= SnakeBalance.BITES_FOR_EFFORT_BONUS -> RecompensaJuego(aplicarJugar = true, xpExtra = SnakeBalance.XP_EFFORT)
        else -> RecompensaJuego(aplicarJugar = true, xpExtra = 0)
    }
}

package com.renea.arete_social.testutils

import com.renea.arete_social.domain.util.Reloj

/** Reloj controlado para tests: el tiempo solo avanza cuando el test lo dice. */
class RelojFalso(var ahora: Long = 1_000_000_000_000L) : Reloj {
    override fun ahora(): Long = ahora

    fun avanzarHoras(horas: Double) {
        ahora += (horas * 3_600_000).toLong()
    }

    fun avanzarMinutos(minutos: Long) {
        ahora += minutos * 60_000
    }
}

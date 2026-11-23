package com.renea.arete_social.feature.miausnake.levels

import java.util.Locale

/** Formatea milisegundos en formato legible mm:ss.s o s.s */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return if (minutes > 0) {
        String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths)
    } else {
        String.format(Locale.ROOT, "%d.%ds", seconds, tenths)
    }
}

/** Resumen de resultado al terminar una partida para mostrar en el pie. */
fun RecordUpdate.summary(mode: GameMode, parMs: Long): String = when (mode) {
    GameMode.META -> {
        if (won) {
            val tiempo = formatTime(elapsedMs)
            val medallaTxt = when (newMedal) {
                Medal.GOLD -> " · ¡Medalla de Oro!"
                Medal.SILVER -> " · ¡Medalla de Plata!"
                Medal.BRONZE -> " · ¡Medalla de Bronce!"
                Medal.NONE -> ""
            }
            "¡Meta superada en $tiempo!$medallaTxt"
        } else {
            "Meta no alcanzada ($score bocados)"
        }
    }
    GameMode.INFINITE -> {
        if (newBestScore) {
            "¡Nuevo récord: $score puntos!"
        } else {
            "Puntos: $score"
        }
    }
}

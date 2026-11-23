package com.renea.arete_social.feature.miausnake.ui.play

enum class ControlMode { SWIPE, DPAD }

/**
 * Ajustes de la partida. Desde la Fase 4 se persisten (control, zurdo, vibración, contraste y ritmo)
 * con `SnakePrefsCodec`; [interpolate] y [showDebug] son solo de depuración y no se guardan.
 */
data class PlaySettings(
    val control: ControlMode = ControlMode.SWIPE,
    val leftHanded: Boolean = false,
    val vibration: Boolean = true,
    /** Paleta de contraste alto. */
    val highContrast: Boolean = false,
    /** Ritmo relajado (+30 % de duración de tick). Solo práctica: sin récords ni medallas. */
    val relaxed: Boolean = false,
    /** Interpolación visual entre ticks (apagarla sirve para comparar en el spike). */
    val interpolate: Boolean = true,
    /** Panel con FPS, tirones y tamaño real de la celda en dp. */
    val showDebug: Boolean = false
)

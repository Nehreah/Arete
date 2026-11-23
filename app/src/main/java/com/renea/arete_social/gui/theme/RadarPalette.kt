package com.renea.arete_social.gui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RadarColors(
    val fondo: Color,
    val panel: Color,
    val verde: Color,
    val verdeBrillante: Color,
    val verdeOscuro: Color,
    val texto: Color,
    val muted: Color,
    val error: Color,
    val advertencia: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val scrimSubtle: Color
)

val RadarNightColors = RadarColors(
    fondo             = AntiredDarkBackground,
    panel             = AntiredDarkSurface,
    verde             = AntiredDarkPrimary, // nombre heredado; ahora representa el acento de marca
    verdeBrillante    = AntiredDarkPrimaryStrong,
    verdeOscuro       = Color(0xFF9F1723),
    texto             = AntiredDarkOnBackground,
    muted             = AntiredDarkOnSurfaceMuted,
    error             = AntiredDarkError,
    advertencia       = AntiredAmbar,
    successContainer  = Color(0xFF173A27),
    onSuccessContainer = Color(0xFF8BE0A7),
    dangerContainer   = Color(0xFF4A171B),
    onDangerContainer = AntiredDarkError,
    scrimSubtle       = Color(0x24FF646E)
)

val RadarDayColors = RadarColors(
    fondo             = AntiredLightBackground,
    panel             = AntiredLightSurface,
    verde             = AntiredLightPrimary, // nombre heredado; ahora representa el acento de marca
    verdeBrillante    = AntiredLightPrimaryStrong,
    verdeOscuro       = Color(0xFF9F1723),
    texto             = AntiredLightOnBackground,
    muted             = AntiredLightOnSurfaceMuted,
    error             = AntiredLightError,
    advertencia       = AntiredAmbar,
    successContainer  = Color(0xFFE8F5EC),
    onSuccessContainer = Color(0xFF185C32),
    dangerContainer   = AntiredLightError.copy(alpha = 0.12f),
    onDangerContainer = AntiredLightError,
    scrimSubtle       = Color(0x18D92332)
)

val LocalRadarColors = staticCompositionLocalOf { RadarNightColors }

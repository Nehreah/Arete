package com.renea.arete_social.gui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// ANTIRED THEME — Blanco/Negro/Rojo Día/Noche
//
// Arete_SocialTheme selecciona automáticamente entre el
// esquema oscuro (grafito, texto claro y rojo coral) y el
// esquema claro (blanco, texto negro y rojo de marca), según
// el tema del sistema o el parámetro [modoTema].
//
// Todos los colores viven en Color.kt (tokens Antired*).
// ============================================================

/**
 * Modo día/noche explícito. Cuando se pasa como argumento a [Arete_SocialTheme],
 * sobreescribe la preferencia del sistema operativo.
 * Persistencia en GestorSesionLocal.guardarModoTema() / obtenerModoTema().
 */
enum class ModoTema { CLARO, OSCURO }

/** Preferencia global de tema, guardada localmente y observable por Compose. */
object PreferenciaTemaApp {
    var modo by mutableStateOf<ModoTema?>(null)
        private set

    fun cargar() {
        modo = com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerModoTema()
    }

    fun establecer(nuevoModo: ModoTema) {
        modo = nuevoModo
        com.renea.arete_social.feature.auth.GestorSesionLocal.guardarModoTema(nuevoModo)
    }
}

private val AntiredDarkColorScheme = darkColorScheme(
    primary          = AntiredDarkPrimary,
    onPrimary        = AntiredDarkOnPrimary,
    secondary        = AntiredDarkPrimaryStrong,
    onSecondary      = AntiredDarkOnPrimary,
    background       = AntiredDarkBackground,
    onBackground     = AntiredDarkOnBackground,
    surface          = AntiredDarkSurface,
    onSurface        = AntiredDarkOnBackground,
    onSurfaceVariant = AntiredDarkOnSurfaceMuted,
    outline          = AntiredDarkSurfaceBorder,
    error            = AntiredDarkError,
    onError          = Color(0xFF690005),
)

private val AntiredLightColorScheme = lightColorScheme(
    primary          = AntiredLightPrimary,
    onPrimary        = AntiredLightOnPrimary,
    secondary        = AntiredLightPrimaryStrong,
    onSecondary      = AntiredLightOnPrimary,
    background       = AntiredLightBackground,
    onBackground     = AntiredLightOnBackground,
    surface          = AntiredLightSurface,
    onSurface        = AntiredLightOnBackground,
    onSurfaceVariant = AntiredLightOnSurfaceMuted,
    outline          = AntiredLightSurfaceBorder,
    error            = AntiredLightError,
    onError          = Color.White,
)

@Composable
fun Arete_SocialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    modoTema: ModoTema? = null,
    content: @Composable () -> Unit
) {
    val esOscuro = when {
        modoTema != null -> modoTema == ModoTema.OSCURO
        else -> darkTheme
    }

    val colorScheme = if (esOscuro) AntiredDarkColorScheme else AntiredLightColorScheme
    val radarColors = if (esOscuro) RadarNightColors else RadarDayColors
    val cicloDiaState = rememberCicloDia()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !esOscuro
                insetsController.isAppearanceLightNavigationBars = !esOscuro
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalRadarColors provides radarColors,
        LocalCicloDia provides cicloDiaState.value
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}


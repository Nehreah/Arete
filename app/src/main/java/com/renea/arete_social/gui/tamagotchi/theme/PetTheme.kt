package com.renea.arete_social.gui.tamagotchi.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores de aviso (se usan también en el dibujo de la mascota).
val DangerRed = Color(0xFFFF5252)
val WarningAmber = Color(0xFFFFB74D)
val SuccessGreen = Color(0xFF69F0AE)

/**
 * Paleta propia de la mascota. La interfaz (tarjetas, botones, textos) usa siempre los colores
 * del tema de Aréte (rojo de marca, claro/oscuro). Esta paleta solo aporta:
 *  - el toque ciberpunk del escenario (cian/menta/magenta neón),
 *  - un color por cada necesidad, para reconocerlas de un vistazo.
 * Son tonos medios: se leen bien tanto sobre fondo claro como oscuro.
 */
object PetPalette {
    // Neones del escenario (siempre sobre fondo oscuro)
    val NeonCyan = Color(0xFF2DD4BF)
    val NeonMint = Color(0xFF4ADE80)
    val NeonMagenta = Color(0xFFE85FA0)
    val NeonGold = Color(0xFFFFC857)

    // Fondo del escenario
    val SceneTop = Color(0xFF0B1220)
    val SceneBottom = Color(0xFF1A1030)

    // Una gama por necesidad
    val Hunger = Color(0xFFF59E0B)
    val Energy = Color(0xFF14B8A6)
    val Mood = Color(0xFFE85FA0)
    val Hygiene = Color(0xFF38BDF8)
    val Health = Color(0xFF22C55E)
}

/**
 * La pantalla hereda el tema global (Arete_SocialTheme) para respetar los modos
 * Claro y Oscuro de toda la aplicación.
 */
@Composable
fun PetGameTheme(content: @Composable () -> Unit) {
    content()
}

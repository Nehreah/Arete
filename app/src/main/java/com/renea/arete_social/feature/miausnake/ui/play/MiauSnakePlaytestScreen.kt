package com.renea.arete_social.feature.miausnake.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.miausnake.levels.PlaytestLevels
import com.renea.arete_social.feature.miausnake.levels.PlaytestMap
import com.renea.arete_social.feature.miausnake.ui.spike.CellSizeSpike
import com.renea.arete_social.feature.miausnake.ui.spike.CellSpec
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette

/**
 * Pantalla de prueba de la Fase 2 (menú del spike + partida). Es una herramienta de playtest:
 * NO es el selector de mapas del juego (eso es la Fase 4) ni se conecta a JUGAR (Fase 3).
 * Récords: solo de esta sesión, en memoria.
 */
@Composable
fun MiauSnakePlaytestScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val palette = remember { GamePalette.Olive }
    var spec by remember { mutableStateOf(CellSpec.PX24) } // el plan manda probar 24 primero
    var map by remember { mutableStateOf(PlaytestMap.WRAP) }
    var settings by remember { mutableStateOf(PlaySettings(showDebug = true)) }
    var playing by remember { mutableStateOf(false) }
    val best = remember { mutableStateMapOf<String, Int>() }

    if (playing) {
        val level = remember(map, spec) { PlaytestLevels.create(map, spec) }
        MiauSnakePlayScreen(
            level = level, settings = settings, modifier = modifier,
            bestScore = best[level.id] ?: 0,
            onFinished = { score, _ -> if (score > (best[level.id] ?: 0)) best[level.id] = score },
            onExit = { playing = false }
        )
        return
    }

    Column(
        modifier.fillMaxSize().background(palette.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SnakeText("MIAU SNAKE · PRUEBA FASE 2", palette, size = 20, bold = true)
        SnakeText("Gráficos programáticos, sin arte ni audio.", palette, size = 12, dim = true)

        Section("Mapa", palette)
        Choice(PlaytestMap.entries.toList(), map, { it.title }, palette) { map = it }

        Section("Tamaño de celda (spike)", palette)
        Choice(CellSpec.entries.toList(), spec, { "${it.referencePx} px → ${it.cols}×${it.rows}" }, palette) { spec = it }

        Section("Control", palette)
        Choice(ControlMode.entries.toList(), settings.control, { if (it == ControlMode.SWIPE) "Swipe" else "Cruceta" }, palette) {
            settings = settings.copy(control = it)
        }
        Toggle("Modo zurdo", settings.leftHanded, palette) { settings = settings.copy(leftHanded = it) }
        Toggle("Vibración", settings.vibration, palette) { settings = settings.copy(vibration = it) }
        Toggle("Interpolación entre ticks", settings.interpolate, palette) { settings = settings.copy(interpolate = it) }
        Toggle("Panel de FPS y tamaño de celda", settings.showDebug, palette) { settings = settings.copy(showDebug = it) }

        Spacer(Modifier.height(8.dp))
        SnakeButton("JUGAR", { playing = true }, palette, Modifier.fillMaxWidth(), primary = true)
        SnakeButton("Salir", onExit, palette, Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Section("Referencia del spike (celda en dp, pantalla típica)", palette)
        for (r in CellSizeSpike.table().filter { it.device.name.startsWith("Típico") }) {
            SnakeText("${r.spec.referencePx} px: celda ${"%.1f".format(r.cellDp)} dp · tablero ${"%.0f".format(r.boardWidthDp)}×${"%.0f".format(r.boardHeightDp)} dp", palette, size = 12, dim = true)
        }
    }
}

@Composable
private fun Section(text: String, palette: GamePalette) =
    SnakeText(text.uppercase(), palette, Modifier.padding(top = 6.dp), size = 11, bold = true, dim = true)

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, palette: GamePalette, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            val on = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) palette.food else palette.panel)
                    .clickable(role = Role.RadioButton) { onSelect(o) },
                contentAlignment = Alignment.Center
            ) {
                BasicTextLabel(label(o), on, palette)
            }
        }
    }
}

@Composable
private fun BasicTextLabel(text: String, on: Boolean, palette: GamePalette) {
    BasicText(
        text,
        style = TextStyle(
            color = if (on) palette.background else palette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun Toggle(text: String, value: Boolean, palette: GamePalette, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(palette.panel)
            .clickable(role = Role.Switch) { onChange(!value) }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SnakeText(text, palette, size = 14)
        SnakeText(if (value) "SÍ" else "NO", palette, size = 14, bold = true)
    }
}

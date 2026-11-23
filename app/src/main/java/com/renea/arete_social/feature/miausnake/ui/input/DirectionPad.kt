package com.renea.arete_social.feature.miausnake.ui.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette

/**
 * Cruceta opcional. Cada zona es de [zone] dp (≥ 56 dp: objetivo táctil amplio) y reacciona AL
 * PRESIONAR, no al soltar. Solo envía comandos de dirección. El modo zurdo lo resuelve quien la coloca.
 */
@Composable
fun DirectionPad(
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
    palette: GamePalette = GamePalette.Olive,
    zone: Dp = 64.dp
) {
    Box(modifier.size(zone * 3)) {
        PadZone(Direction.UP, "Arriba", Alignment.TopCenter, zone, palette, onDirection)
        PadZone(Direction.DOWN, "Abajo", Alignment.BottomCenter, zone, palette, onDirection)
        PadZone(Direction.LEFT, "Izquierda", Alignment.CenterStart, zone, palette, onDirection)
        PadZone(Direction.RIGHT, "Derecha", Alignment.CenterEnd, zone, palette, onDirection)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PadZone(
    dir: Direction,
    label: String,
    align: Alignment,
    zone: Dp,
    palette: GamePalette,
    onDirection: (Direction) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(onDirection)
    Box(
        Modifier
            .align(align)
            .size(zone)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) palette.wallEdge else palette.panel)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) { current(dir); true }
            }
            .pointerInput(dir) {
                detectTapGestures(onPress = {
                    pressed = true
                    current(dir)
                    tryAwaitRelease()
                    pressed = false
                })
            }
    ) {
        Canvas(Modifier.size(zone)) {
            val s = size.width
            val deg = when (dir) {
                Direction.UP -> 0f
                Direction.RIGHT -> 90f
                Direction.DOWN -> 180f
                Direction.LEFT -> 270f
            }
            rotate(deg, pivot = center) {
                val path = Path().apply {
                    moveTo(s * 0.5f, s * 0.24f)
                    lineTo(s * 0.76f, s * 0.68f)
                    lineTo(s * 0.24f, s * 0.68f)
                    close()
                }
                drawPath(path, palette.head)
            }
        }
    }
}

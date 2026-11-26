package com.renea.arete_social.feature.miausnake.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette

/** Piezas del menú previo a la partida (antes vivían en la pantalla de prueba de la Fase 2). */
@Composable
internal fun MenuSection(text: String, palette: GamePalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(4.dp, 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.food)
        )
        SnakeText(text.uppercase(), palette, size = 12, bold = true, dim = true)
    }
}

@Composable
internal fun <T> MenuChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    palette: GamePalette,
    onSelect: (T) -> Unit
) {
    val outerShape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(palette.background.copy(alpha = 0.5f))
            .border(1.dp, palette.wallEdge.copy(alpha = 0.35f), outerShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (o in options) {
            val on = o == selected
            val itemShape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(itemShape)
                    .background(if (on) palette.food else palette.panel.copy(alpha = 0.4f))
                    .clickable(role = Role.RadioButton) { onSelect(o) },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    label(o),
                    style = TextStyle(
                        color = if (on) palette.background else palette.text,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
internal fun MenuToggle(text: String, value: Boolean, palette: GamePalette, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(palette.panel)
            .border(1.dp, palette.wallEdge.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Switch) { onChange(!value) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SnakeText(text, palette, size = 14)
        // Switch indicador visual
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (value) palette.food else palette.background)
                .border(1.dp, palette.wallEdge.copy(alpha = 0.6f), RoundedCornerShape(13.dp))
                .padding(horizontal = 3.dp),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (value) palette.background else palette.wallEdge)
            )
        }
    }
}

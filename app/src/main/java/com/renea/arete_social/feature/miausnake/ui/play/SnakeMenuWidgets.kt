package com.renea.arete_social.feature.miausnake.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
internal fun MenuSection(text: String, palette: GamePalette) =
    SnakeText(text.uppercase(), palette, Modifier.padding(top = 6.dp), size = 12, bold = true, dim = true)

@Composable
internal fun <T> MenuChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    palette: GamePalette,
    onSelect: (T) -> Unit
) {
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
                BasicText(
                    label(o),
                    style = TextStyle(
                        color = if (on) palette.background else palette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
internal fun MenuToggle(text: String, value: Boolean, palette: GamePalette, onChange: (Boolean) -> Unit) {
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

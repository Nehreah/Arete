package com.renea.arete_social.feature.miausnake.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette

/** Botón del juego: ≥ 48 dp de alto, rol Button y texto (no depende solo del color). */
@Composable
fun SnakeButton(
    text: String,
    onClick: () -> Unit,
    palette: GamePalette,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (primary) palette.food else palette.panel
    val borderModifier = if (primary) Modifier else Modifier.border(1.dp, palette.wallEdge.copy(alpha = 0.5f), shape)
    Box(
        modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
            .clip(shape)
            .then(borderModifier)
            .background(if (enabled) bg else palette.panel.copy(alpha = 0.5f))
            // Fase 7: el lector de pantalla anuncia «<texto>, botón» y, si está inactivo, «desactivado».
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = text, onClick = onClick)
            .semantics { if (!enabled) disabled() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (primary) palette.background else if (enabled) palette.text else palette.textDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun SnakeText(
    text: String,
    palette: GamePalette,
    modifier: Modifier = Modifier,
    size: Int = 14,
    bold: Boolean = false,
    dim: Boolean = false
) {
    // Fase 7: los títulos (negrita y ≥ 20 sp) se anuncian como encabezado para navegar por TalkBack.
    val titulo = bold && size >= 20
    BasicText(
        text,
        if (titulo) modifier.semantics { heading() } else modifier,
        style = TextStyle(
            color = if (dim) palette.textDim else palette.text,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    )
}

/** Tarjeta con estilo retro-moderno y bordes suaves. */
@Composable
fun SnakeCard(
    palette: GamePalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val borderCol = if (highlight) palette.food else palette.wallEdge.copy(alpha = 0.6f)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Tarjeta", onClick = onClick)
    } else Modifier

    Box(
        modifier
            .clip(shape)
            .border(if (highlight) 2.dp else 1.dp, borderCol, shape)
            .background(palette.panel)
            .then(clickModifier)
            .padding(14.dp)
    ) {
        content()
    }
}

/** Píldora o etiqueta compacta informativa (ej. META, INFINITO, ORO). */
@Composable
fun SnakeBadge(
    text: String,
    palette: GamePalette,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val bg = if (highlight) palette.food else palette.wallEdge.copy(alpha = 0.35f)
    val fg = if (highlight) palette.background else palette.text
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** Botón de icono cuadrado con accesibilidad mínima de 48 dp. */
@Composable
fun SnakeIconButton(
    onClick: () -> Unit,
    label: String,
    palette: GamePalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(palette.panel)
            .border(1.dp, palette.wallEdge.copy(alpha = 0.5f), shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

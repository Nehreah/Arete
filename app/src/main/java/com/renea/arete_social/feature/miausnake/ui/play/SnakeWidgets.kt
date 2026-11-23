package com.renea.arete_social.feature.miausnake.ui.play

import androidx.compose.foundation.background
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
    Box(
        modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) palette.food else palette.panel)
            // Fase 7: el lector de pantalla anuncia «<texto>, botón» y, si está inactivo, «desactivado».
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = text, onClick = onClick)
            .semantics { if (!enabled) disabled() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (primary) palette.background else palette.text,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
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

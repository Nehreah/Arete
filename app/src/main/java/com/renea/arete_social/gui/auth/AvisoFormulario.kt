package com.renea.arete_social.gui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Aviso suave para formularios (login/registro): una tarjeta tenue con icono y una frase
 * clara, en lugar de un texto rojo suelto. Se anuncia a TalkBack al aparecer.
 */
@Composable
fun AvisoFormulario(mensaje: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), forma)
            .border(1.dp, color.copy(alpha = 0.35f), forma)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "⚠️", fontSize = 16.sp)
        Text(
            text = mensaje,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

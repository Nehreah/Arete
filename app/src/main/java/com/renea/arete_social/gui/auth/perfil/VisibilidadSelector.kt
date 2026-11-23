package com.renea.arete_social.gui.auth.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.gui.theme.AntiredAmbar
import com.renea.arete_social.gui.theme.AntiredRojo
import com.renea.arete_social.gui.theme.AntiredVerde
import com.renea.arete_social.gui.theme.RadarChip

/**
 * Selector 🟢 visible / 🟡 aproximada / 🔴 invisible, tal como se describió en el
 * documento original del proyecto. El chip seleccionado toma el color de
 * ui/theme/Color.kt correspondiente a esa visibilidad.
 */
@Composable
fun VisibilidadSelector(
    seleccionado: Visibilidad,
    onSeleccionar: (Visibilidad) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OpcionVisibilidad(Visibilidad.VISIBLE, "🟢 Visible", AntiredVerde, seleccionado, onSeleccionar)
        OpcionVisibilidad(Visibilidad.APROXIMADA, "🟡 Aproximada", AntiredAmbar, seleccionado, onSeleccionar)
        OpcionVisibilidad(Visibilidad.INVISIBLE, "🔴 Invisible", AntiredRojo, seleccionado, onSeleccionar)
    }
}

@Composable
private fun OpcionVisibilidad(
    valor: Visibilidad,
    etiqueta: String,
    color: Color,
    seleccionado: Visibilidad,
    onSeleccionar: (Visibilidad) -> Unit
) {
    RadarChip(
        texto = etiqueta,
        seleccionado = valor == seleccionado,
        onClick = { onSeleccionar(valor) },
        colorActivo = color
    )
}

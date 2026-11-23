package com.renea.arete_social.gui.mapa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.gui.mapa.osm.CapaMapa
import com.renea.arete_social.gui.theme.LocalRadarColors

/**
 * Botón flotante de selección de capa del mapa.
 *
 * Muestra el icono de la capa activa. Al pulsarlo despliega un mini-menú con las 4 capas
 * disponibles. Al seleccionar una se cierra el menú e invoca [onCapaSeleccionada].
 *
 * No tiene estado propio: recibe la [capaActual] y delega el cambio al llamador.
 */
@Composable
fun SelectorCapaMapa(
    capaActual: CapaMapa,
    onCapaSeleccionada: (CapaMapa) -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    var abierto by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        // Menú desplegable: aparece encima del botón.
        AnimatedVisibility(
            visible = abierto,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit  = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 52.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .border(1.dp, radarColors.verde.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    CapaMapa.entries.forEach { capa ->
                        val activa = capa == capaActual
                        Row(
                            modifier = Modifier
                                .widthIn(min = 160.dp)
                                .background(
                                    if (activa) radarColors.verde.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onCapaSeleccionada(capa)
                                    abierto = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(capa.icono, fontSize = 18.sp)
                            Text(
                                text = capa.etiqueta,
                                fontSize = 13.sp,
                                fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal,
                                color = if (activa) radarColors.verdeBrillante
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Botón principal: icono de la capa activa.
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, radarColors.verde.copy(alpha = 0.55f), CircleShape)
                .clickable { abierto = !abierto },
            contentAlignment = Alignment.Center
        ) {
            Text(capaActual.icono, fontSize = 20.sp)
        }
    }
}

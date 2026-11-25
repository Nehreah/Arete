package com.renea.arete_social.gui.mapa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.renea.arete_social.gui.mapa.osm.CapaMapa
import com.renea.arete_social.gui.theme.LocalRadarColors

/**
 * Botón único de **Mapa**.
 *
 * Al pulsarlo se despliega, justo debajo, un menú con las capas disponibles (cambiar de mapa) y la
 * opción de **descargar el mapa** para usarlo sin internet. Se cierra al elegir una opción o al
 * tocar fuera del menú.
 *
 * No tiene estado de mapa propio: recibe la [capaActual] y delega los cambios al llamador.
 */
@Composable
fun SelectorCapaMapa(
    capaActual: CapaMapa,
    onCapaSeleccionada: (CapaMapa) -> Unit,
    onDescargarMapa: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    var abierto by remember { mutableStateOf(false) }
    val separacionPx = with(LocalDensity.current) { 52.dp.roundToPx() }

    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        // Botón principal: siempre el mismo icono de mapa; se resalta mientras el menú está abierto.
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(if (abierto) radarColors.verde.copy(alpha = 0.25f) else radarColors.panel)
                .border(
                    width = if (abierto) 1.5.dp else 1.dp,
                    color = if (abierto) radarColors.verdeBrillante else radarColors.verde.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .clickable { abierto = !abierto }
                .semantics { contentDescription = "Mapa: cambiar de mapa o descargar" },
            contentAlignment = Alignment.Center
        ) {
            Text("🗺️", fontSize = 20.sp)
        }

        MenuCapasPopup(
            abierto = abierto,
            onDismissRequest = { abierto = false },
            capaActual = capaActual,
            onCapaSeleccionada = {
                onCapaSeleccionada(it)
                abierto = false
            },
            onDescargarMapa = {
                abierto = false
                onDescargarMapa()
            }
        )
    }
}

/**
 * Menú desplegable flotante de capas de mapa y descarga offline.
 */
@Composable
fun MenuCapasPopup(
    abierto: Boolean,
    onDismissRequest: () -> Unit,
    capaActual: CapaMapa,
    onCapaSeleccionada: (CapaMapa) -> Unit,
    onDescargarMapa: () -> Unit,
    offsetY: androidx.compose.ui.unit.Dp = 52.dp
) {
    val radarColors = LocalRadarColors.current
    val separacionPx = with(LocalDensity.current) { offsetY.roundToPx() }

    if (abierto) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, separacionPx),
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn() + slideInVertically { -it / 4 }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .widthIn(min = 180.dp)
                        .border(1.dp, radarColors.verde.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "MAPA",
                            color = radarColors.muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        CapaMapa.entries.forEach { capa ->
                            val activa = capa == capaActual
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 180.dp)
                                    .background(
                                        if (activa) radarColors.verde.copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onCapaSeleccionada(capa)
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

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = radarColors.verde.copy(alpha = 0.25f)
                        )

                        Row(
                            modifier = Modifier
                                .widthIn(min = 180.dp)
                                .clickable {
                                    onDescargarMapa()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📥", fontSize = 18.sp)
                            Text(
                                text = "Descargar mapa",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

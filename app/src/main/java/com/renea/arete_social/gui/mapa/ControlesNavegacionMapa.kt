package com.renea.arete_social.gui.mapa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.gui.theme.LocalRadarColors
import kotlin.math.abs

/**
 * Controles de navegación y orientación táctica del mapa construidos 100% con
 * Software Libre (Jetpack Compose puro, sin dependencias privativas ni Google Play Services).
 */

/**
 * Brújula táctica interactiva. Muestra la orientación actual del mapa y rota su aguja
 * apuntando al Norte magnético. Al pulsarla, orienta la cámara de nuevo hacia el Norte (bearing = 0°).
 */
@Composable
fun BrujulaTactico(
    bearing: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    siempreVisible: Boolean = false
) {
    val radarColors = LocalRadarColors.current
    val rotacionAguja by animateFloatAsState(
        targetValue = -bearing,
        animationSpec = spring(stiffness = 300f),
        label = "rotacion_brujula"
    )

    val desviadoDelNorte = abs(bearing) > 1.5f && abs(bearing - 360f) > 1.5f

    AnimatedVisibility(
        visible = siempreVisible || desviadoDelNorte,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(radarColors.panel)
                .border(1.dp, radarColors.verde.copy(alpha = 0.65f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(34.dp)
                    .rotate(rotacionAguja)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radio = size.width * 0.45f

                // Anillo exterior sutil
                drawCircle(
                    color = radarColors.verde.copy(alpha = 0.35f),
                    radius = radio,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Aguja Norte (Rojo vivo / Acento táctico)
                val agujaNorte = Path().apply {
                    moveTo(cx, cy - radio + 2.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), cy)
                    lineTo(cx - 4.dp.toPx(), cy)
                    close()
                }
                drawPath(path = agujaNorte, color = Color(0xFFFF3B30))

                // Aguja Sur (Muted / Grisáceo táctico)
                val agujaSur = Path().apply {
                    moveTo(cx, cy + radio - 2.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), cy)
                    lineTo(cx - 4.dp.toPx(), cy)
                    close()
                }
                drawPath(path = agujaSur, color = radarColors.muted.copy(alpha = 0.7f))

                // Pivote central
                drawCircle(
                    color = radarColors.verdeBrillante,
                    radius = 2.dp.toPx()
                )
            }

            // Indicador de "N" fijo en la parte superior para accesibilidad táctica
            Text(
                text = "N",
                color = Color(0xFFFF3B30),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
        }
    }
}

/**
 * Botonera de Zoom táctica (+ y -). Permite acercar o alejar el mapa suavemente
 * sin depender de gestos multitouch si el usuario opera el dispositivo con una sola mano.
 */
@Composable
fun BotoneraZoom(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(radarColors.panel)
            .border(1.dp, radarColors.verde.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botón Zoom In (+)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable { onZoomIn() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = radarColors.verdeBrillante,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Línea divisoria
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 1.dp)
                .background(radarColors.verde.copy(alpha = 0.25f))
        )

        // Botón Zoom Out (−)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable { onZoomOut() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = radarColors.verdeBrillante,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

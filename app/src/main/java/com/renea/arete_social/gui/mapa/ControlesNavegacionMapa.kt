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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Barra de cámara: **Alejar · Brújula/Centrar · Acercar** en una cápsula flotante.
 * La brújula central rota señalando al Norte magnético y, al pulsarla, centra la cámara en la posición
 * del usuario.
 */
@Composable
fun BarraControlesCamara(
    bearing: Float,
    siguiendoUsuario: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCentrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    Row(
        modifier = modifier
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(radarColors.panel.copy(alpha = 0.96f))
            .border(1.dp, radarColors.verde.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotonZoomRedondo(simbolo = "−", tamanoTexto = 20.sp, descripcion = "Alejar", onClick = onZoomOut)
        BotonBrujulaCentrar(bearing = bearing, siguiendoUsuario = siguiendoUsuario, onClick = onCentrar)
        BotonZoomRedondo(simbolo = "+", tamanoTexto = 22.sp, descripcion = "Acercar", onClick = onZoomIn)
    }
}

@Composable
private fun BotonZoomRedondo(
    simbolo: String,
    tamanoTexto: androidx.compose.ui.unit.TextUnit,
    descripcion: String,
    onClick: () -> Unit
) {
    val radarColors = LocalRadarColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = simbolo,
            color = radarColors.verdeBrillante,
            fontSize = tamanoTexto,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Brújula y botón de centrado unificado:
 * La aguja rota en tiempo real hacia el Norte magnético y la retícula táctica se ilumina
 * mientras la cámara sigue al usuario. Al pulsarla, centra la posición en el usuario.
 */
@Composable
private fun BotonBrujulaCentrar(
    bearing: Float,
    siguiendoUsuario: Boolean,
    onClick: () -> Unit
) {
    val radarColors = LocalRadarColors.current
    val rotacionAguja by animateFloatAsState(
        targetValue = -bearing,
        animationSpec = spring(stiffness = 300f),
        label = "rotacion_brujula_centrar"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (siguiendoUsuario) radarColors.verde.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                width = if (siguiendoUsuario) 1.5.dp else 1.dp,
                color = if (siguiendoUsuario) radarColors.verdeBrillante else radarColors.verde.copy(alpha = 0.6f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .semantics { contentDescription = "Brújula y centrar en mi posición" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(28.dp)
                .rotate(rotacionAguja)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radio = size.width * 0.46f

            // Anillo exterior sutil
            drawCircle(
                color = if (siguiendoUsuario) radarColors.verdeBrillante.copy(alpha = 0.45f)
                        else radarColors.verde.copy(alpha = 0.3f),
                radius = radio,
                style = Stroke(width = 1.dp.toPx())
            )

            // Ticks de la mira (retícula táctica)
            val tick = 3.dp.toPx()
            val colorTick = if (siguiendoUsuario) radarColors.verdeBrillante else radarColors.muted
            drawLine(color = colorTick, start = Offset(cx, 0f), end = Offset(cx, tick), strokeWidth = 1.dp.toPx())
            drawLine(color = colorTick, start = Offset(cx, size.height - tick), end = Offset(cx, size.height), strokeWidth = 1.dp.toPx())
            drawLine(color = colorTick, start = Offset(0f, cy), end = Offset(tick, cy), strokeWidth = 1.dp.toPx())
            drawLine(color = colorTick, start = Offset(size.width - tick, cy), end = Offset(size.width, cy), strokeWidth = 1.dp.toPx())

            // Aguja Norte (Rojo vivo)
            val agujaNorte = Path().apply {
                moveTo(cx, cy - radio + 2.dp.toPx())
                lineTo(cx + 3.dp.toPx(), cy)
                lineTo(cx - 3.dp.toPx(), cy)
                close()
            }
            drawPath(path = agujaNorte, color = Color(0xFFFF3B30))

            // Aguja Sur (Muted táctico)
            val agujaSur = Path().apply {
                moveTo(cx, cy + radio - 2.dp.toPx())
                lineTo(cx + 3.dp.toPx(), cy)
                lineTo(cx - 3.dp.toPx(), cy)
                close()
            }
            drawPath(path = agujaSur, color = radarColors.muted.copy(alpha = 0.7f))

            // Pivote central iluminado
            drawCircle(
                color = if (siguiendoUsuario) radarColors.verdeBrillante else radarColors.verde,
                radius = 2.dp.toPx()
            )
        }

        // Indicador "N"
        Text(
            text = "N",
            color = Color(0xFFFF3B30),
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.5.dp)
        )
    }
}

/** Botón "Agregar evento" táctico: tamaño calibrado (40.dp) para integrarse con la cabecera. */
@Composable
fun BotonCrearEvento(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(radarColors.verde)
            .border(1.dp, radarColors.verdeBrillante, CircleShape)
            .clickable { onClick() }
            .semantics { contentDescription = "Agregar evento" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Un acceso rápido del mapa (Mapa, Cercanos, Sala general, Librería). */
data class AccesoMapa(val id: String, val icono: String, val etiqueta: String)

/**
 * Los accesos rápidos como círculos pequeños agrupados en reposo. Al pulsar se despliegan en fila
 * (con su nombre debajo) y al elegir uno vuelven a recogerse. Si no se elige nada, se recogen solos a los 4 segundos.
 *
 * Soporta alineación desde la derecha para posicionarse en la esquina superior derecha sin salirse de la pantalla.
 */
@Composable
fun AccesosMapaApilados(
    accesos: List<AccesoMapa>,
    idActivo: String?,
    onSeleccionar: (String) -> Unit,
    modifier: Modifier = Modifier,
    desdeLaDerecha: Boolean = true
) {
    val radarColors = LocalRadarColors.current
    var expandido by remember { mutableStateOf(false) }
    val progreso by animateFloatAsState(
        targetValue = if (expandido) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "accesos_apilados"
    )
    LaunchedEffect(expandido) {
        if (expandido) {
            delay(4000)
            expandido = false
        }
    }

    val tamano = 40.dp
    val solape = 12.dp      // cuánto asoma cada círculo en reposo
    val separacion = 48.dp  // distancia entre centros ya desplegados
    val alturaEtiqueta = 14.dp
    val huecos = (accesos.size - 1).coerceAtLeast(0)
    val anchoTotal = tamano + lerp(solape * huecos, separacion * huecos, progreso)

    // En reposo, el activo o el primero queda al frente
    val porRango = remember(accesos, idActivo) { accesos.sortedByDescending { it.id == idActivo } }

    Box(
        modifier = modifier
            .width(anchoTotal)
            .height(tamano + alturaEtiqueta)
    ) {
        // Se dibuja de atrás hacia adelante: el rango 0 queda encima de todos.
        porRango.withIndex().reversed().forEach { (rango, acceso) ->
            val indice = accesos.indexOf(acceso)
            val activo = acceso.id == idActivo

            val x: Dp = if (desdeLaDerecha) {
                val xReposado = anchoTotal - tamano - (solape * rango)
                val xDesplegado = anchoTotal - tamano - (separacion * indice)
                lerp(xReposado, xDesplegado, progreso)
            } else {
                lerp(solape * rango, separacion * indice, progreso)
            }

            Column(
                modifier = Modifier.offset(x = x).width(tamano),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(tamano)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            if (activo) radarColors.verde.copy(alpha = 0.35f).compositeOver(radarColors.panel)
                            else radarColors.panel
                        )
                        .border(
                            width = if (activo) 1.5.dp else 1.dp,
                            color = if (activo) radarColors.verdeBrillante else radarColors.verde.copy(alpha = 0.65f),
                            shape = CircleShape
                        )
                        .clickable {
                            if (expandido) {
                                expandido = false
                                onSeleccionar(acceso.id)
                            } else {
                                expandido = true
                            }
                        }
                        .semantics { contentDescription = acceso.etiqueta },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = acceso.icono,
                        color = if (acceso.id == "mapa") Color.Unspecified else radarColors.verdeBrillante,
                        fontSize = if (acceso.id == "mapa") 18.sp else 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = acceso.etiqueta,
                    color = radarColors.verdeBrillante,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentWidth(unbounded = true)
                        .padding(top = 2.dp)
                        .alpha(progreso)
                )
            }
        }
    }
}

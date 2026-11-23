package com.renea.arete_social.gui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import com.renea.arete_social.gui.theme.TabChatColor
import com.renea.arete_social.gui.theme.TabComunidadColor
import com.renea.arete_social.gui.theme.TabMapaColor
import com.renea.arete_social.gui.theme.TabMascotaColor
import com.renea.arete_social.gui.theme.TabPerfilColor

/**
 * Pestañas principales de navegación inferior para el rediseño completo de AntiRed.
 */
enum class TabNavegacion(val etiqueta: String) {
    PERFIL("PERFIL"),
    MASCOTA("MASCOTA"),
    MAPA("MAPA"),
    COMUNIDAD("PARCHE"),
    CHAT("CHAT")
}

/**
 * Barra de navegación inferior moderna tipo cyber-táctica, idéntica a los mockups del rediseño.
 */
@Composable
fun AreteBottomBar(
    tabActual: TabNavegacion,
    onTabSeleccionado: (TabNavegacion) -> Unit,
    modifier: Modifier = Modifier
) {
    val esOscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colorFondo = MaterialTheme.colorScheme.surface
    val colorBorde = MaterialTheme.colorScheme.outline.copy(alpha = if (esOscuro) 0.18f else 0.12f)
    val formaBarra = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = formaBarra, spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(formaBarra)
            .background(colorFondo)
            .border(1.dp, colorBorde, formaBarra)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabNavegacion.values().forEach { tab ->
                val seleccionado = tab == tabActual
                val colorMarca = colorDeMarca(tab)

                val colorIcono by animateColorAsState(
                    targetValue = if (seleccionado) colorMarca else (if (esOscuro) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.42f)),
                    animationSpec = tween(220),
                    label = "tab_color"
                )

                val escalaIcono by animateFloatAsState(
                    targetValue = if (seleccionado) 1.08f else 1.0f,
                    animationSpec = tween(220),
                    label = "tab_scale"
                )

                val fondoPillAlpha by animateFloatAsState(
                    targetValue = if (seleccionado) (if (esOscuro) 0.15f else 0.10f) else 0f,
                    animationSpec = tween(220),
                    label = "pill_alpha"
                )

                val formaPill = RoundedCornerShape(16.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(formaPill)
                        .background(colorMarca.copy(alpha = fondoPillAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSeleccionado(tab)
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BottomBarIcon(
                            tab = tab,
                            color = colorIcono,
                            modifier = Modifier
                                .size(22.dp)
                                .scale(escalaIcono)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = tab.etiqueta,
                            color = colorIcono,
                            fontSize = 9.5.sp,
                            fontWeight = if (seleccionado) FontWeight.ExtraBold else FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // Indicador de pestaña activa sutil y con aire
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (seleccionado) colorMarca else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

/** Color de marca fijo por pestaña (Plan de Fidelidad Visual v2, sección 2.1). */
private fun colorDeMarca(tab: TabNavegacion): Color = when (tab) {
    TabNavegacion.PERFIL -> TabPerfilColor
    TabNavegacion.MASCOTA -> TabMascotaColor
    TabNavegacion.MAPA -> TabMapaColor
    TabNavegacion.COMUNIDAD -> TabComunidadColor
    TabNavegacion.CHAT -> TabChatColor
}

/**
 * Renderizado de íconos vectoriales tácticos adaptados a la estética de los mockups.
 */
@Composable
private fun BottomBarIcon(
    tab: TabNavegacion,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.7.dp.toPx()

        when (tab) {
            TabNavegacion.PERFIL -> {
                // Cabeza: círculo
                val r = w * 0.20f
                drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(w * 0.5f, h * 0.28f),
                    style = Stroke(width = strokeWidth)
                )

                // Hombros: arco curvado
                val pathHombros = Path().apply {
                    moveTo(w * 0.16f, h * 0.88f)
                    cubicTo(
                        w * 0.18f, h * 0.60f,
                        w * 0.32f, h * 0.54f,
                        w * 0.50f, h * 0.54f
                    )
                    cubicTo(
                        w * 0.68f, h * 0.54f,
                        w * 0.82f, h * 0.60f,
                        w * 0.84f, h * 0.88f
                    )
                }
                drawPath(
                    path = pathHombros,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            TabNavegacion.MASCOTA -> {
                // Huella: 4 deditos
                val rDedo = w * 0.08f
                drawCircle(color = color, radius = rDedo, center = Offset(w * 0.22f, h * 0.32f), style = Stroke(width = strokeWidth))
                drawCircle(color = color, radius = rDedo, center = Offset(w * 0.40f, h * 0.20f), style = Stroke(width = strokeWidth))
                drawCircle(color = color, radius = rDedo, center = Offset(w * 0.60f, h * 0.20f), style = Stroke(width = strokeWidth))
                drawCircle(color = color, radius = rDedo, center = Offset(w * 0.78f, h * 0.32f), style = Stroke(width = strokeWidth))

                // Almohadilla central
                val pathPad = Path().apply {
                    moveTo(w * 0.30f, h * 0.58f)
                    cubicTo(w * 0.35f, h * 0.46f, w * 0.65f, h * 0.46f, w * 0.70f, h * 0.58f)
                    cubicTo(w * 0.82f, h * 0.76f, w * 0.62f, h * 0.88f, w * 0.50f, h * 0.88f)
                    cubicTo(w * 0.38f, h * 0.88f, w * 0.18f, h * 0.76f, w * 0.30f, h * 0.58f)
                    close()
                }
                drawPath(path = pathPad, color = color, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
            }

            TabNavegacion.MAPA -> {
                // Mapa plegado en 3 paneles
                val path = Path().apply {
                    // Panel 1 (izquierdo)
                    moveTo(w * 0.15f, h * 0.25f)
                    lineTo(w * 0.38f, h * 0.14f)
                    lineTo(w * 0.38f, h * 0.75f)
                    lineTo(w * 0.15f, h * 0.86f)
                    close()

                    // Panel 2 (central)
                    moveTo(w * 0.38f, h * 0.14f)
                    lineTo(w * 0.62f, h * 0.25f)
                    lineTo(w * 0.62f, h * 0.86f)
                    lineTo(w * 0.38f, h * 0.75f)
                    close()

                    // Panel 3 (derecho)
                    moveTo(w * 0.62f, h * 0.25f)
                    lineTo(w * 0.85f, h * 0.14f)
                    lineTo(w * 0.85f, h * 0.75f)
                    lineTo(w * 0.62f, h * 0.86f)
                    close()
                }
                drawPath(path = path, color = color, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))
            }

            TabNavegacion.COMUNIDAD -> {
                // Grupo de personas: persona frontal + secundaria
                // Persona frontal
                drawCircle(
                    color = color,
                    radius = w * 0.17f,
                    center = Offset(w * 0.42f, h * 0.30f),
                    style = Stroke(width = strokeWidth)
                )
                val pathFront = Path().apply {
                    moveTo(w * 0.16f, h * 0.86f)
                    cubicTo(w * 0.18f, h * 0.58f, w * 0.30f, h * 0.54f, w * 0.42f, h * 0.54f)
                    cubicTo(w * 0.54f, h * 0.54f, w * 0.66f, h * 0.58f, w * 0.68f, h * 0.86f)
                }
                drawPath(path = pathFront, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                // Persona de fondo (derecha)
                drawCircle(
                    color = color,
                    radius = w * 0.13f,
                    center = Offset(w * 0.72f, h * 0.28f),
                    style = Stroke(width = strokeWidth)
                )
                val pathBack = Path().apply {
                    moveTo(w * 0.68f, h * 0.56f)
                    cubicTo(w * 0.76f, h * 0.56f, w * 0.86f, h * 0.62f, w * 0.88f, h * 0.82f)
                }
                drawPath(path = pathBack, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }

            TabNavegacion.CHAT -> {
                // Burbuja de chat redondeada con notch
                val r = 4.dp.toPx()
                val rect = Rect(w * 0.14f, h * 0.18f, w * 0.86f, h * 0.76f)
                val pathBubble = Path().apply {
                    addRoundRect(RoundRect(rect, CornerRadius(r, r)))
                }
                drawPath(path = pathBubble, color = color, style = Stroke(width = strokeWidth))

                // 3 puntos dentro
                val dotR = 1.3.dp.toPx()
                val cy = h * 0.47f
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.36f, cy), style = Fill)
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.50f, cy), style = Fill)
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.64f, cy), style = Fill)
            }
        }
    }
}

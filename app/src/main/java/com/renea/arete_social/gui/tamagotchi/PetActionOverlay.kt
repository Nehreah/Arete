package com.renea.arete_social.gui.tamagotchi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Acciones con animación. Los textos son cortos y claros; el efecto visual sigue siendo
 * ciberpunk (rejilla, láser, hexágonos, rayos).
 */
enum class PetActionType(val title: String, val subtitle: String, val color: Color) {
    FEED("¡Ñam ñam!", "Se está llenando de energía", Color(0xFF2EE59D)),
    PLAY("¡A jugar!", "Se divierte contigo (+XP)", Color(0xFF00E5FF)),
    CLEAN("Hora del baño", "Quedó como nueva", Color(0xFF38BDF8)),
    SLEEP("Descansando", "Recargando energía", Color(0xFF81D4FA)),
    HEAL("Medicina", "Se siente mucho mejor", Color(0xFFFF5252)),
    TRAIN("¡Modo turbo!", "Gana el doble de XP un rato", Color(0xFFFFC857)),
    REBOOT("Reiniciando…", "Vuelve a estar en línea", Color(0xFFE85FA0))
}

/**
 * Capa animada que se superpone sobre la escena mientras dura una acción.
 */
@Composable
fun PetActionOverlay(
    actionType: PetActionType?,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(actionType) {
        if (actionType != null) {
            visible = true
            delay(2200L)
            visible = false
            delay(300L)
            onAnimationEnd()
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && actionType != null,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        if (actionType == null) return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f)),
            contentAlignment = Alignment.Center
        ) {
            when (actionType) {
                PetActionType.FEED -> FeedDataAnimation()
                PetActionType.PLAY, PetActionType.REBOOT -> NeuralSyncAnimation()
                PetActionType.CLEAN -> CachePurgeAnimation()
                PetActionType.SLEEP -> PlasmaRechargeAnimation()
                PetActionType.HEAL -> AntivirusPatchAnimation()
                PetActionType.TRAIN -> OverclockBurstAnimation()
            }

            // Cartel inferior: fondo del tema, borde y título del color de la acción.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xE60B1220), RoundedCornerShape(14.dp))
                    .border(1.dp, actionType.color.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = actionType.title,
                        color = actionType.color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = actionType.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/** Comer: cascada de bloques de energía que caen hacia la mascota. */
@Composable
private fun FeedDataAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "feedAnim")
    val streamProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "feedStream"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f

        // Columnas de bits y cubos cayendo
        val columns = listOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)
        columns.forEachIndexed { idx, colXRatio ->
            val colX = w * colXRatio
            val offsetProgress = (streamProgress + idx * 0.18f) % 1f
            val packetY = h * offsetProgress

            drawRect(
                color = Color(0xFF2EE59D).copy(alpha = (1f - offsetProgress).coerceIn(0.1f, 0.9f)),
                topLeft = Offset(colX - 6.dp.toPx(), packetY),
                size = Size(12.dp.toPx(), 18.dp.toPx())
            )
            // Línea de flujo
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xFF2EE59D).copy(alpha = 0.7f), Color.Transparent),
                    startY = packetY - 40.dp.toPx(),
                    endY = packetY
                ),
                start = Offset(colX, packetY - 40.dp.toPx()),
                end = Offset(colX, packetY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Pulso de absorción central
        drawCircle(
            color = Color(0xFF2EE59D).copy(alpha = (1f - streamProgress) * 0.5f),
            radius = streamProgress * (w * 0.35f),
            center = Offset(centerX, centerY + 20.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

/** Jugar / reiniciar: anillo de nodos que gira alrededor de la mascota. */
@Composable
private fun NeuralSyncAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "neuralAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitRotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + 15.dp.toPx()
        val radius = size.width * 0.28f

        // Anillo exterior rotatorio con nodos
        val radAngle = Math.toRadians(rotation.toDouble())
        val nodeCount = 6
        for (i in 0 until nodeCount) {
            val a = radAngle + (i * 2 * Math.PI / nodeCount)
            val nx = cx + radius * cos(a).toFloat()
            val ny = cy + (radius * 0.5f) * sin(a).toFloat()

            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                radius = 5.dp.toPx(),
                center = Offset(nx, ny)
            )
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                start = Offset(cx, cy),
                end = Offset(nx, ny),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        drawOval(
            color = Color(0xFF00E5FF).copy(alpha = 0.5f),
            topLeft = Offset(cx - radius, cy - radius * 0.5f),
            size = Size(radius * 2, radius),
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.18f),
            radius = radius * 0.8f,
            center = Offset(cx, cy)
        )
    }
}

/** Baño: haz de luz que barre la escena de arriba abajo. */
@Composable
private fun CachePurgeAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "purgeAnim")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "laserSweep"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val currentLaserY = h * laserYRatio

        // Rejilla de fondo sutil
        val gridLines = 8
        for (i in 1..gridLines) {
            val y = (h / (gridLines + 1)) * i
            drawLine(
                color = Color(0xFF00FFA3).copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Rayo láser verde esmeralda y resplandor
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF00FFA3).copy(alpha = 0.35f),
                    Color(0xFF00FFA3).copy(alpha = 0.85f),
                    Color(0xFF00FFA3).copy(alpha = 0.35f),
                    Color.Transparent
                ),
                startY = currentLaserY - 24.dp.toPx(),
                endY = currentLaserY + 24.dp.toPx()
            ),
            topLeft = Offset(0f, currentLaserY - 24.dp.toPx()),
            size = Size(w, 48.dp.toPx())
        )
        drawLine(
            color = Color.White,
            start = Offset(0f, currentLaserY),
            end = Offset(w, currentLaserY),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

/** Dormir: cúpula hexagonal protectora que respira. */
@Composable
private fun PlasmaRechargeAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "plasmaAnim")
    val shieldPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shieldScale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + 18.dp.toPx()
        val radius = size.width * 0.32f * shieldPulse

        // Hexágono protector
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60 - 30).toDouble())
            val px = cx + radius * cos(angle).toFloat()
            val py = cy + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        drawPath(
            path = path,
            color = Color(0xFF81D4FA).copy(alpha = 0.20f)
        )
        drawPath(
            path = path,
            color = Color(0xFF81D4FA).copy(alpha = 0.75f),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

/** Medicina: cruz verde que pulsa y partículas rojas que se disipan. */
@Composable
private fun AntivirusPatchAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "antivirusAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "shieldRot"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + 15.dp.toPx()
        val radius = size.width * 0.30f

        // Cruz médica / de integridad en el centro
        val crossSize = 22.dp.toPx()
        drawRect(
            color = Color(0xFF2EE59D).copy(alpha = pulse),
            topLeft = Offset(cx - crossSize / 2f, cy - 6.dp.toPx()),
            size = Size(crossSize, 12.dp.toPx())
        )
        drawRect(
            color = Color(0xFF2EE59D).copy(alpha = pulse),
            topLeft = Offset(cx - 6.dp.toPx(), cy - crossSize / 2f),
            size = Size(12.dp.toPx(), crossSize)
        )

        // Círculo de escaneo protector
        drawCircle(
            color = Color(0xFF2EE59D).copy(alpha = 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5.dp.toPx())
        )
        // Partículas rojas que se desvanecen
        val rad = Math.toRadians(rotation.toDouble())
        for (i in 0 until 4) {
            val a = rad + i * (Math.PI / 2)
            val px = cx + (radius * 0.6f) * cos(a).toFloat()
            val py = cy + (radius * 0.6f) * sin(a).toFloat()
            drawCircle(
                color = Color(0xFFFF5252).copy(alpha = (1f - pulse)),
                radius = 4.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}

/** Entrenar: rayos dorados que salen de la mascota. */
@Composable
private fun OverclockBurstAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "overclockAnim")
    val rayAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(250), RepeatMode.Reverse),
        label = "rayAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f + 15.dp.toPx()
        val radius = size.width * 0.38f

        // Resplandor radial de alta energía
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFD700).copy(alpha = rayAlpha * 0.5f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius
            ),
            center = Offset(cx, cy),
            radius = radius
        )

        // Relámpagos angulares
        val angles = listOf(30, 75, 120, 165, 210, 255, 300, 345)
        angles.forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val endX = cx + (radius * 0.9f) * cos(rad).toFloat()
            val endY = cy + (radius * 0.9f) * sin(rad).toFloat()
            drawLine(
                color = Color(0xFFFFD700).copy(alpha = rayAlpha),
                start = Offset(cx, cy),
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

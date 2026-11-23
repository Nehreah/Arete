package com.renea.arete_social.gui.tamagotchi

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * La mascota "viva": respira, camina suave, parpadea y reacciona al tacto.
 * Solo dibuja a la mascota y sus efectos; los rótulos (ánimo...) los pone la escena
 * (PetScene) y las necesidades el panel lateral, para que no haya información duplicada encima del dibujo.
 *
 * @param onTap se llama cada vez que la persona completa una tanda de caricias RASCANDO la pantalla
 *   (arrastrar el dedo sobre la mascota). Un simple toque ya no cuenta. No se llama si duerme.
 */
@Composable
fun PetView(
    pet: PetEntity,
    mood: PetMood,
    modifier: Modifier = Modifier,
    activeAnimation: PetActionType? = null,
    onAnimationEnd: () -> Unit = {},
    onTap: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    var rascando by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "petLoop")
    val animTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "animTime"
    )

    val quieta = pet.isSleeping || pet.isCorrupted || pet.stage == PetStage.HUEVO
    val walkX = if (!quieta) sin(animTime * 0.8f) * 12f else 0f
    val bounce = if (!quieta) sin(animTime * 2.4f) * 3.5f else sin(animTime) * 1.5f
    val sway = if (!quieta) sin(animTime * 1.6f) * 2.5f else 0f
    // Un parpadeo corto una vez por ciclo (solo despierta).
    val parpadea = !pet.isSleeping && animTime > 5.95f && animTime < 6.2f

    val jump by animateFloatAsState(
        targetValue = if (isTapped) -22f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "tapJump"
    )

    val aura = when {
        pet.isCorrupted -> PetPalette.NeonMagenta
        pet.isSick -> Color(0xFFFF5252)
        else -> PetPalette.NeonCyan
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pet.isSleeping, pet.isCorrupted) {
                if (!pet.isSleeping && !pet.isCorrupted) {
                    // Rascar: cada ~90 dp recorridos con el dedo cuentan como una tanda de caricias.
                    val distanciaPorTanda = 90.dp.toPx()
                    var recorrido = 0f
                    detectDragGestures(
                        onDragStart = { recorrido = 0f; rascando = true },
                        onDragEnd = { rascando = false },
                        onDragCancel = { rascando = false },
                        onDrag = { change, delta ->
                            change.consume()
                            recorrido += delta.getDistance()
                            if (recorrido >= distanciaPorTanda) {
                                recorrido = 0f
                                onTap()
                                scope.launch {
                                    isTapped = true
                                    delay(450)
                                    isTapped = false
                                }
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Aura de neón que late suavemente detrás de la mascota.
        Box(
            modifier = Modifier
                .size(85.dp)
                .graphicsLayer {
                    alpha = 0.26f + sin(animTime * 2f) * 0.10f
                    val pulso = 1f + sin(animTime) * 0.08f
                    scaleX = pulso
                    scaleY = pulso
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(aura, aura.copy(alpha = 0.12f), Color.Transparent)))
        )

        PetCanvas(
            pet = pet,
            animTime = animTime,
            isBlinking = parpadea,
            mood = mood,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(walkX.dp.roundToPx(), (bounce + jump).dp.roundToPx()) }
                .graphicsLayer { rotationZ = sway }
        )

        if (!pet.isCorrupted) PetSparkles(animTime)

        // Reacción a las caricias: mientras la rascas se ve un corazón.
        if (isTapped || rascando) {
            Text(
                text = "♥",
                color = PetPalette.NeonMagenta,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).offset(y = (-62).dp)
            )
        }

        if (pet.isSleeping) SleepingZs(animTime)

        PetActionOverlay(actionType = activeAnimation, onAnimationEnd = onAnimationEnd)
    }
}

/** Destellos de 4 puntas que titilan alrededor de la mascota (sustituyen a los emojis sueltos). */
@Composable
private fun PetSparkles(animTime: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val puntos = listOf(
            Triple(0.22f, 0.22f, PetPalette.NeonCyan),
            Triple(0.80f, 0.28f, PetPalette.NeonMagenta),
            Triple(0.86f, 0.62f, PetPalette.NeonGold),
            Triple(0.16f, 0.58f, PetPalette.NeonMint)
        )
        puntos.forEachIndexed { i, (fx, fy, color) ->
            val fase = sin(animTime * 1.5f + i * 1.7f)
            val alpha = (0.35f + 0.45f * fase).coerceIn(0.1f, 0.85f)
            val r = (3.dp.toPx() + 2.dp.toPx() * fase.coerceAtLeast(0f))
            val cx = size.width * fx
            val cy = size.height * fy
            val star = Path().apply {
                moveTo(cx, cy - r * 2f)
                lineTo(cx + r * 0.5f, cy - r * 0.5f)
                lineTo(cx + r * 2f, cy)
                lineTo(cx + r * 0.5f, cy + r * 0.5f)
                lineTo(cx, cy + r * 2f)
                lineTo(cx - r * 0.5f, cy + r * 0.5f)
                lineTo(cx - r * 2f, cy)
                lineTo(cx - r * 0.5f, cy - r * 0.5f)
                close()
            }
            drawPath(star, color = color.copy(alpha = alpha))
        }
    }
}

/** "z Z z" que flota hacia arriba mientras duerme. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SleepingZs(animTime: Float) {
    val subida = (animTime / 6.283185f)
    listOf(0f, 0.33f, 0.66f).forEachIndexed { i, desfase ->
        val t = (subida + desfase) % 1f
        Text(
            text = if (i == 1) "Z" else "z",
            color = PetPalette.NeonCyan.copy(alpha = (1f - t).coerceIn(0f, 0.9f)),
            fontSize = (14 + i * 3).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (30 + i * 10).dp, y = (-56 - t * 34f).dp)
        )
    }
}

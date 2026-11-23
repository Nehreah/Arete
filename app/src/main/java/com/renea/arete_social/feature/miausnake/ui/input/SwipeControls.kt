package com.renea.arete_social.feature.miausnake.ui.input

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.renea.arete_social.feature.miausnake.core.Direction

/**
 * Swipe sobre toda el área donde se aplica el modificador (umbral recomendado ≈ 24 dp, convertido
 * a px por quien llama). Los giros son encadenables. Solo emite comandos de dirección: no toca el modelo.
 */
@Composable
fun Modifier.swipeControls(
    thresholdPx: Float,
    enabled: Boolean = true,
    onDirection: (Direction) -> Unit
): Modifier {
    val current by rememberUpdatedState(onDirection)
    if (!enabled) return this
    return pointerInput(thresholdPx) {
        val acc = SwipeAccumulator(thresholdPx)
        detectDragGestures(
            onDragStart = { acc.reset() },
            onDragEnd = { acc.reset() },
            onDragCancel = { acc.reset() }
        ) { change, drag ->
            change.consume()
            acc.add(drag.x, drag.y)?.let { current(it) }
        }
    }
}

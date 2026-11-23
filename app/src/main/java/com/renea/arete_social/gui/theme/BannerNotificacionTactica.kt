package com.renea.arete_social.gui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.model.NotificacionTactica
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BannerNotificacionTactica(
    notif: NotificacionTactica?,
    onDescartar: () -> Unit,
    onAbrir: (NotificacionTactica) -> Unit,
    modifier: Modifier = Modifier
) {
    var ultima by remember { mutableStateOf<NotificacionTactica?>(null) }
    if (notif != null) {
        ultima = notif
    }

    LaunchedEffect(notif?.id) {
        if (notif != null) {
            delay(10_000L)
            onDescartar()
        }
    }

    val umbral = with(LocalDensity.current) { 48.dp.toPx() }

    AnimatedVisibility(
        visible = notif != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        var dx by remember(notif?.id) { mutableStateOf(0f) }
        var dy by remember(notif?.id) { mutableStateOf(0f) }

        ultima?.let { n ->
            val radarColors = LocalRadarColors.current
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(dx.roundToInt(), dy.coerceAtMost(0f).roundToInt()) }
                    .pointerInput(n.id) {
                        detectDragGestures(
                            onDragEnd = {
                                if (dy < -umbral || abs(dx) > umbral * 1.5f) {
                                    onDescartar()
                                } else {
                                    dx = 0f
                                    dy = 0f
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dx += dragAmount.x
                                dy += dragAmount.y
                            }
                        )
                    }
                    .clickable {
                        onAbrir(n)
                        onDescartar()
                    },
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, radarColors.verde.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = n.remitenteNombre.firstOrNull()?.uppercase() ?: "A",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = n.titulo,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = n.subtitulo,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

package com.renea.arete_social.gui.tamagotchi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette

// ============================================================================
// Piezas reutilizables de la pantalla de la mascota. Todas leen los colores del
// tema de Aréte (claro/oscuro) y usan la misma geometría que el resto de pantallas
// (tarjetas de superficie, borde fino, esquinas de 16 dp).
// ============================================================================

/** Radio de las tarjetas, igual que RadarCard. */
internal val RADIO_TARJETA = 16.dp

/** "12:05" para esperas cortas, "1 h 05 min" para las largas. */
internal fun formatearEspera(segundos: Long): String {
    val s = segundos.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min"
    else "$m:${sec.toString().padStart(2, '0')}"
}

/**
 * Una necesidad como círculo pequeño que se llena de 0 a 100 %: ícono al centro y porcentaje debajo.
 * Pensado para ir en fila, sobre el fondo oscuro del escenario. Se pone roja si es crítica.
 */
@Composable
internal fun PetStatRing(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 26.dp,
    icon: @Composable (Color) -> Unit
) {
    val v = value.coerceIn(0, 100)
    val progress by animateFloatAsState(targetValue = v / 100f, animationSpec = tween(500), label = "stat-$label")
    val critica = v <= PetBalance.CRITICAL_THRESHOLD
    val tono = if (critica) MaterialTheme.colorScheme.error else color

    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $v por ciento" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val grosor = 3.dp.toPx()
                val margen = grosor / 2f
                val tamano = Size(size.width - grosor, size.height - grosor)
                val origen = Offset(margen, margen)
                drawArc(
                    color = tono.copy(alpha = 0.25f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = origen, size = tamano, style = Stroke(width = grosor)
                )
                if (progress > 0.005f) {
                    drawArc(
                        color = tono,
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        topLeft = origen, size = tamano,
                        style = Stroke(width = grosor, cap = StrokeCap.Round)
                    )
                }
            }
            icon(tono)
        }
        Text(
            text = "$v%",
            color = if (critica) tono else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Fila horizontal y compacta con las cinco necesidades. Va en la franja superior del escenario,
 * entre el botón de estado y el de cambiar fondo, directamente sobre el fondo (sin cápsula).
 */
@Composable
internal fun PetStatsBar(
    pet: PetEntity,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PetStatRing("Comida", pet.hunger, PetPalette.Hunger) { PetIcons.Feed(Modifier.size(12.dp), it) }
        PetStatRing("Energía", pet.energy, PetPalette.Energy) { PetIcons.Battery(Modifier.size(12.dp), it) }
        PetStatRing("Ánimo", pet.happiness, PetPalette.Mood) { PetIcons.Heart(Modifier.size(12.dp), it) }
        PetStatRing("Limpieza", pet.hygiene, PetPalette.Hygiene) { PetIcons.Clean(Modifier.size(12.dp), it) }
        PetStatRing("Salud", pet.health, PetPalette.Health) { PetIcons.Heal(Modifier.size(12.dp), it) }
    }
}

/** Barra fina de progreso (experiencia, incubación...). */
@Composable
internal fun PetThinBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(500), label = "thinBar")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

/** Borde que late suavemente (0.45 → 1) para llamar la atención sin ser molesto. */
@Composable
private fun alfaLatido(activo: Boolean): Float {
    if (!activo) return 1f
    val transicion = rememberInfiniteTransition(label = "latido")
    val alfa by transicion.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "latido-alfa"
    )
    return alfa
}

/**
 * Botón circular de cuidado que vive dentro del escenario (sobre fondo oscuro): círculo con
 * ícono, nombre y una línea de apoyo (cuenta atrás o motivo) de alto fijo, para que los tres
 * queden siempre alineados.
 *
 * Siempre se puede tocar: si en ese momento no se puede hacer, la pantalla explica por qué
 * con un mensaje (en vez de un botón muerto). [disponible] solo cambia el aspecto;
 * [destacado] hace latir el borde cuando la mascota lo necesita ahora.
 */
@Composable
internal fun PetCircleAction(
    label: String,
    hint: String?,
    disponible: Boolean,
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
    acentoBase: Color = PetPalette.NeonCyan,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val resalta = destacado && disponible
    val acento = if (resalta) PetPalette.NeonGold else acentoBase
    val latido = alfaLatido(resalta)
    val colorIcono = if (disponible) acento else Color.White.copy(alpha = 0.45f)
    val borde = if (disponible) acento.copy(alpha = if (resalta) latido else 0.75f) else Color.White.copy(alpha = 0.2f)

    Column(
        modifier = modifier.width(58.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xCC0B1220))
                .border(if (resalta) 2.dp else 1.5.dp, borde, CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { icon(colorIcono) }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (disponible) 1f else 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
            if (hint != null) {
                Text(
                    text = hint,
                    color = if (resalta) PetPalette.NeonGold else Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Símbolo del estado actual (cambia según el ánimo). El ícono de curar queda solo para el botón Curar. */
@Composable
private fun IconoEstado(mood: PetMood, corrupta: Boolean, tamano: Dp) {
    Text(
        text = if (corrupta) "📴" else mood.badgeEmoji,
        fontSize = (tamano.value * 0.85f).sp,
        textAlign = TextAlign.Center
    )
}

/**
 * Botón redondo de estado: su icono cambia según cómo está la mascota. Al tocarlo se despliega
 * la lectura del estado ([PetEstadoPanel]). Late si está enferma o fuera de línea.
 */
@Composable
internal fun PetStatusButton(
    mood: PetMood,
    corrupta: Boolean,
    abierto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alerta = corrupta || mood == PetMood.ENFERMO
    val color = if (corrupta) PetPalette.NeonMagenta else colorDeAnimo(mood)
    val latido = alfaLatido(alerta && !abierto)
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (abierto) color.copy(alpha = 0.28f) else Color(0xCC0B1220))
            .border(if (alerta) 2.dp else 1.5.dp, color.copy(alpha = if (alerta) latido else 0.8f), CircleShape)
            .semantics { contentDescription = "Estado de la mascota" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { IconoEstado(mood, corrupta, 20.dp) }
}

/**
 * Lectura del estado que se despliega bajo el escenario: cómo está, rasgos y, si hace falta,
 * la acción (Curar / Reiniciar). Vive en el flujo de la pantalla, sin tapar a la mascota.
 */
@Composable
internal fun PetEstadoPanel(
    visible: Boolean,
    titulo: String,
    texto: String,
    mood: PetMood,
    corrupta: Boolean,
    rasgos: List<Pair<String, String>>,
    accion: String?,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (corrupta) PetPalette.NeonMagenta else colorDeAnimo(mood)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(250)) + fadeIn(tween(250)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
    ) {
        val forma = RoundedCornerShape(RADIO_TARJETA)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(forma)
                .background(scheme.surface)
                .border(1.dp, color.copy(alpha = 0.7f), forma)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { IconoEstado(mood, corrupta, 16.dp) }
                Spacer(Modifier.width(10.dp))
                Text(titulo, color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(texto, color = scheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            if (rasgos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Rasgos", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rasgos.forEach { (nombre, descripcion) ->
                        Column {
                            PetPill(nombre, PetPalette.Energy)
                            Spacer(Modifier.height(3.dp))
                            Text(descripcion, color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (accion != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color)
                        .clickable(role = Role.Button, onClick = onAccion)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(accion, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
    }
}

/** Etiqueta pequeña redondeada (etapa, turbo, rasgo). */
@Composable
internal fun PetPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

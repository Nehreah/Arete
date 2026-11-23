package com.renea.arete_social.gui.tamagotchi

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.R
import com.renea.arete_social.feature.tamagotchi.logic.modoSuperActivo
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@DrawableRes
private val fondoPredeterminadoMascota = R.drawable.bg_pixel_028

/** Alto total del escenario Cyberpunk. */
internal val ALTO_ESCENARIO = 485.dp

/**
 * Forma poligonal Cyberpunk con cortes achaflanados a 45 grados en las 4 esquinas.
 */
class CyberChamferShape(private val chamfer: Dp = 18.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { chamfer.toPx() }.coerceAtMost(size.minDimension / 4f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(0f, h - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Escenario de la mascota: fondo ilustrado con franjas que nunca se superponen:
 *  - arriba, el botón de estado (ánimo, rasgos), las necesidades en fila y el botón para cambiar de fondo,
 *  - en el centro, la mascota y, sobre ella a la izquierda, el botón «Cuidar» (despliega los cuidados
 *    hacia la derecha, en filas de cinco),
 *  - luego, lo que la mascota dice (el espacio está siempre reservado),
 *  - la barra de nivel al pie.
 *
 */
@Composable
internal fun PetScene(
    pet: PetEntity,
    mood: PetMood,
    mensaje: String?,
    activeAnimation: PetActionType?,
    onAnimationEnd: () -> Unit,
    onCuddle: () -> Unit,
    modifier: Modifier = Modifier,
    /** True = se muestran los cuidados (fila inferior). False = visitas, huevo o fuera de línea. */
    conCuidados: Boolean = false,
    /** Botón redondo de estado e información (arriba a la izquierda). */
    accionEstado: @Composable () -> Unit = {},
    /** Barra de nivel al pie de la escena. */
    barraNivel: @Composable () -> Unit = {},
    /** Botón «Cuidar» a la izquierda que despliega los cuidados hacia la derecha. Solo si [conCuidados]. */
    accionesLaterales: @Composable () -> Unit = {}
) {

    // Se conserva el último texto para que el globo no quede vacío mientras se desvanece.
    var ultimoMensaje by remember { mutableStateOf("") }
    LaunchedEffect(mensaje) { if (mensaje != null) ultimoMensaje = mensaje }

    // Modo Súper: aura dorada mientras dure. El reloj propio hace que se apague a tiempo aunque nada más cambie.
    val ahoraSuper by produceState(System.currentTimeMillis(), pet.superDesdeMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30.seconds)
        }
    }
    val superActivo = pet.modoSuperActivo(ahoraSuper)
    val esOscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Colores del marco Cyberpunk adaptables al tema claro, oscuro o súper
    val colorCyber = when {
        superActivo -> PetPalette.NeonGold // 0xFFFFD700
        esOscuro -> Color(0xFF00E5FF)       // Neón Cyan Eléctrico
        else -> Color(0xFF00695C)          // Deep Cyber Teal de alto contraste
    }
    val colorCyberSecundario = when {
        superActivo -> Color(0xFFFFB300)   // Oro ámbar
        esOscuro -> Color(0xFF00FF9D)       // Verde Matrix Neón
        else -> Color(0xFF0277BD)          // Azul Cobalto Táctico
    }

    val chamferExterior = 18.dp
    val chamferInterior = 15.dp
    val formaExterior = remember { CyberChamferShape(chamferExterior) }
    val formaInterior = remember { CyberChamferShape(chamferInterior) }

    Box(
        modifier = modifier
            .clip(formaExterior)
            .background(colorCyber.copy(alpha = if (esOscuro) 0.12f else 0.08f))
            .padding(4.dp)
    ) {
        // Escenario interior con las imágenes de fondo y controles
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(formaInterior)
                .background(Brush.verticalGradient(listOf(PetPalette.SceneTop, PetPalette.SceneBottom)))
        ) {
            Image(
                painter = painterResource(id = fondoPredeterminadoMascota),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Velo para que los rótulos siempre se lean sobre cualquier fondo pixel art.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x7308111C), Color(0x2608111C), Color(0xBF08111C))
                    )
                )
            )

            if (superActivo) {
                // Brillo dorado detrás de Michi (Modo Súper).
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(PetPalette.NeonGold.copy(alpha = 0.35f), Color.Transparent))
                    )
                )
            }

            // Organización jerárquica de elementos en 5 Tiers
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 6.dp)
            ) {
                // ── Tier 1: Consola Superior del Visor ───────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Izquierda: Botón de estado de ánimo
                    accionEstado()

                    // Centro: Cyber HUD Badge de estado
                    CyberHudBadge(
                        superActivo = superActivo,
                        color = colorCyber,
                        colorSecundario = colorCyberSecundario,
                        esOscuro = esOscuro
                    )

                    // Derecha: Espaciador para balance simétrico del HUD
                    Spacer(modifier = Modifier.size(36.dp))
                }

                // ── Tier 2: Muelle de Telemetría de Necesidades (HUD Vital Stats Dock) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (esOscuro) Color(0x99060E18) else Color(0xDDF0FDF4))
                        .border(0.9.dp, colorCyber.copy(alpha = if (esOscuro) 0.35f else 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pet.stage != PetStage.HUEVO) {
                        PetStatsBar(pet, modifier = Modifier.fillMaxWidth())
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colorCyberSecundario)
                            )
                            Text(
                                text = "FASE DE INCUBACIÓN // TELEMETRÍA EN ESPERA",
                                color = colorCyber,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // ── Tier 3: Hábitat Central (Michi + Cuidados) ───────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Suelo holográfico con haz de luz
                    Canvas(Modifier.fillMaxSize()) {
                        val petSize = (size.minDimension * 0.14f).coerceAtLeast(15f)
                        val y = size.height / 2f + petSize * 1.07f
                        val x0 = size.width * 0.12f
                        val x1 = size.width * 0.88f
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, colorCyber.copy(alpha = 0.85f), Color.Transparent),
                                startX = x0, endX = x1
                            ),
                            start = Offset(x0, y), end = Offset(x1, y),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, colorCyberSecundario.copy(alpha = 0.4f), Color.Transparent),
                                startX = x0 + 20f, endX = x1 - 20f
                            ),
                            start = Offset(x0 + 20f, y + 4f), end = Offset(x1 - 20f, y + 4f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    PetView(
                        pet = pet,
                        mood = mood,
                        activeAnimation = activeAnimation,
                        onAnimationEnd = onAnimationEnd,
                        onTap = onCuddle
                    )

                    if (conCuidados) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 4.dp)
                        ) {
                            accionesLaterales()
                        }
                    }
                }

                // ── Tier 4: Transmisión de Diálogo de Michi (Globo HUD) ─────
                GloboMensaje(
                    mensaje = mensaje,
                    ultimoMensaje = ultimoMensaje,
                    colorCyber = colorCyber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )

                // ── Tier 5: Base de Consola (Barra de Nivel y XP) ────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(if (esOscuro) Color(0xCC060C14) else Color(0xE6F0FDF4))
                        .border(
                            width = 1.dp,
                            color = colorCyber.copy(alpha = if (esOscuro) 0.35f else 0.55f),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    barraNivel()
                }
            }
        }

        // ── Overlay del Marco Cyberpunk HUD (Brackets angulados mecha, remaches y retículos) ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val c = chamferExterior.toPx()
            val bracketLen = 22.dp.toPx()
            val strokeThick = 2.8.dp.toPx()
            val strokeThin = 1.dp.toPx()

            // 1. Contorno perimetral achaflanado exterior
            val pathOuter = Path().apply {
                moveTo(c, 0f)
                lineTo(w - c, 0f)
                lineTo(w, c)
                lineTo(w, h - c)
                lineTo(w - c, h)
                lineTo(c, h)
                lineTo(0f, h - c)
                lineTo(0f, c)
                close()
            }
            if (esOscuro || superActivo) {
                drawPath(pathOuter, color = colorCyber.copy(alpha = 0.25f), style = Stroke(width = 5.dp.toPx()))
            }
            drawPath(pathOuter, color = colorCyber.copy(alpha = if (esOscuro) 0.85f else 0.95f), style = Stroke(width = 1.8.dp.toPx()))

            // 2. Heavy Corner Armor Brackets con Remaches
            // Top-Left
            val brTL = Path().apply {
                moveTo(0f, c + bracketLen)
                lineTo(0f, c)
                lineTo(c, 0f)
                lineTo(c + bracketLen, 0f)
            }
            drawPath(brTL, color = colorCyberSecundario, style = Stroke(width = strokeThick, cap = StrokeCap.Square))
            drawCircle(color = colorCyberSecundario, radius = 2.5.dp.toPx(), center = Offset(c * 0.42f, c * 0.42f))
            drawCircle(color = Color.White, radius = 1.dp.toPx(), center = Offset(c * 0.42f, c * 0.42f))

            // Top-Right
            val brTR = Path().apply {
                moveTo(w - c - bracketLen, 0f)
                lineTo(w - c, 0f)
                lineTo(w, c)
                lineTo(w, c + bracketLen)
            }
            drawPath(brTR, color = colorCyberSecundario, style = Stroke(width = strokeThick, cap = StrokeCap.Square))
            drawCircle(color = colorCyberSecundario, radius = 2.5.dp.toPx(), center = Offset(w - c * 0.42f, c * 0.42f))
            drawCircle(color = Color.White, radius = 1.dp.toPx(), center = Offset(w - c * 0.42f, c * 0.42f))

            // Bottom-Left
            val brBL = Path().apply {
                moveTo(0f, h - c - bracketLen)
                lineTo(0f, h - c)
                lineTo(c, h)
                lineTo(c + bracketLen, h)
            }
            drawPath(brBL, color = colorCyberSecundario, style = Stroke(width = strokeThick, cap = StrokeCap.Square))
            drawCircle(color = colorCyberSecundario, radius = 2.5.dp.toPx(), center = Offset(c * 0.42f, h - c * 0.42f))
            drawCircle(color = Color.White, radius = 1.dp.toPx(), center = Offset(c * 0.42f, h - c * 0.42f))

            // Bottom-Right
            val brBR = Path().apply {
                moveTo(w - c - bracketLen, h)
                lineTo(w - c, h)
                lineTo(w, h - c)
                lineTo(w, h - c - bracketLen)
            }
            drawPath(brBR, color = colorCyberSecundario, style = Stroke(width = strokeThick, cap = StrokeCap.Square))
            drawCircle(color = colorCyberSecundario, radius = 2.5.dp.toPx(), center = Offset(w - c * 0.42f, h - c * 0.42f))
            drawCircle(color = Color.White, radius = 1.dp.toPx(), center = Offset(w - c * 0.42f, h - c * 0.42f))

            // 3. Bisel de Visera Superior (Top Visor Tab con Hazard Hashes ///)
            val tabW = 60.dp.toPx()
            val tabH = 4.dp.toPx()
            val midX = w / 2f
            val visorPath = Path().apply {
                moveTo(midX - tabW / 2f, 0f)
                lineTo(midX - tabW / 2f + tabH, tabH)
                lineTo(midX + tabW / 2f - tabH, tabH)
                lineTo(midX + tabW / 2f, 0f)
                close()
            }
            drawPath(visorPath, color = colorCyber.copy(alpha = 0.4f))
            drawPath(visorPath, color = colorCyber, style = Stroke(width = 1.2.dp.toPx()))

            for (i in -1..1) {
                val slashX = midX + i * 8.dp.toPx()
                drawLine(
                    color = colorCyberSecundario,
                    start = Offset(slashX - 2.dp.toPx(), 1.dp.toPx()),
                    end = Offset(slashX + 2.dp.toPx(), tabH - 1.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 4. Reglas de calibración lateral y retículos de mira
            val midY = h * 0.5f
            val tickShort = 5.dp.toPx()
            val tickLong = 9.dp.toPx()

            // Izquierda
            drawLine(color = colorCyber.copy(alpha = 0.6f), start = Offset(0f, h * 0.28f), end = Offset(tickShort, h * 0.28f), strokeWidth = strokeThin)
            drawLine(color = colorCyberSecundario, start = Offset(0f, midY), end = Offset(tickLong, midY), strokeWidth = 1.5.dp.toPx())
            drawLine(color = colorCyberSecundario, start = Offset(tickLong / 2f, midY - 3.dp.toPx()), end = Offset(tickLong / 2f, midY + 3.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawLine(color = colorCyber.copy(alpha = 0.6f), start = Offset(0f, h * 0.72f), end = Offset(tickShort, h * 0.72f), strokeWidth = strokeThin)

            // Derecha
            drawLine(color = colorCyber.copy(alpha = 0.6f), start = Offset(w, h * 0.28f), end = Offset(w - tickShort, h * 0.28f), strokeWidth = strokeThin)
            drawLine(color = colorCyberSecundario, start = Offset(w, midY), end = Offset(w - tickLong, midY), strokeWidth = 1.5.dp.toPx())
            drawLine(color = colorCyberSecundario, start = Offset(w - tickLong / 2f, midY - 3.dp.toPx()), end = Offset(w - tickLong / 2f, midY + 3.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawLine(color = colorCyber.copy(alpha = 0.6f), start = Offset(w, h * 0.72f), end = Offset(w - tickShort, h * 0.72f), strokeWidth = strokeThin)
        }

        // Micro-etiquetas grabadas en la base del chasis
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 1.5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYS: ACTIVE // LINK-OK",
                color = colorCyber.copy(alpha = if (esOscuro) 0.6f else 0.8f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "NET: 2.4GHz // CHIP-V2",
                color = colorCyber.copy(alpha = if (esOscuro) 0.6f else 0.8f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

/**
 * Badge superior con indicador de estado pulsante tipo HUD.
 */
@Composable
private fun CyberHudBadge(
    superActivo: Boolean,
    color: Color,
    colorSecundario: Color,
    esOscuro: Boolean
) {
    val transicion = rememberInfiniteTransition(label = "hud-pulse")
    val alfaPunto by transicion.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "hud-punto-alfa"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (esOscuro) Color(0xCC060E18) else Color(0xDDF0FDF4))
            .border(1.dp, color.copy(alpha = if (esOscuro) 0.6f else 0.8f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colorSecundario.copy(alpha = alfaPunto))
        )
        Text(
            text = if (superActivo) "SÚPER // MODO ACTIVO" else "MICHI-OS // HABITAT-V2",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Globo de diálogo con lo que dice la mascota, presentado como transmisión de terminal cyber.
 */
@Composable
private fun GloboMensaje(
    mensaje: String?,
    ultimoMensaje: String,
    colorCyber: Color,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = mensaje != null,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xE608121E))
                .border(1.dp, colorCyber.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetIcons.Chat(Modifier.size(15.dp), colorCyber)
            Spacer(Modifier.width(8.dp))
            Text(
                text = mensaje ?: ultimoMensaje,
                color = Color.White,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun colorDeAnimo(mood: PetMood): Color = when (mood) {
    PetMood.FELIZ -> PetPalette.NeonMint
    PetMood.NORMAL -> PetPalette.NeonCyan
    PetMood.DORMIDO -> Color(0xFF81D4FA)
    PetMood.ENFERMO -> Color(0xFFFF5252)
    PetMood.HAMBRIENTO -> PetPalette.NeonGold
    PetMood.SUCIO -> Color(0xFF8BC34A)
    PetMood.CANSADO -> Color(0xFF9575CD)
    PetMood.TRISTE -> Color(0xFF64B5F6)
}

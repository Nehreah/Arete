package com.renea.arete_social.gui.tamagotchi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderizado visual procedural de alta fidelidad en Compose Canvas para la mascota virtual Cybersolarpunk.
 * Implementa estrictamente el diseño de 'rediseño mascota.png' y las especificaciones de 'Mimi_Plan_Rediseno_Mascota.md':
 *
 * 1. Evolución por 5 etapas (PetStage):
 *    - HUEVO: Cápsula de datos con pulsación bioluminiscente, red de circuitos y pedestal metálico.
 *    - BEBÉ: Proporciones tiernas de cría, solo un brote sutil de antena con LED cian (sin visor ni placas).
 *    - NIÑO: Silueta juvenil, antena BLE con anillos concéntricos de escaneo neón y Visor Ocular Digital.
 *    - ADULTO: Visor Ocular, Antena BLE, Núcleo de sincronización en pecho, Placas dorsales (1-4) y Almohadillas de tracción.
 *    - ANCIANO: Traje cyber completo con capa/manto dorsal, pátina dorada y transición lumínica a dorado/ámbar cálido.
 *
 * 2. Las 6 partes cyber (drawCyberPartsOverlay):
 *    - Visor Ocular con HUD y cambio de color emocional (PetMood).
 *    - Núcleo de Sincronización con pulsación rítmica según happiness.
 *    - Antena de escaneo BLE con ondas de emisión radial.
 *    - Placas dorsales segmentadas que se iluminan progresivamente según neuralLevel.
 *    - Almohadillas de tracción en las patas con micro-chispas.
 *    - Fibra óptica / Modo turbo (al despertar tras un buen descanso) con aura magenta radiante (#FF0055).
 *
 * 3. Estados y expresiones (PetMood):
 *    - Feliz (#00FFAA), Normal (#00E5FF), Hambriento (#FFD600), Sucio (#8BC34A),
 *      Triste (#448AFF), Cansado (#7E57C2), Enfermo (#FF1744), Dormido (Standby) y Corrupción Glitch.
 */
@Composable
fun PetCanvas(
    pet: PetEntity,
    animTime: Float,
    isBlinking: Boolean,
    mood: PetMood = PetMood.from(pet),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val baseCy = size.height / 2f
        val petSize = (size.minDimension * 0.14f).coerceAtLeast(15f)

        // Animación de respiración / rebote según estado
        val bounce = if (pet.isSleeping) {
            sin(animTime * 1.6f) * (petSize * 0.025f)
        } else {
            sin(animTime * 3.2f) * (petSize * 0.065f)
        }
        val cy = baseCy + bounce

        // Sombra en el suelo con gradiente suave
        val shadowWidth = when (pet.stage) {
            PetStage.HUEVO -> petSize * 1.3f
            PetStage.BEBE -> petSize * 1.2f
            PetStage.NINO -> petSize * 1.5f
            PetStage.ADULTO, PetStage.ANCIANO -> petSize * 1.9f
        }
        drawOval(
            color = Color(0x3D020805),
            topLeft = Offset(cx - shadowWidth / 2f, baseCy + petSize * 0.88f),
            size = Size(shadowWidth, petSize * 0.38f)
        )

        // Corrupción de datos: Glitch visual de jitter, aberración cromática y ruido digital
        if (pet.health <= 0 || pet.isCorrupted) {
            drawCorruptedGlitch(cx, cy, petSize, animTime, pet.petType, pet.stage, mood)
            return@Canvas
        }

        when (pet.stage) {
            PetStage.HUEVO -> {
                drawEgg(cx, cy, petSize, animTime)
            }
            else -> {
                // 1. Cuerpo orgánico base según la especie y etapa
                when (pet.petType) {
                    PetType.GATO -> drawCat(cx, cy, petSize, pet.stage, animTime, isBlinking, pet.isSleeping, mood)
                    PetType.PERRO -> drawDog(cx, cy, petSize, pet.stage, animTime, isBlinking, pet.isSleeping, mood)
                    PetType.ZORRO -> drawFox(cx, cy, petSize, pet.stage, animTime, isBlinking, pet.isSleeping, mood)
                    PetType.CONEJO -> drawRabbit(cx, cy, petSize, pet.stage, animTime, isBlinking, pet.isSleeping, mood)
                }
                // 2. Overlay de las 6 partes cyber progresivas
                drawCyberPartsOverlay(cx, cy, petSize, pet, mood, animTime)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PALETA DE COLORES CYBERSOLARPUNK OFICIAL ('rediseño mascota.png')
// ─────────────────────────────────────────────────────────────────────────────

private object CyberColors {
    // Pelaje Felino Cybersolarpunk
    val FurPrimary = Color(0xFF474A72)       // Azul-violeta espacial pizarra (ref: filas 1-3 hoja de sprites)
    val FurShadow = Color(0xFF2A2C48)        // Sombra / marcas oscuras
    val FurCheeks = Color(0xFF666999)        // Mejillas y hocico
    val InnerEar = Color(0xFFFF80AB)         // Rosa interior suave
    val NosePink = Color(0xFFFF4081)         // Nariz vibrante
    val Whiskers = Color(0xFF9FA8DA)         // Bigotes con reflejo

    // Elementos Cyber Estándar — alineados a la paleta CyberSolarPunk del
    // Plan de Fidelidad Visual v2, sección 2.1:
    val NeonMint = Color(0xFF4ADE80)         // Verde menta: visor, placas (antes 0xFF00FFCC)
    val NeonCyan = Color(0xFF2DD4BF)         // Teal: núcleo de sincronización (antes 0xFF00E5FF)
    val EarBlue = Color(0xFF5B9FE0)          // Azul: oreja biomecánica
    val PawAmber = Color(0xFFF0B94A)         // Ámbar: pata biomecánica / almohadillas
    val FurAccentOrange = Color(0xFFE8823C)  // Naranja: acentos de pelaje (marcas tipo tabby)
    val TailMagenta = Color(0xFFE85FA0)      // Rosa/magenta: cola de energía

    // Elementos Cyber Anciano (Pátina Dorada / Ámbar Cálido) — filas 3-4 de la hoja de sprites
    val GoldPrimary = Color(0xFFFFD700)
    val AmberGlow = Color(0xFFFFA000)
    val BrassTrim = Color(0xFFB58D3D)
    val CapeBase = Color(0xFF382E1E)
    val CapeTrim = Color(0xFFFFC107)

    // Overclock Cuántico
    val OverclockMagenta = Color(0xFFFF0055)
    val OverclockElectric = Color(0xFFFF4081)

    /**
     * Color del Visor Ocular y ojos según el estado emocional (PetMood)
     */
    fun forMood(mood: PetMood, isElder: Boolean): Color {
        if (isElder && mood != PetMood.ENFERMO) {
            return when (mood) {
                PetMood.FELIZ -> Color(0xFFFFE082)
                PetMood.HAMBRIENTO -> Color(0xFFFFB300)
                PetMood.SUCIO -> Color(0xFFC0CA33)
                PetMood.TRISTE, PetMood.CANSADO -> Color(0xFFB0BEC5)
                PetMood.DORMIDO -> Color(0x66FFD700)
                PetMood.NORMAL -> GoldPrimary
                PetMood.ENFERMO -> Color(0xFFFF1744)
            }
        }
        return when (mood) {
            PetMood.FELIZ -> Color(0xFF00FFAA)
            PetMood.NORMAL -> NeonMint
            PetMood.HAMBRIENTO -> Color(0xFFFFD600)
            PetMood.SUCIO -> Color(0xFF8BC34A)
            PetMood.TRISTE -> Color(0xFF448AFF)
            PetMood.CANSADO -> Color(0xFF7E57C2)
            PetMood.ENFERMO -> Color(0xFFFF1744)
            PetMood.DORMIDO -> Color(0x664ADE80)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 1: HUEVO (CÁPSULA DE DATOS BIOLUMINISCENTE)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawEgg(cx: Float, cy: Float, size: Float, animTime: Float) {
    val rock = sin(animTime * 3.5f) * (size * 0.05f)
    val eggX = cx + rock

    // 1. Anillos de contención y campo de datos holográfico exterior
    val pulseHalo = 1.0f + sin(animTime * 4.5f) * 0.08f
    drawCircle(
        color = Color(0x2200E5FF),
        radius = size * 1.18f * pulseHalo,
        center = Offset(eggX, cy)
    )
    drawCircle(
        color = CyberColors.NeonCyan.copy(alpha = 0.45f),
        radius = size * 1.08f * pulseHalo,
        center = Offset(eggX, cy),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // 2. Pedestal metálico tecnológico de soporte inferior
    val baseW = size * 1.15f
    val baseH = size * 0.32f
    val baseY = cy + size * 0.65f
    drawRoundRect(
        color = Color(0xFF16243B),
        topLeft = Offset(eggX - baseW / 2f, baseY),
        size = Size(baseW, baseH),
        cornerRadius = CornerRadius(baseH / 2f, baseH / 2f)
    )
    drawRoundRect(
        color = Color(0xFF243A5E),
        topLeft = Offset(eggX - baseW * 0.42f, baseY + 2.dp.toPx()),
        size = Size(baseW * 0.84f, baseH * 0.45f),
        cornerRadius = CornerRadius(baseH / 4f, baseH / 4f)
    )
    // LEDs de estado en el pedestal
    val ledAlpha = 0.6f + sin(animTime * 6f) * 0.4f
    drawCircle(
        color = CyberColors.NeonMint.copy(alpha = ledAlpha),
        radius = 3.dp.toPx(),
        center = Offset(eggX - baseW * 0.28f, baseY + baseH * 0.6f)
    )
    drawCircle(
        color = CyberColors.NeonCyan.copy(alpha = ledAlpha),
        radius = 3.dp.toPx(),
        center = Offset(eggX, baseY + baseH * 0.6f)
    )
    drawCircle(
        color = CyberColors.NeonMint.copy(alpha = ledAlpha),
        radius = 3.dp.toPx(),
        center = Offset(eggX + baseW * 0.28f, baseY + baseH * 0.6f)
    )

    // 3. Cáscara de la Cápsula Cyber (Ovoide oscuro de datos)
    val eggWidth = size * 1.35f
    val eggHeight = size * 1.78f
    val eggTopLeft = Offset(eggX - eggWidth / 2f, cy - eggHeight / 2f - size * 0.05f)

    // Cuerpo exterior con gradiente espacial
    drawOval(
        color = Color(0xFF0C1628),
        topLeft = eggTopLeft,
        size = Size(eggWidth, eggHeight)
    )
    drawOval(
        color = Color(0xFF16253F),
        topLeft = Offset(eggTopLeft.x + 4.dp.toPx(), eggTopLeft.y + 4.dp.toPx()),
        size = Size(eggWidth - 8.dp.toPx(), eggHeight - 8.dp.toPx())
    )

    // 4. Núcleo interior bioluminiscente de datos palpitante
    val corePulse = 0.85f + sin(animTime * 5f) * 0.18f
    val coreCenter = Offset(eggX, cy - size * 0.08f)
    drawCircle(
        color = CyberColors.NeonMint.copy(alpha = 0.35f * corePulse),
        radius = size * 0.42f * corePulse,
        center = coreCenter
    )
    drawCircle(
        color = CyberColors.NeonCyan.copy(alpha = 0.85f * corePulse),
        radius = size * 0.24f * corePulse,
        center = coreCenter
    )
    drawCircle(
        color = Color.White,
        radius = size * 0.11f,
        center = Offset(coreCenter.x - size * 0.03f, coreCenter.y - size * 0.03f)
    )

    // 5. Red de circuitos neón / grietas bioluminiscentes en la cápsula
    val circuitPath = Path().apply {
        // Red lateral izquierda
        moveTo(eggX - size * 0.48f, cy - size * 0.35f)
        lineTo(eggX - size * 0.28f, cy - size * 0.15f)
        lineTo(eggX - size * 0.38f, cy + size * 0.2f)
        lineTo(eggX - size * 0.18f, cy + size * 0.45f)

        // Red central
        moveTo(eggX - size * 0.28f, cy - size * 0.15f)
        lineTo(eggX, cy - size * 0.38f)
        lineTo(eggX + size * 0.26f, cy - size * 0.18f)

        // Red lateral derecha
        moveTo(eggX + size * 0.26f, cy - size * 0.18f)
        lineTo(eggX + size * 0.46f, cy + size * 0.05f)
        lineTo(eggX + size * 0.28f, cy + size * 0.4f)
    }
    drawPath(
        path = circuitPath,
        color = CyberColors.NeonMint,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    // Nodos de datos en las uniones
    drawCircle(CyberColors.NeonCyan, radius = 2.8.dp.toPx(), center = Offset(eggX - size * 0.28f, cy - size * 0.15f))
    drawCircle(CyberColors.NeonCyan, radius = 2.8.dp.toPx(), center = Offset(eggX + size * 0.26f, cy - size * 0.18f))
    drawCircle(CyberColors.NeonMint, radius = 3.2.dp.toPx(), center = Offset(eggX, cy - size * 0.38f))

    // 6. Cúpula / emisor superior con luz testigo
    drawRoundRect(
        color = Color(0xFF243A5E),
        topLeft = Offset(eggX - size * 0.22f, cy - eggHeight / 2f - size * 0.12f),
        size = Size(size * 0.44f, size * 0.16f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawCircle(
        color = CyberColors.NeonCyan,
        radius = size * 0.065f,
        center = Offset(eggX, cy - eggHeight / 2f - size * 0.04f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ETAPAS 2, 3, 4 Y 5: GATITO CYBERSOLARPUNK (MIMI)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawCat(
    cx: Float, cy: Float, size: Float,
    stage: PetStage, animTime: Float, isBlinking: Boolean, isSleeping: Boolean,
    mood: PetMood
) {
    // Proporciones morfológicas exactas según la etapa de desarrollo
    val (scale, headRatio, earScale, bodyOffset) = when (stage) {
        PetStage.BEBE -> Quad(0.85f, 0.88f, 0.82f, 0.08f)    // Cabeza grande, cuerpo tierno y compacto
        PetStage.NINO -> Quad(1.0f, 0.78f, 1.0f, 0.0f)       // Juvenil en desarrollo
        PetStage.ADULTO -> Quad(1.22f, 0.72f, 1.08f, -0.05f) // Felino adulto estilizado y esbelto
        PetStage.ANCIANO -> Quad(1.24f, 0.74f, 1.05f, -0.05f)// Sabio con porte solemne y capa
        PetStage.HUEVO -> Quad(1.0f, 0.75f, 1.0f, 0.0f)
    }
    val s = size * scale
    val isElder = stage == PetStage.ANCIANO
    val earTwitch = sin(animTime * 3.5f) * (s * 0.035f)

    // Traje Cyber Completo en Anciano: Capa / Manto dorsal con ribetes dorados
    if (isElder) {
        drawElderCyberCape(cx, cy + s * bodyOffset, s, animTime)
    }

    // 1. Cola animada con punta cyber
    val tailWag = sin(animTime * 2.8f) * (s * 0.14f)
    val tailPath = Path().apply {
        moveTo(cx + s * 0.45f, cy + s * 0.45f)
        quadraticTo(
            cx + s * 0.95f + tailWag, cy + s * 0.25f,
            cx + s * 0.82f + tailWag * 1.3f, cy - s * 0.22f
        )
    }
    drawPath(
        path = tailPath,
        color = CyberColors.FurShadow,
        style = Stroke(width = s * 0.14f, cap = StrokeCap.Round)
    )
    drawPath(
        path = tailPath,
        color = CyberColors.FurPrimary,
        style = Stroke(width = s * 0.10f, cap = StrokeCap.Round)
    )
    // Anillo cyber en la punta de la cola
    val tailTipColor = if (isElder) CyberColors.GoldPrimary else CyberColors.NeonCyan
    drawCircle(
        color = tailTipColor,
        radius = s * 0.055f,
        center = Offset(cx + s * 0.82f + tailWag * 1.3f, cy - s * 0.22f)
    )

    // 2. Patitas delanteras / cuerpo base
    drawCircle(
        color = CyberColors.FurShadow,
        radius = s * 0.58f,
        center = Offset(cx, cy + s * 0.32f + s * bodyOffset)
    )
    drawCircle(
        color = CyberColors.FurPrimary,
        radius = s * 0.52f,
        center = Offset(cx, cy + s * 0.30f + s * bodyOffset)
    )
    // Pecho suave / pelaje frontal
    drawOval(
        color = CyberColors.FurCheeks,
        topLeft = Offset(cx - s * 0.30f, cy + s * 0.12f + s * bodyOffset),
        size = Size(s * 0.60f, s * 0.42f)
    )

    // 3. Orejas triangulares estilizadas
    val earTopL = Offset(cx - s * (0.75f * earScale) + earTwitch, cy - s * (1.05f * earScale))
    val earTopR = Offset(cx + s * (0.75f * earScale) - earTwitch, cy - s * (1.05f * earScale))

    val earPathL = Path().apply {
        moveTo(cx - s * 0.55f, cy - s * 0.22f)
        lineTo(earTopL.x, earTopL.y)
        lineTo(cx - s * 0.12f, cy - s * 0.52f)
        close()
    }
    val earPathR = Path().apply {
        moveTo(cx + s * 0.55f, cy - s * 0.22f)
        lineTo(earTopR.x, earTopR.y)
        lineTo(cx + s * 0.12f, cy - s * 0.52f)
        close()
    }
    drawPath(earPathL, CyberColors.FurPrimary)
    drawPath(earPathR, CyberColors.FurPrimary)

    // Puntas oscuras de las orejas (Cyber ear tips)
    val earTipL = Path().apply {
        moveTo(earTopL.x, earTopL.y)
        lineTo(earTopL.x + s * 0.12f, earTopL.y + s * 0.22f)
        lineTo(earTopL.x - s * 0.08f, earTopL.y + s * 0.24f)
        close()
    }
    val earTipR = Path().apply {
        moveTo(earTopR.x, earTopR.y)
        lineTo(earTopR.x - s * 0.12f, earTopR.y + s * 0.22f)
        lineTo(earTopR.x + s * 0.08f, earTopR.y + s * 0.24f)
        close()
    }
    drawPath(earTipL, CyberColors.FurShadow)
    drawPath(earTipR, CyberColors.FurShadow)

    // Interior de las orejas (Rosa suave)
    val innerEarL = Path().apply {
        moveTo(cx - s * 0.48f, cy - s * 0.26f)
        lineTo(earTopL.x + s * 0.05f, earTopL.y + s * 0.16f)
        lineTo(cx - s * 0.18f, cy - s * 0.48f)
        close()
    }
    val innerEarR = Path().apply {
        moveTo(cx + s * 0.48f, cy - s * 0.26f)
        lineTo(earTopR.x - s * 0.05f, earTopR.y + s * 0.16f)
        lineTo(cx + s * 0.18f, cy - s * 0.48f)
        close()
    }
    drawPath(innerEarL, CyberColors.InnerEar)
    drawPath(innerEarR, CyberColors.InnerEar)

    // 4. Cabeza principal (Ovoide felino estilizado)
    val headRadius = s * headRatio
    drawCircle(
        color = CyberColors.FurShadow,
        radius = headRadius,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = CyberColors.FurPrimary,
        radius = headRadius * 0.96f,
        center = Offset(cx, cy - s * 0.02f)
    )

    // Marcas de pelaje en la frente (rayas atigradas cyber)
    val stripeColor = CyberColors.FurShadow
    drawLine(stripeColor, Offset(cx, cy - headRadius * 0.85f), Offset(cx, cy - headRadius * 0.55f), strokeWidth = s * 0.055f, cap = StrokeCap.Round)
    drawLine(stripeColor, Offset(cx - s * 0.18f, cy - headRadius * 0.80f), Offset(cx - s * 0.14f, cy - headRadius * 0.58f), strokeWidth = s * 0.042f, cap = StrokeCap.Round)
    drawLine(stripeColor, Offset(cx + s * 0.18f, cy - headRadius * 0.80f), Offset(cx + s * 0.14f, cy - headRadius * 0.58f), strokeWidth = s * 0.042f, cap = StrokeCap.Round)

    // Mejillas suaves
    val cheekRadius = headRadius * 0.42f
    drawCircle(CyberColors.FurCheeks, radius = cheekRadius, center = Offset(cx - headRadius * 0.45f, cy + headRadius * 0.22f))
    drawCircle(CyberColors.FurCheeks, radius = cheekRadius, center = Offset(cx + headRadius * 0.45f, cy + headRadius * 0.22f))

    // 5. Expresiones faciales y ojos según PetMood y etapa
    drawPetEyes(cx, cy, s, isBlinking, isSleeping, mood, isElder)

    // 6. Hocico y nariz felina
    drawCircle(CyberColors.NosePink, radius = s * 0.075f, center = Offset(cx, cy + s * 0.14f))
    // Boca felina (^ o linita)
    val mouthPath = Path().apply {
        moveTo(cx - s * 0.12f, cy + s * 0.24f)
        quadraticTo(cx - s * 0.06f, cy + s * 0.27f, cx, cy + s * 0.21f)
        quadraticTo(cx + s * 0.06f, cy + s * 0.27f, cx + s * 0.12f, cy + s * 0.24f)
    }
    drawPath(mouthPath, CyberColors.FurShadow, style = Stroke(width = s * 0.035f, cap = StrokeCap.Round))

    // 7. Bigotes sutiles
    val whiskerW = s * 0.028f
    drawLine(CyberColors.Whiskers, Offset(cx - s * 0.26f, cy + s * 0.16f), Offset(cx - s * 0.75f, cy + s * 0.09f), strokeWidth = whiskerW, cap = StrokeCap.Round)
    drawLine(CyberColors.Whiskers, Offset(cx - s * 0.26f, cy + s * 0.24f), Offset(cx - s * 0.78f, cy + s * 0.28f), strokeWidth = whiskerW, cap = StrokeCap.Round)
    drawLine(CyberColors.Whiskers, Offset(cx + s * 0.26f, cy + s * 0.16f), Offset(cx + s * 0.75f, cy + s * 0.09f), strokeWidth = whiskerW, cap = StrokeCap.Round)
    drawLine(CyberColors.Whiskers, Offset(cx + s * 0.26f, cy + s * 0.24f), Offset(cx + s * 0.78f, cy + s * 0.28f), strokeWidth = whiskerW, cap = StrokeCap.Round)

    // Detalles adicionales de sabiduría en Anciano
    if (isElder) {
        // Cejas venerables / mechones sabios
        drawLine(CyberColors.GoldPrimary, Offset(cx - s * 0.38f, cy - s * 0.38f), Offset(cx - s * 0.15f, cy - s * 0.44f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(CyberColors.GoldPrimary, Offset(cx + s * 0.38f, cy - s * 0.38f), Offset(cx + s * 0.15f, cy - s * 0.44f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CAPA / MANTO CYBER DEL ANCIANO ('Traje cyber completo con pátina dorada')
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawElderCyberCape(cx: Float, cy: Float, s: Float, animTime: Float) {
    val capeFlutter = sin(animTime * 2.2f) * (s * 0.035f)

    // Manto dorsal ceremonial
    val capePath = Path().apply {
        moveTo(cx - s * 0.62f, cy + s * 0.05f)
        lineTo(cx - s * 0.78f + capeFlutter, cy + s * 0.75f)
        quadraticTo(cx, cy + s * 0.88f, cx + s * 0.78f - capeFlutter, cy + s * 0.75f)
        lineTo(cx + s * 0.62f, cy + s * 0.05f)
        close()
    }
    drawPath(capePath, CyberColors.CapeBase)
    drawPath(capePath, CyberColors.CapeTrim, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

    // Hombreras de bronce / oro
    drawCircle(CyberColors.BrassTrim, radius = s * 0.16f, center = Offset(cx - s * 0.52f, cy + s * 0.12f))
    drawCircle(CyberColors.GoldPrimary, radius = s * 0.12f, center = Offset(cx - s * 0.52f, cy + s * 0.12f))
    drawCircle(CyberColors.BrassTrim, radius = s * 0.16f, center = Offset(cx + s * 0.52f, cy + s * 0.12f))
    drawCircle(CyberColors.GoldPrimary, radius = s * 0.12f, center = Offset(cx + s * 0.52f, cy + s * 0.12f))

    // Collarín rúnico
    val collarPath = Path().apply {
        moveTo(cx - s * 0.45f, cy + s * 0.22f)
        quadraticTo(cx, cy + s * 0.36f, cx + s * 0.45f, cy + s * 0.22f)
    }
    drawPath(collarPath, CyberColors.GoldPrimary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPRESIONES EMOCIONALES DE OJOS SEGÚN PETMOOD
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawPetEyes(
    cx: Float, cy: Float, s: Float,
    isBlinking: Boolean, isSleeping: Boolean,
    mood: PetMood, isElder: Boolean
) {
    val eyeL = Offset(cx - s * 0.32f, cy - s * 0.06f)
    val eyeR = Offset(cx + s * 0.32f, cy - s * 0.06f)
    val eyeRadius = s * 0.17f
    val moodColor = CyberColors.forMood(mood, isElder)

    if (isSleeping || isBlinking) {
        // Modo sueño / parpadeo: arcos cerrados pacíficos
        val eyeColor = CyberColors.FurShadow
        val pathL = Path().apply {
            moveTo(eyeL.x - eyeRadius, eyeL.y)
            quadraticTo(eyeL.x, eyeL.y + eyeRadius * 0.6f, eyeL.x + eyeRadius, eyeL.y)
        }
        val pathR = Path().apply {
            moveTo(eyeR.x - eyeRadius, eyeR.y)
            quadraticTo(eyeR.x, eyeR.y + eyeRadius * 0.6f, eyeR.x + eyeRadius, eyeR.y)
        }
        drawPath(pathL, eyeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(pathR, eyeColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        return
    }

    when (mood) {
        PetMood.FELIZ -> {
            // Arcos sonrientes estilo anime / guiño alegre (^ ^)
            val pathL = Path().apply {
                moveTo(eyeL.x - eyeRadius, eyeL.y + eyeRadius * 0.3f)
                quadraticTo(eyeL.x, eyeL.y - eyeRadius * 0.9f, eyeL.x + eyeRadius, eyeL.y + eyeRadius * 0.3f)
            }
            val pathR = Path().apply {
                moveTo(eyeR.x - eyeRadius, eyeR.y + eyeRadius * 0.3f)
                quadraticTo(eyeR.x, eyeR.y - eyeRadius * 0.9f, eyeR.x + eyeRadius, eyeR.y + eyeRadius * 0.3f)
            }
            drawPath(pathL, moodColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            drawPath(pathR, moodColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            // Chispas de alegría
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(eyeL.x, eyeL.y - eyeRadius * 0.4f))
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(eyeR.x, eyeR.y - eyeRadius * 0.4f))
        }

        PetMood.TRISTE, PetMood.CANSADO -> {
            // Ojos semicerrados / caídos
            drawCircle(CyberColors.FurShadow, radius = eyeRadius, center = eyeL)
            drawCircle(CyberColors.FurShadow, radius = eyeRadius, center = eyeR)
            drawCircle(moodColor, radius = eyeRadius * 0.65f, center = Offset(eyeL.x, eyeL.y + eyeRadius * 0.2f))
            drawCircle(moodColor, radius = eyeRadius * 0.65f, center = Offset(eyeR.x, eyeR.y + eyeRadius * 0.2f))

            // Párpado caído cubriendo la mitad superior
            val droopL = Path().apply {
                moveTo(eyeL.x - eyeRadius * 1.1f, eyeL.y - eyeRadius * 0.2f)
                quadraticTo(eyeL.x, eyeL.y + eyeRadius * 0.3f, eyeL.x + eyeRadius * 1.1f, eyeL.y - eyeRadius * 0.2f)
                lineTo(eyeL.x + eyeRadius * 1.1f, eyeL.y - eyeRadius * 1.1f)
                lineTo(eyeL.x - eyeRadius * 1.1f, eyeL.y - eyeRadius * 1.1f)
                close()
            }
            val droopR = Path().apply {
                moveTo(eyeR.x - eyeRadius * 1.1f, eyeR.y - eyeRadius * 0.2f)
                quadraticTo(eyeR.x, eyeR.y + eyeRadius * 0.3f, eyeR.x + eyeRadius * 1.1f, eyeR.y - eyeRadius * 0.2f)
                lineTo(eyeR.x + eyeRadius * 1.1f, eyeR.y - eyeRadius * 1.1f)
                lineTo(eyeR.x - eyeRadius * 1.1f, eyeR.y - eyeRadius * 1.1f)
                close()
            }
            drawPath(droopL, CyberColors.FurPrimary)
            drawPath(droopR, CyberColors.FurPrimary)
        }

        PetMood.ENFERMO, PetMood.HAMBRIENTO -> {
            // Ojos concentrados / en alerta o enfermos
            drawCircle(CyberColors.FurShadow, radius = eyeRadius, center = eyeL)
            drawCircle(CyberColors.FurShadow, radius = eyeRadius, center = eyeR)
            drawCircle(moodColor, radius = eyeRadius * 0.72f, center = eyeL)
            drawCircle(moodColor, radius = eyeRadius * 0.72f, center = eyeR)
            // Pupila vertical felina
            drawOval(Color.Black, topLeft = Offset(eyeL.x - eyeRadius * 0.15f, eyeL.y - eyeRadius * 0.5f), size = Size(eyeRadius * 0.3f, eyeRadius))
            drawOval(Color.Black, topLeft = Offset(eyeR.x - eyeRadius * 0.15f, eyeR.y - eyeRadius * 0.5f), size = Size(eyeRadius * 0.3f, eyeRadius))
        }

        else -> {
            // Ojos abiertos expresivos (Normal, etc.) con doble reflejo
            drawCircle(CyberColors.FurShadow, radius = eyeRadius, center = eyeL)
            drawCircle(CyberColors.FurShadow, radius = eyeRadius, center = eyeR)

            // Iris con color emocional
            drawCircle(moodColor, radius = eyeRadius * 0.82f, center = eyeL)
            drawCircle(moodColor, radius = eyeRadius * 0.82f, center = eyeR)

            // Pupila interior profunda
            drawCircle(Color(0xFF0F0E1E), radius = eyeRadius * 0.52f, center = eyeL)
            drawCircle(Color(0xFF0F0E1E), radius = eyeRadius * 0.52f, center = eyeR)

            // Brillo estelar principal
            drawCircle(Color.White, radius = eyeRadius * 0.36f, center = Offset(eyeL.x - eyeRadius * 0.28f, eyeL.y - eyeRadius * 0.28f))
            drawCircle(Color.White, radius = eyeRadius * 0.36f, center = Offset(eyeR.x - eyeRadius * 0.28f, eyeR.y - eyeRadius * 0.28f))
            // Micro-brillo secundario
            drawCircle(Color.White, radius = eyeRadius * 0.16f, center = Offset(eyeL.x + eyeRadius * 0.26f, eyeL.y + eyeRadius * 0.24f))
            drawCircle(Color.White, radius = eyeRadius * 0.16f, center = Offset(eyeR.x + eyeRadius * 0.26f, eyeR.y + eyeRadius * 0.24f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAS 6 PARTES CYBER PROGRESSIVAS SEGÚN LA ETAPA ('drawCyberPartsOverlay')
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawCyberPartsOverlay(
    cx: Float, cy: Float, size: Float,
    pet: PetEntity, mood: PetMood, animTime: Float
) {
    if (pet.stage == PetStage.HUEVO) return

    val scale = when (pet.stage) {
        PetStage.BEBE -> 0.85f
        PetStage.NINO -> 1.0f
        PetStage.ADULTO -> 1.22f
        PetStage.ANCIANO -> 1.24f
        PetStage.HUEVO -> 1.0f
    }
    val s = size * scale
    val isElder = pet.stage == PetStage.ANCIANO

    // Paleta lumínica adaptativa: Cian/Menta en Bebé-Niño-Adulto, Dorado/Ámbar en Anciano
    val primaryCyber = if (isElder) CyberColors.GoldPrimary else CyberColors.NeonCyan
    val secondaryCyber = if (isElder) CyberColors.AmberGlow else CyberColors.NeonMint
    val glowCyber = if (isElder) Color(0x44FFD700) else Color(0x4400E5FF)

    // =========================================================================
    // PARTE 1: ANTENA DE ESCANEO BLE (Brote en Bebé, Completa en Niño/Adulto/Anciano)
    // =========================================================================
    when (pet.stage) {
        PetStage.BEBE -> {
            // "Bebé: Muestra un brote de antena sutil en la cabeza"
            val budTopY = cy - s * 0.72f
            drawLine(
                color = primaryCyber,
                start = Offset(cx, cy - s * 0.62f),
                end = Offset(cx, budTopY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            val budGlow = 0.6f + sin(animTime * 4f) * 0.4f
            drawCircle(
                color = secondaryCyber.copy(alpha = budGlow),
                radius = s * 0.055f,
                center = Offset(cx, budTopY)
            )
            drawCircle(
                color = Color.White,
                radius = s * 0.025f,
                center = Offset(cx, budTopY)
            )
        }
        else -> {
            // "Niño, Adulto, Anciano: Antena activa con ondas concéntricas de escaneo"
            val antennaHeight = s * 0.48f
            val topY = cy - s * 0.72f - antennaHeight
            drawLine(
                color = primaryCyber,
                start = Offset(cx, cy - s * 0.70f),
                end = Offset(cx, topY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Nodo emisor superior
            val blinkAlpha = if ((animTime * 8f).toInt() % 2 == 0) 1.0f else 0.4f
            drawCircle(
                color = glowCyber,
                radius = s * 0.12f,
                center = Offset(cx, topY)
            )
            drawCircle(
                color = secondaryCyber.copy(alpha = blinkAlpha),
                radius = s * 0.075f,
                center = Offset(cx, topY)
            )
            drawCircle(
                color = Color.White,
                radius = s * 0.035f,
                center = Offset(cx, topY)
            )

            // Ondas de emisión concéntricas BLE neón
            val wavePulse1 = ((animTime * 2.5f) % 1.0f)
            val wavePulse2 = ((animTime * 2.5f + 0.5f) % 1.0f)
            drawCircle(
                color = primaryCyber.copy(alpha = (1.0f - wavePulse1) * 0.65f),
                radius = s * 0.15f + wavePulse1 * (s * 0.28f),
                center = Offset(cx, topY),
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawCircle(
                color = secondaryCyber.copy(alpha = (1.0f - wavePulse2) * 0.65f),
                radius = s * 0.15f + wavePulse2 * (s * 0.28f),
                center = Offset(cx, topY),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }

    // =========================================================================
    // PARTE 2: VISOR OCULAR DIGITAL (Presente en Niño, Adulto y Anciano)
    // =========================================================================
    if (pet.stage == PetStage.NINO || pet.stage == PetStage.ADULTO || pet.stage == PetStage.ANCIANO) {
        val eyeVisorCenter = Offset(cx - s * 0.32f, cy - s * 0.06f)
        val visorColor = CyberColors.forMood(mood, isElder)
        val visorW = s * 0.38f
        val visorH = s * 0.26f

        // Montura digital del visor
        drawRoundRect(
            color = visorColor.copy(alpha = 0.32f),
            topLeft = Offset(eyeVisorCenter.x - visorW / 2f, eyeVisorCenter.y - visorH / 2f),
            size = Size(visorW, visorH),
            cornerRadius = CornerRadius(visorH * 0.35f, visorH * 0.35f)
        )
        drawRoundRect(
            color = visorColor,
            topLeft = Offset(eyeVisorCenter.x - visorW / 2f, eyeVisorCenter.y - visorH / 2f),
            size = Size(visorW, visorH),
            cornerRadius = CornerRadius(visorH * 0.35f, visorH * 0.35f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Templo / patilla de conexión cyber hasta la oreja
        drawLine(
            color = visorColor,
            start = Offset(eyeVisorCenter.x - visorW / 2f, eyeVisorCenter.y),
            end = Offset(cx - s * 0.62f, cy - s * 0.12f),
            strokeWidth = 2.dp.toPx()
        )

        // Retícula / HUD interior del visor según el estado de ánimo
        when (mood) {
            PetMood.FELIZ -> {
                // Mini glifo de corazón / alegría
                drawCircle(Color.White, radius = 2.dp.toPx(), center = eyeVisorCenter)
            }
            PetMood.DORMIDO -> {
                // Línea de standby Z-Z
                drawLine(
                    color = visorColor.copy(alpha = 0.7f),
                    start = Offset(eyeVisorCenter.x - s * 0.10f, eyeVisorCenter.y),
                    end = Offset(eyeVisorCenter.x + s * 0.10f, eyeVisorCenter.y),
                    strokeWidth = 1.8.dp.toPx()
                )
            }
            else -> {
                // Retícula de mira cyber
                drawLine(
                    color = visorColor.copy(alpha = 0.6f),
                    start = Offset(eyeVisorCenter.x - s * 0.12f, eyeVisorCenter.y),
                    end = Offset(eyeVisorCenter.x + s * 0.12f, eyeVisorCenter.y),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawLine(
                    color = visorColor.copy(alpha = 0.6f),
                    start = Offset(eyeVisorCenter.x, eyeVisorCenter.y - s * 0.08f),
                    end = Offset(eyeVisorCenter.x, eyeVisorCenter.y + s * 0.08f),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }

    // =========================================================================
    // PARTE 3: NÚCLEO DE SINCRONIZACIÓN EN EL PECHO (Presente en Adulto y Anciano)
    // =========================================================================
    if (pet.stage == PetStage.ADULTO || pet.stage == PetStage.ANCIANO) {
        // Pulsación rítmica directamente ligada a 'happiness'
        val happinessRate = 2.5f + (pet.happiness / 100f) * 3.5f
        val pulseScale = 1.0f + sin(animTime * happinessRate) * 0.16f
        val orbRadius = s * 0.12f * pulseScale
        val orbCenter = Offset(cx, cy + s * 0.28f)

        // Halo exterior
        drawCircle(
            color = glowCyber,
            radius = orbRadius * 1.9f,
            center = orbCenter
        )
        // Núcleo energético
        drawCircle(
            color = primaryCyber,
            radius = orbRadius,
            center = orbCenter
        )
        // Centro brillante
        drawCircle(
            color = Color.White,
            radius = orbRadius * 0.42f,
            center = Offset(orbCenter.x - orbRadius * 0.2f, orbCenter.y - orbRadius * 0.2f)
        )
    }

    // =========================================================================
    // PARTE 4: PLACAS DORSALES SEGMENTADAS (Presente en Adulto y Anciano)
    // =========================================================================
    if (pet.stage == PetStage.ADULTO || pet.stage == PetStage.ANCIANO) {
        // Se ubican a lo largo del lomo (silueta lateral trasera izquierda)
        // Se iluminan progresivamente según el nivel (1 a 4)
        val totalPlates = 4
        val litPlates = pet.nivel.coerceIn(1, totalPlates)

        for (i in 0 until totalPlates) {
            val plateY = cy - s * 0.22f + i * (s * 0.16f)
            val plateX = cx + s * 0.52f - (i * s * 0.04f)
            val isLit = i < litPlates

            val plateColor = if (isLit) {
                if (isElder) CyberColors.GoldPrimary else CyberColors.NeonMint
            } else {
                Color(0x332A2645) // Placa opaca apagada
            }

            // Placa angular segmentada
            val platePath = Path().apply {
                moveTo(plateX, plateY - s * 0.05f)
                lineTo(plateX + s * 0.10f, plateY)
                lineTo(plateX, plateY + s * 0.05f)
                close()
            }
            drawPath(platePath, plateColor)
            if (isLit) {
                drawPath(platePath, Color.White, style = Stroke(width = 1.dp.toPx()))
            }
        }
    }

    // =========================================================================
    // PARTE 5: ALMOHADILLAS DE TRACCIÓN EN LAS PATAS (Adulto y Anciano)
    // =========================================================================
    if (pet.stage == PetStage.ADULTO || pet.stage == PetStage.ANCIANO) {
        val pawLeft = Offset(cx - s * 0.26f, cy + s * 0.62f)
        val pawRight = Offset(cx + s * 0.26f, cy + s * 0.62f)
        val padColor = if (isElder) CyberColors.BrassTrim else CyberColors.NeonCyan

        // Almohadilla central
        drawOval(padColor.copy(alpha = 0.75f), topLeft = Offset(pawLeft.x - s * 0.08f, pawLeft.y), size = Size(s * 0.16f, s * 0.08f))
        drawOval(padColor.copy(alpha = 0.75f), topLeft = Offset(pawRight.x - s * 0.08f, pawRight.y), size = Size(s * 0.16f, s * 0.08f))

        // Micro-chispas de fricción al caminar
        val sparkPhase = (animTime * 6f).toInt() % 3
        if (sparkPhase == 0) {
            drawCircle(primaryCyber, radius = 2.dp.toPx(), center = Offset(pawLeft.x - s * 0.10f, pawLeft.y + s * 0.04f))
            drawCircle(secondaryCyber, radius = 2.dp.toPx(), center = Offset(pawRight.x + s * 0.10f, pawRight.y + s * 0.04f))
        }
    }

    // =========================================================================
    // PARTE 6: FIBRA ÓPTICA / OVERCLOCK CUÁNTICO (#FF0055)
    // =========================================================================
    if (pet.modoTurbo) {
        val auraRadius = s * 1.15f + sin(animTime * 9f) * (s * 0.08f)
        // Halo exterior eléctrico magenta
        drawCircle(
            color = CyberColors.OverclockMagenta.copy(alpha = 0.28f),
            radius = auraRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = CyberColors.OverclockMagenta,
            radius = auraRadius * 0.95f,
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx())
        )

        // Filamentos curvados de fibra óptica con estelas de partículas
        for (f in 0..3) {
            val angle = animTime * 3f + (f * 1.57f)
            val fx = cx + cos(angle) * (s * 0.82f)
            val fy = cy + sin(angle) * (s * 0.82f)
            drawCircle(
                color = CyberColors.OverclockElectric,
                radius = 3.5.dp.toPx(),
                center = Offset(fx, fy)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CORRUPCIÓN DE DATOS: GLITCH VISUAL Y ABERRACIÓN CROMÁTICA
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawCorruptedGlitch(
    cx: Float, cy: Float, size: Float, animTime: Float,
    petType: PetType, stage: PetStage, mood: PetMood
) {
    val jitterX = sin(animTime * 28f) * (size * 0.08f)
    val jitterY = cos(animTime * 34f) * (size * 0.04f)
    val glitchCx = cx + jitterX
    val glitchCy = cy + jitterY

    // 1. Aberración cromática: Silueta cian hacia la izquierda (-6px)
    withTransform({ translate(left = -6f, top = 2f) }) {
        drawCorruptedBase(glitchCx, glitchCy, size, stage, animTime, petType, mood)
    }

    // 2. Aberración cromática: Silueta magenta hacia la derecha (+6px)
    withTransform({ translate(left = 6f, top = -2f) }) {
        drawCorruptedBase(glitchCx, glitchCy, size, stage, animTime, petType, mood)
    }

    // 3. Mascota base en posición alterada
    drawCorruptedBase(glitchCx, glitchCy, size, stage, animTime, petType, mood)

    // 4. Ruido estático digital / píxeles flotantes en desorden
    val randomSeed = (animTime * 120f).toInt()
    for (i in 0..12) {
        val px = glitchCx + sin((randomSeed + i * 19).toFloat()) * (size * 0.95f)
        val py = glitchCy + cos((randomSeed + i * 27).toFloat()) * (size * 0.95f)
        val pW = 5.dp.toPx() + (i % 3) * 2.dp.toPx()
        val pH = 2.5.dp.toPx() + (i % 2) * 2.dp.toPx()
        val color = if (i % 2 == 0) Color(0xFFFF1744) else CyberColors.NeonCyan
        drawRect(
            color = color.copy(alpha = 0.88f),
            topLeft = Offset(px, py),
            size = Size(pW, pH)
        )
    }
}

private fun DrawScope.drawCorruptedBase(
    cx: Float, cy: Float, size: Float,
    stage: PetStage, animTime: Float, petType: PetType, mood: PetMood
) {
    when (stage) {
        PetStage.HUEVO -> drawEgg(cx, cy, size, animTime)
        else -> {
            when (petType) {
                PetType.GATO -> drawCat(cx, cy, size, stage, animTime, isBlinking = false, isSleeping = false, mood = mood)
                PetType.PERRO -> drawDog(cx, cy, size, stage, animTime, isBlinking = false, isSleeping = false, mood = mood)
                PetType.ZORRO -> drawFox(cx, cy, size, stage, animTime, isBlinking = false, isSleeping = false, mood = mood)
                PetType.CONEJO -> drawRabbit(cx, cy, size, stage, animTime, isBlinking = false, isSleeping = false, mood = mood)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ESPECIES SECUNDARIAS: PERRO, ZORRO, CONEJO (ADAPTADAS CON PROPORCIONES CYBER)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawDog(
    cx: Float, cy: Float, size: Float,
    stage: PetStage, animTime: Float, isBlinking: Boolean, isSleeping: Boolean,
    mood: PetMood
) {
    val scale = if (stage == PetStage.ADULTO || stage == PetStage.ANCIANO) 1.22f else 1.0f
    val s = size * scale
    val dogBrown = Color(0xFF8D6E63)
    val dogDark = Color(0xFF5D4037)
    val isElder = stage == PetStage.ANCIANO

    if (isElder) {
        drawElderCyberCape(cx, cy, s, animTime)
    }

    // Orejas caídas
    val tailWiggle = sin(animTime * 7f) * (s * 0.12f)
    drawRoundRect(
        dogBrown,
        topLeft = Offset(cx - s * 0.92f, cy - s * 0.48f + tailWiggle * 0.2f),
        size = Size(s * 0.35f, s * 0.75f),
        cornerRadius = CornerRadius(s * 0.15f, s * 0.15f)
    )
    drawRoundRect(
        dogBrown,
        topLeft = Offset(cx + s * 0.58f, cy - s * 0.48f - tailWiggle * 0.2f),
        size = Size(s * 0.35f, s * 0.75f),
        cornerRadius = CornerRadius(s * 0.15f, s * 0.15f)
    )

    // Cabeza
    drawCircle(Color(0xFFA1887F), radius = s * 0.72f, center = Offset(cx, cy))
    // Mancha en ojo
    drawOval(dogDark.copy(alpha = 0.35f), topLeft = Offset(cx - s * 0.50f, cy - s * 0.38f), size = Size(s * 0.44f, s * 0.52f))

    // Ojos según PetMood
    drawPetEyes(cx, cy, s, isBlinking, isSleeping, mood, isElder)

    // Hocico
    drawOval(Color(0xFFD7CCC8), topLeft = Offset(cx - s * 0.34f, cy + s * 0.06f), size = Size(s * 0.68f, s * 0.44f))
    drawCircle(Color(0xFF212121), radius = s * 0.11f, center = Offset(cx, cy + s * 0.15f))
}

private fun DrawScope.drawFox(
    cx: Float, cy: Float, size: Float,
    stage: PetStage, animTime: Float, isBlinking: Boolean, isSleeping: Boolean,
    mood: PetMood
) {
    val scale = if (stage == PetStage.ADULTO || stage == PetStage.ANCIANO) 1.22f else 1.0f
    val s = size * scale
    val foxOrange = Color(0xFFFF5722)
    val isElder = stage == PetStage.ANCIANO

    if (isElder) {
        drawElderCyberCape(cx, cy, s, animTime)
    }

    // Orejas
    val earTwitch = sin(animTime * 3f) * (s * 0.03f)
    val earL = Path().apply {
        moveTo(cx - s * 0.60f, cy - s * 0.20f)
        lineTo(cx - s * 0.88f + earTwitch, cy - s * 1.15f)
        lineTo(cx - s * 0.15f, cy - s * 0.50f)
        close()
    }
    val earR = Path().apply {
        moveTo(cx + s * 0.60f, cy - s * 0.20f)
        lineTo(cx + s * 0.88f - earTwitch, cy - s * 1.15f)
        lineTo(cx + s * 0.15f, cy - s * 0.50f)
        close()
    }
    drawPath(earL, foxOrange)
    drawPath(earR, foxOrange)

    // Cabeza
    drawCircle(foxOrange, radius = s * 0.74f, center = Offset(cx, cy))

    // Mejillas blancas
    val cheekL = Path().apply {
        moveTo(cx, cy + s * 0.10f)
        lineTo(cx - s * 0.70f, cy + s * 0.20f)
        lineTo(cx - s * 0.28f, cy + s * 0.64f)
        close()
    }
    val cheekR = Path().apply {
        moveTo(cx, cy + s * 0.10f)
        lineTo(cx + s * 0.70f, cy + s * 0.20f)
        lineTo(cx + s * 0.28f, cy + s * 0.64f)
        close()
    }
    drawPath(cheekL, Color(0xFFFAFAFA))
    drawPath(cheekR, Color(0xFFFAFAFA))

    // Ojos según PetMood
    drawPetEyes(cx, cy, s, isBlinking, isSleeping, mood, isElder)

    // Nariz
    drawCircle(Color(0xFF212121), radius = s * 0.09f, center = Offset(cx, cy + s * 0.22f))
}

private fun DrawScope.drawRabbit(
    cx: Float, cy: Float, size: Float,
    stage: PetStage, animTime: Float, isBlinking: Boolean, isSleeping: Boolean,
    mood: PetMood
) {
    val scale = if (stage == PetStage.ADULTO || stage == PetStage.ANCIANO) 1.22f else 1.0f
    val s = size * scale
    val rabbitWhite = Color(0xFFEEEEEE)
    val innerPink = Color(0xFFF48FB1)
    val isElder = stage == PetStage.ANCIANO

    if (isElder) {
        drawElderCyberCape(cx, cy, s, animTime)
    }

    // Orejas largas
    val earTwitch = sin(animTime * 4.5f) * (s * 0.05f)
    drawRoundRect(rabbitWhite, topLeft = Offset(cx - s * 0.44f + earTwitch, cy - s * 1.52f), size = Size(s * 0.30f, s * 1.12f), cornerRadius = CornerRadius(s * 0.15f, s * 0.15f))
    drawRoundRect(innerPink, topLeft = Offset(cx - s * 0.36f + earTwitch, cy - s * 1.38f), size = Size(s * 0.15f, s * 0.82f), cornerRadius = CornerRadius(s * 0.075f, s * 0.075f))

    drawRoundRect(rabbitWhite, topLeft = Offset(cx + s * 0.14f - earTwitch, cy - s * 1.52f), size = Size(s * 0.30f, s * 1.12f), cornerRadius = CornerRadius(s * 0.15f, s * 0.15f))
    drawRoundRect(innerPink, topLeft = Offset(cx + s * 0.22f - earTwitch, cy - s * 1.38f), size = Size(s * 0.15f, s * 0.82f), cornerRadius = CornerRadius(s * 0.075f, s * 0.075f))

    // Cabeza
    drawCircle(rabbitWhite, radius = s * 0.72f, center = Offset(cx, cy))

    // Ojos según PetMood
    drawPetEyes(cx, cy, s, isBlinking, isSleeping, mood, isElder)

    // Nariz y boca
    drawCircle(innerPink, radius = s * 0.08f, center = Offset(cx, cy + s * 0.15f))
}

// Estructura interna auxiliar para proporciones
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


package com.renea.arete_social.gui.mapa

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.sin

/** Colores de un Mimi. El pelaje se elige según el id de la persona, así siempre es el mismo gato. */
private class PelajeMimi(val cuerpo: Color, val ojos: Color)

private val OREJA_INTERIOR = Color(0xFFFFB3C1)
private val NARIZ = Color(0xFFE88AA0)
private val BORDE = Color(0xFF2A2A30).copy(alpha = 0.65f)

private val PELAJES = listOf(
    PelajeMimi(Color(0xFFF2A65A), Color(0xFF2B2B2B)), // naranja
    PelajeMimi(Color(0xFF9AA3AD), Color(0xFF2B2B2B)), // gris
    PelajeMimi(Color(0xFF3B3B44), Color(0xFFFFE066)), // negro
    PelajeMimi(Color(0xFFF6EFE6), Color(0xFF2B2B2B)), // crema
    PelajeMimi(Color(0xFF9C6B4A), Color(0xFFFFE066)), // café
    PelajeMimi(Color(0xFFE3CDAE), Color(0xFF3A6EA5))  // siamés
)

/**
 * Gatito Mimi en miniatura, dibujado a mano con Canvas (sin imágenes, muy liviano).
 * Mira hacia la derecha; quien lo use lo voltea con scaleX = -1 para mirar a la izquierda.
 *
 * Los parámetros son lambdas a propósito: se leen solo al DIBUJAR, así la animación de cada
 * cuadro no obliga a recomponer nada.
 */
@Composable
fun MimiMapaSprite(
    semilla: String,
    tiempoSegundos: () -> Float,
    caminando: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val pelaje = remember(semilla) { PELAJES[(semilla.hashCode() and Int.MAX_VALUE) % PELAJES.size] }
    // Fase propia para que dos Mimis no muevan la cola ni parpadeen exactamente a la vez.
    val fase = remember(semilla) { ((semilla.hashCode() ushr 3) and 0xFF) / 255f * 6.2831855f }

    Canvas(modifier = modifier) {
        dibujarMimi(pelaje, fase, tiempoSegundos(), caminando())
    }
}

private fun DrawScope.ovaloConBorde(centro: Offset, rx: Float, ry: Float, color: Color, grosorBorde: Float) {
    drawOval(
        color = BORDE,
        topLeft = Offset(centro.x - rx - grosorBorde, centro.y - ry - grosorBorde),
        size = Size((rx + grosorBorde) * 2f, (ry + grosorBorde) * 2f)
    )
    drawOval(
        color = color,
        topLeft = Offset(centro.x - rx, centro.y - ry),
        size = Size(rx * 2f, ry * 2f)
    )
}

private fun DrawScope.triangulo(a: Offset, b: Offset, c: Offset, relleno: Color, borde: Color?, grosor: Float) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, relleno)
    if (borde != null) {
        drawPath(path, borde, style = Stroke(width = grosor, join = StrokeJoin.Round))
    }
}

private fun DrawScope.dibujarMimi(pelaje: PelajeMimi, fase: Float, t: Float, caminando: Boolean) {
    val s = size.minDimension
    val borde = s * 0.022f
    val paso = if (caminando) sin(t * 9f + fase) else 0f
    val rebote = if (caminando) abs(paso) * s * 0.035f else 0f
    val balanceoCola = sin(t * (if (caminando) 6f else 2.5f) + fase) * s * 0.07f
    val parpadea = ((t + fase) % 3.4f) < 0.12f

    // Sombra en el suelo.
    drawOval(
        color = Color.Black.copy(alpha = 0.22f),
        topLeft = Offset(s * 0.18f, s * 0.87f),
        size = Size(s * 0.62f, s * 0.09f)
    )

    // Cola.
    val cola = Path().apply {
        moveTo(s * 0.22f, s * 0.66f - rebote)
        cubicTo(
            s * 0.04f, s * 0.66f - rebote,
            s * 0.02f + balanceoCola, s * 0.36f,
            s * 0.14f + balanceoCola, s * 0.30f
        )
    }
    drawPath(cola, BORDE, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round))
    drawPath(cola, pelaje.cuerpo, style = Stroke(width = s * 0.09f, cap = StrokeCap.Round))

    // Patas (las diagonales se mueven a la vez, como al caminar de verdad).
    listOf(0.30f to paso, 0.40f to -paso, 0.54f to -paso, 0.64f to paso).forEach { (px, mov) ->
        ovaloConBorde(
            centro = Offset(s * px + mov * s * 0.03f, s * 0.82f),
            rx = s * 0.06f, ry = s * 0.055f,
            color = pelaje.cuerpo, grosorBorde = borde
        )
    }

    // Cuerpo.
    ovaloConBorde(
        centro = Offset(s * 0.42f, s * 0.64f - rebote),
        rx = s * 0.27f, ry = s * 0.17f,
        color = pelaje.cuerpo, grosorBorde = borde
    )

    // Orejas (detrás de la cabeza, por eso se dibujan antes).
    triangulo(
        Offset(s * 0.50f, s * 0.32f - rebote), Offset(s * 0.53f, s * 0.09f - rebote), Offset(s * 0.65f, s * 0.24f - rebote),
        pelaje.cuerpo, BORDE, borde * 1.4f
    )
    triangulo(
        Offset(s * 0.67f, s * 0.24f - rebote), Offset(s * 0.79f, s * 0.09f - rebote), Offset(s * 0.83f, s * 0.32f - rebote),
        pelaje.cuerpo, BORDE, borde * 1.4f
    )
    triangulo(
        Offset(s * 0.54f, s * 0.26f - rebote), Offset(s * 0.55f, s * 0.15f - rebote), Offset(s * 0.61f, s * 0.23f - rebote),
        OREJA_INTERIOR, null, 0f
    )
    triangulo(
        Offset(s * 0.70f, s * 0.23f - rebote), Offset(s * 0.77f, s * 0.15f - rebote), Offset(s * 0.79f, s * 0.26f - rebote),
        OREJA_INTERIOR, null, 0f
    )

    // Cabeza.
    val cabeza = Offset(s * 0.67f, s * 0.43f - rebote)
    ovaloConBorde(cabeza, rx = s * 0.20f, ry = s * 0.19f, color = pelaje.cuerpo, grosorBorde = borde)

    // Ojos (parpadean de vez en cuando).
    val yOjos = cabeza.y - s * 0.01f
    listOf(s * 0.61f, s * 0.75f).forEach { xOjo ->
        if (parpadea) {
            drawLine(
                color = pelaje.ojos,
                start = Offset(xOjo - s * 0.03f, yOjos),
                end = Offset(xOjo + s * 0.03f, yOjos),
                strokeWidth = s * 0.02f,
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(color = pelaje.ojos, radius = s * 0.035f, center = Offset(xOjo, yOjos))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = s * 0.012f, center = Offset(xOjo + s * 0.01f, yOjos - s * 0.012f))
        }
    }

    // Nariz.
    drawCircle(color = NARIZ, radius = s * 0.022f, center = Offset(cabeza.x + s * 0.09f, cabeza.y + s * 0.06f))
}

package com.renea.arete_social.gui.comunidad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * ── Plan de Fidelidad Visual v2, sección 4.4 ──────────────────────────────────────────
 *
 * El plan pedía trazar los 5 íconos de categoría desde el PNG de referencia y vectorizarlos
 * con una herramienta de "Image Trace" (Illustrator/Inkscape/Figma) para importarlos como
 * `ImageVector` — un paso de producción gráfica externo a esta sesión de código (no hay
 * herramienta de vectorización de imágenes disponible aquí, solo búsqueda de imágenes web,
 * que no serviría para trazar UN ícono específico de una referencia que el usuario tiene en
 * local). Como aproximación funcional inmediata, se optó por dibujar a mano cada ícono con
 * primitivas de `Canvas` — el mismo patrón ya usado en el proyecto por `PetIcons.kt` y
 * `BottomBarIcon` (AreteBottomBar.kt) — en vez de usar `Icons.Outlined.*` genéricos de
 * Material, que el propio plan descarta explícitamente por no coincidir con el trazo de la
 * referencia. Si más adelante se vectoriza el PNG real, estos Composables son un reemplazo
 * directo 1:1 de cada `CategoriaIcon.*` por su `ImageVector` importado — ver [CategoryChipRow]
 * en ComunidadComponents.kt, que ya consume estos íconos por nombre de categoría.
 */
object CategoriaIcon {

    /** Psicología: perfil de cabeza clásico (línea de frente, nariz, mentón). */
    @Composable
    fun Psicologia(color: Color, modifier: Modifier = Modifier.size(28.dp)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.07f
            val perfil = Path().apply {
                moveTo(w * 0.30f, h * 0.88f)
                lineTo(w * 0.30f, h * 0.55f)
                cubicTo(w * 0.30f, h * 0.30f, w * 0.42f, h * 0.14f, w * 0.58f, h * 0.14f)
                cubicTo(w * 0.66f, h * 0.14f, w * 0.70f, h * 0.20f, w * 0.68f, h * 0.26f)
                cubicTo(w * 0.78f, h * 0.28f, w * 0.82f, h * 0.34f, w * 0.76f, h * 0.40f)
                lineTo(w * 0.80f, h * 0.46f)
                lineTo(w * 0.70f, h * 0.48f)
                lineTo(w * 0.70f, h * 0.60f)
                cubicTo(w * 0.70f, h * 0.72f, w * 0.62f, h * 0.80f, w * 0.52f, h * 0.82f)
                lineTo(w * 0.52f, h * 0.88f)
            }
            drawPath(perfil, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color = color, radius = w * 0.025f, center = Offset(w * 0.60f, h * 0.34f), style = Fill)
        }
    }

    /** Biblioteca: libro abierto con lomo central. */
    @Composable
    fun Biblioteca(color: Color, modifier: Modifier = Modifier.size(28.dp)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.07f
            val paginaIzq = Path().apply {
                moveTo(w * 0.50f, h * 0.28f)
                cubicTo(w * 0.38f, h * 0.18f, w * 0.22f, h * 0.18f, w * 0.14f, h * 0.24f)
                lineTo(w * 0.14f, h * 0.78f)
                cubicTo(w * 0.22f, h * 0.72f, w * 0.38f, h * 0.72f, w * 0.50f, h * 0.82f)
                close()
            }
            val paginaDer = Path().apply {
                moveTo(w * 0.50f, h * 0.28f)
                cubicTo(w * 0.62f, h * 0.18f, w * 0.78f, h * 0.18f, w * 0.86f, h * 0.24f)
                lineTo(w * 0.86f, h * 0.78f)
                cubicTo(w * 0.78f, h * 0.72f, w * 0.62f, h * 0.72f, w * 0.50f, h * 0.82f)
                close()
            }
            drawPath(paginaIzq, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawPath(paginaDer, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
    }

    /** Universidad: birrete con borla. */
    @Composable
    fun Universidad(color: Color, modifier: Modifier = Modifier.size(28.dp)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.06f
            val tabla = Path().apply {
                moveTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.88f, h * 0.36f)
                lineTo(w * 0.50f, h * 0.56f)
                lineTo(w * 0.12f, h * 0.36f)
                close()
            }
            drawPath(tabla, color = color, style = Fill)
            val base = Path().apply {
                moveTo(w * 0.28f, h * 0.44f)
                lineTo(w * 0.28f, h * 0.62f)
                cubicTo(w * 0.28f, h * 0.72f, w * 0.72f, h * 0.72f, w * 0.72f, h * 0.62f)
                lineTo(w * 0.72f, h * 0.44f)
            }
            drawPath(base, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color = color, start = Offset(w * 0.86f, h * 0.36f), end = Offset(w * 0.86f, h * 0.62f), strokeWidth = stroke * 0.6f, cap = StrokeCap.Round)
            drawCircle(color = color, radius = w * 0.05f, center = Offset(w * 0.86f, h * 0.68f), style = Fill)
        }
    }

    /** Sostenibilidad: hoja con nervadura central. */
    @Composable
    fun Sostenibilidad(color: Color, modifier: Modifier = Modifier.size(28.dp)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.07f
            val hoja = Path().apply {
                moveTo(w * 0.50f, h * 0.86f)
                cubicTo(w * 0.20f, h * 0.80f, w * 0.14f, h * 0.46f, w * 0.30f, h * 0.16f)
                cubicTo(w * 0.55f, h * 0.20f, w * 0.86f, h * 0.36f, w * 0.86f, h * 0.62f)
                cubicTo(w * 0.86f, h * 0.76f, w * 0.68f, h * 0.86f, w * 0.50f, h * 0.86f)
                close()
            }
            drawPath(hoja, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color = color, start = Offset(w * 0.30f, h * 0.18f), end = Offset(w * 0.54f, h * 0.80f), strokeWidth = stroke * 0.6f, cap = StrokeCap.Round)
        }
    }

    /** Tecnología: chip/circuito con pines. */
    @Composable
    fun Tecnologia(color: Color, modifier: Modifier = Modifier.size(28.dp)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.06f
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.26f, h * 0.26f),
                size = Size(w * 0.48f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                style = Stroke(width = stroke)
            )
            drawCircle(color = color, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = stroke * 0.7f))
            val posiciones = listOf(0.36f, 0.50f, 0.64f)
            posiciones.forEach { px ->
                drawLine(color = color, start = Offset(w * px, h * 0.10f), end = Offset(w * px, h * 0.26f), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(w * px, h * 0.74f), end = Offset(w * px, h * 0.90f), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
            }
            posiciones.forEach { py ->
                drawLine(color = color, start = Offset(w * 0.10f, h * py), end = Offset(w * 0.26f, h * py), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(w * 0.74f, h * py), end = Offset(w * 0.90f, h * py), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
            }
        }
    }
}

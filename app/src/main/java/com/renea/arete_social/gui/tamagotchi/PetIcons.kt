package com.renea.arete_social.gui.tamagotchi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Íconos propios de la mascota, dibujados con primitivas de Canvas (la app evita la
 * dependencia material-icons-extended). Todos son de un solo color y se tiñen con [color],
 * así siguen el tema claro/oscuro y el estado habilitado/deshabilitado de cada botón.
 */
object PetIcons {

    private val DefaultSize = Modifier.size(24.dp)

    @Composable
    fun Feed(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            // Plato: óvalo aplanado en la base + tres bolitas de comida.
            drawOval(color, Offset(w * 0.08f, h * 0.55f), Size(w * 0.84f, h * 0.32f))
            val r = w * 0.11f
            drawCircle(color, r, Offset(w * 0.50f, h * 0.30f))
            drawCircle(color, r, Offset(w * 0.30f, h * 0.46f))
            drawCircle(color, r, Offset(w * 0.70f, h * 0.46f))
        }
    }

    @Composable
    fun Play(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outer = size.width * 0.48f
            val inner = size.width * 0.16f
            val star = Path().apply {
                moveTo(cx, cy - outer)
                lineTo(cx + inner, cy - inner)
                lineTo(cx + outer, cy)
                lineTo(cx + inner, cy + inner)
                lineTo(cx, cy + outer)
                lineTo(cx - inner, cy + inner)
                lineTo(cx - outer, cy)
                lineTo(cx - inner, cy - inner)
                close()
            }
            drawPath(star, color = color)
        }
    }

    @Composable
    fun Clean(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val drop = Path().apply {
                moveTo(w * 0.5f, h * 0.05f)
                quadraticTo(w * 0.95f, h * 0.55f, w * 0.5f, h * 0.95f)
                quadraticTo(w * 0.05f, h * 0.55f, w * 0.5f, h * 0.05f)
                close()
            }
            drawPath(drop, color = color)
        }
    }

    @Composable
    fun Sleep(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val full = Path().apply { addOval(Rect(Offset(w * 0.10f, h * 0.10f), Size(w * 0.80f, h * 0.80f))) }
            val cut = Path().apply { addOval(Rect(Offset(w * 0.32f, h * 0.02f), Size(w * 0.80f, h * 0.80f))) }
            val crescent = Path().apply { op(full, cut, PathOperation.Difference) }
            drawPath(crescent, color = color)
        }
    }

    @Composable
    fun Wake(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val center = Offset(w / 2f, size.height / 2f)
            val r = w * 0.22f
            drawCircle(color = color, radius = r, center = center)
            for (deg in 0 until 360 step 45) {
                val rad = Math.toRadians(deg.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                drawLine(
                    color = color,
                    start = Offset(center.x + dx * r * 1.35f, center.y + dy * r * 1.35f),
                    end = Offset(center.x + dx * r * 2.1f, center.y + dy * r * 2.1f),
                    strokeWidth = w * 0.06f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    @Composable
    fun Heal(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val arm = w * 0.28f
            val corner = CornerRadius(arm * 0.3f)
            drawRoundRect(color, Offset((w - arm) / 2f, h * 0.08f), Size(arm, h * 0.84f), corner)
            drawRoundRect(color, Offset(w * 0.08f, (h - arm) / 2f), Size(w * 0.84f, arm), corner)
        }
    }

    /** Rayo: modo turbo (XP doble). */
    @Composable
    fun Train(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val bolt = Path().apply {
                moveTo(w * 0.58f, h * 0.04f)
                lineTo(w * 0.20f, h * 0.56f)
                lineTo(w * 0.47f, h * 0.56f)
                lineTo(w * 0.40f, h * 0.96f)
                lineTo(w * 0.80f, h * 0.40f)
                lineTo(w * 0.53f, h * 0.40f)
                close()
            }
            drawPath(bolt, color = color)
        }
    }

    /** Reiniciar: flecha circular. */
    @Composable
    fun Reboot(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val stroke = w * 0.11f
            val inset = w * 0.18f
            drawArc(
                color = color,
                startAngle = -60f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2, w - inset * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Punta de flecha arriba a la derecha
            val tip = Path().apply {
                moveTo(w * 0.80f, w * 0.10f)
                lineTo(w * 0.82f, w * 0.38f)
                lineTo(w * 0.56f, w * 0.30f)
                close()
            }
            drawPath(tip, color = color)
        }
    }

    /** Chispa de 4 puntas con un puntito: moneda social de la mascota. */
    @Composable
    fun Spark(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val star = Path().apply {
                moveTo(w * 0.42f, h * 0.06f)
                quadraticTo(w * 0.44f, h * 0.44f, w * 0.80f, h * 0.48f)
                quadraticTo(w * 0.44f, h * 0.54f, w * 0.42f, h * 0.92f)
                quadraticTo(w * 0.40f, h * 0.54f, w * 0.04f, h * 0.48f)
                quadraticTo(w * 0.40f, h * 0.44f, w * 0.42f, h * 0.06f)
                close()
            }
            drawPath(star, color = color)
            drawCircle(color, w * 0.09f, Offset(w * 0.84f, h * 0.18f))
        }
    }

    /** Corazón: ánimo. */
    @Composable
    fun Heart(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val heart = Path().apply {
                moveTo(w * 0.5f, h * 0.90f)
                cubicTo(w * -0.10f, h * 0.50f, w * 0.15f, h * 0.05f, w * 0.5f, h * 0.30f)
                cubicTo(w * 0.85f, h * 0.05f, w * 1.10f, h * 0.50f, w * 0.5f, h * 0.90f)
                close()
            }
            drawPath(heart, color = color)
        }
    }

    /** Pila: energía. */
    @Composable
    fun Battery(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.09f
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.12f, h * 0.22f),
                size = Size(w * 0.70f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.08f),
                style = Stroke(width = stroke)
            )
            drawRoundRect(color, Offset(w * 0.84f, h * 0.38f), Size(w * 0.06f, h * 0.24f), CornerRadius(w * 0.02f))
            drawRoundRect(color, Offset(w * 0.22f, h * 0.32f), Size(w * 0.34f, h * 0.36f), CornerRadius(w * 0.03f))
        }
    }

    /** Flecha hacia atrás (chevron). */
    @Composable
    fun Back(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val chevron = Path().apply {
                moveTo(w * 0.62f, h * 0.20f)
                lineTo(w * 0.32f, h * 0.50f)
                lineTo(w * 0.62f, h * 0.80f)
            }
            drawPath(
                chevron, color = color,
                style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    /** Cambiar imagen (flechas cruzadas simplificadas: dos óvalos). */
    @Composable
    fun Scene(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.10f, h * 0.20f),
                size = Size(w * 0.80f, h * 0.60f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = Stroke(width = w * 0.09f)
            )
            drawCircle(color, w * 0.08f, Offset(w * 0.32f, h * 0.40f))
            val hills = Path().apply {
                moveTo(w * 0.14f, h * 0.76f)
                lineTo(w * 0.42f, h * 0.50f)
                lineTo(w * 0.60f, h * 0.68f)
                lineTo(w * 0.72f, h * 0.56f)
                lineTo(w * 0.86f, h * 0.76f)
                close()
            }
            drawPath(hills, color = color)
        }
    }

    /** Burbuja de chat. */
    @Composable
    fun Chat(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val bubble = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        Rect(Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.58f)),
                        CornerRadius(w * 0.16f)
                    )
                )
                moveTo(w * 0.30f, h * 0.70f)
                lineTo(w * 0.26f, h * 0.92f)
                lineTo(w * 0.52f, h * 0.72f)
                close()
            }
            drawPath(bubble, color = color)
        }
    }

    /** Flecha de enviar. */
    @Composable
    fun Send(modifier: Modifier = DefaultSize, color: Color) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val plane = Path().apply {
                moveTo(w * 0.10f, h * 0.12f)
                lineTo(w * 0.92f, h * 0.50f)
                lineTo(w * 0.10f, h * 0.88f)
                lineTo(w * 0.24f, h * 0.50f)
                close()
            }
            drawPath(plane, color = color)
        }
    }
}

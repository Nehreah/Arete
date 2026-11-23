package com.renea.arete_social.gui.auth.perfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.gui.theme.DisenoConfig

internal const val PESTANA_BITACORA = 0
internal const val PESTANA_AMIGOS = 1
internal const val PESTANA_MENSAJES = 2
internal const val PESTANA_CARTAS = 3
internal const val PESTANA_LIBRERIA = 4

// Medidas de la cabecera, compartidas con el perfil de otras personas para que se vean igual.
internal val AlturaPortadaPerfil = 268.dp
internal val SolapePestanasPerfil = 34.dp
internal val AlturaPestanasPerfil = 68.dp

/** Divisor orgánico para que el borde del perfil dialogue con las pestañas curvas. */
@Composable
internal fun DivisorOndulado(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height * 0.55f)
            cubicTo(size.width * 0.14f, -size.height * 0.10f, size.width * 0.20f, size.height * 1.10f, size.width * 0.36f, size.height * 0.52f)
            cubicTo(size.width * 0.52f, -size.height * 0.08f, size.width * 0.58f, size.height * 1.08f, size.width * 0.72f, size.height * 0.50f)
            cubicTo(size.width * 0.86f, -size.height * 0.08f, size.width * 0.92f, size.height * 1.08f, size.width, size.height * 0.52f)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * Fondo táctico de retícula de radar concéntrica detrás del avatar.
 */
@Composable
internal fun FondoRadarReticle(
    modifier: Modifier = Modifier
) {
    val reticleColorBase = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(size.width, size.height) / 2f
        val reticleColor = reticleColorBase.copy(alpha = 0.09f)
        val stroke = Stroke(width = 1.dp.toPx())

        // Círculos concéntricos
        drawCircle(color = reticleColor, radius = maxR * 0.40f, style = stroke)
        drawCircle(color = reticleColor, radius = maxR * 0.70f, style = stroke)
        drawCircle(color = reticleColor, radius = maxR * 0.98f, style = stroke)

        // Cruz radial
        drawLine(reticleColor, Offset(cx, cy - maxR * 1.05f), Offset(cx, cy + maxR * 1.05f), strokeWidth = 1.dp.toPx())
        drawLine(reticleColor, Offset(cx - maxR * 1.05f, cy), Offset(cx + maxR * 1.05f, cy), strokeWidth = 1.dp.toPx())

        // Diagonales suaves
        val diag = maxR * 0.85f
        drawLine(reticleColor.copy(alpha = 0.04f), Offset(cx - diag, cy - diag), Offset(cx + diag, cy + diag), strokeWidth = 1.dp.toPx())
        drawLine(reticleColor.copy(alpha = 0.04f), Offset(cx + diag, cy - diag), Offset(cx - diag, cy + diag), strokeWidth = 1.dp.toPx())
    }
}

/**
 * Contenedor táctico con corchetes en las 4 esquinas según el diseño de Perfil.jpeg.
 */
@Composable
internal fun TarjetaTacticalBox(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
    cornerColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    content: @Composable () -> Unit
) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    val bordeFinal = if (esNuevo) MaterialTheme.colorScheme.outline.copy(alpha = 0.18f) else borderColor
    val fondoFinal = if (esNuevo) MaterialTheme.colorScheme.surface else backgroundColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fondoFinal)
            .border(if (esNuevo) 0.8.dp else 1.dp, bordeFinal, RoundedCornerShape(16.dp))
    ) {
        if (!esNuevo) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeW = 1.6.dp.toPx()
                val bracketLen = 13.dp.toPx()
                val inset = 5.dp.toPx()

                // Top-left
                drawLine(cornerColor, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeW)
                drawLine(cornerColor, Offset(inset, inset), Offset(inset, inset + bracketLen), strokeW)

                // Top-right
                drawLine(cornerColor, Offset(size.width - inset, inset), Offset(size.width - inset - bracketLen, inset), strokeW)
                drawLine(cornerColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + bracketLen), strokeW)

                // Bottom-left
                drawLine(cornerColor, Offset(inset, size.height - inset), Offset(inset + bracketLen, size.height - inset), strokeW)
                drawLine(cornerColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - bracketLen), strokeW)

                // Bottom-right
                drawLine(cornerColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - bracketLen, size.height - inset), strokeW)
                drawLine(cornerColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - bracketLen), strokeW)
            }
        }
        content()
    }
}

/**
 * Ícono de onda de pulso cardíaco/ECG en canvas [ ─/\_─ ].
 */
@Composable
internal fun IconoOndaCardiaca(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
) {
    Canvas(modifier = modifier.size(width = 38.dp, height = 16.dp)) {
        val stroke = 1.5.dp.toPx()
        val h = size.height
        val w = size.width
        val cy = h / 2f

        drawLine(color, Offset(0f, cy), Offset(w * 0.32f, cy), stroke)
        drawLine(color, Offset(w * 0.32f, cy), Offset(w * 0.44f, cy - h * 0.45f), stroke)
        drawLine(color, Offset(w * 0.44f, cy - h * 0.45f), Offset(w * 0.56f, cy + h * 0.45f), stroke)
        drawLine(color, Offset(w * 0.56f, cy + h * 0.45f), Offset(w * 0.68f, cy), stroke)
        drawLine(color, Offset(w * 0.68f, cy), Offset(w, cy), stroke)
    }
}

/** Botón de acción de la portada: rectángulo de esquinas redondeadas, color sólido. */
@Composable
internal fun PerfilIconButton(texto: String, descripcion: String, onClick: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.size(40.dp)
            .shadow(3.dp, forma)
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(texto, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Barra de 3 pestañas: botones rectangulares de esquinas redondeadas, color sólido,
 * mismo ancho y repartidos de forma simétrica a lo largo de la pantalla.
 */
@Composable
internal fun BarraPestanasPerfil(
    pestanaActiva: Int,
    onPestanaCambia: (Int) -> Unit,
    numBitacora: Int,
    numAmigos: Int,
    numSolicitudes: Int,
    numMensajes: Int,
    numCartasSinLeer: Int,
    numObras: Int
) {
    val items = listOf(
        Triple("▤", "Bitácora", numBitacora),
        Triple("♡", "Amigos", numAmigos),
        Triple("✉", "Mensajes", numMensajes),
        Triple("✎", "Cartas", 0),
        Triple("❦", "Librería", numObras)
    )
    val insignias = mapOf(PESTANA_AMIGOS to numSolicitudes, PESTANA_CARTAS to numCartasSinLeer)
    BarraPestanasSocial(items, insignias, pestanaActiva, onPestanaCambia)
}

/**
 * Barra de pestañas con el estilo del perfil: tarjetas redondeadas de igual ancho.
 * La reutilizan el perfil propio y el de otras personas (que muestra menos pestañas).
 * [items] = (icono, título, contador); [insignias] = índice -> avisos pendientes.
 */
@Composable
fun BarraPestanasSocial(
    items: List<Triple<String, String, Int>>,
    insignias: Map<Int, Int>,
    pestanaActiva: Int,
    onPestanaCambia: (Int) -> Unit
) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = pestanaActiva == index
            val fondo = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val contenido = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val acento = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .shadow(if (selected) 6.dp else 3.dp, forma)
                    .clip(forma)
                    .background(fondo)
                    .clickable { onPestanaCambia(index) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.first, color = acento, fontSize = 18.sp)
                        if (item.third > 0) Text(item.third.toString(), color = acento, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(item.second, color = contenido, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
                val pendientes = insignias[index] ?: 0
                if (pendientes > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) { Text(pendientes.toString(), color = MaterialTheme.colorScheme.onError, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

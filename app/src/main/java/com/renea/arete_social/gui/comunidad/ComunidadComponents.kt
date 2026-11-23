package com.renea.arete_social.gui.comunidad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.renea.arete_social.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.gui.theme.AntiredGreenAccent
import com.renea.arete_social.gui.theme.CategoriaBibliotecaBg
import com.renea.arete_social.gui.theme.CategoriaBibliotecaIcono
import com.renea.arete_social.gui.theme.CategoriaPsicologiaBg
import com.renea.arete_social.gui.theme.CategoriaPsicologiaIcono
import com.renea.arete_social.gui.theme.CategoriaSostenibilidadBg
import com.renea.arete_social.gui.theme.CategoriaSostenibilidadIcono
import com.renea.arete_social.gui.theme.CategoriaTecnologiaBg
import com.renea.arete_social.gui.theme.CategoriaTecnologiaIcono
import com.renea.arete_social.gui.theme.CategoriaUniversidadBg
import com.renea.arete_social.gui.theme.CategoriaUniversidadIcono

/**
 * ── Plan de Fidelidad Visual v2, sección 4 — componentes de Comunidad ─────────────────
 *
 * Estos 4 componentes ([HeroCreateCommunityCard], [CategoryChipRow], [CommunityListItemCard],
 * [AvataresApilados]) están escritos exactamente como los especifica el plan, tomando sus
 * colores de los tokens nuevos en `gui/theme/Color.kt`.
 *
 * **Por qué no se reemplazó ya `ComunidadesScreen.kt` con ellos:** esa pantalla, tal como
 * está hoy, es una implementación ya terminada y fiel a OTRO mockup previo (ver su propio
 * comentario de cabecera: "rediseñada fiel al mockup Comunidad.jpeg" — estética táctica de
 * "BASE DE DATOS / NODOS DISPONIBLES"). El plan v2 aquí adjunto describe una dirección
 * visual distinta (tarjeta hero con foto de campus + esquina cortada, chips circulares de
 * categoría, "Activo ahora"), pero esta conversación no incluyó la imagen de referencia de
 * ESA pantalla — solo la hoja de sprites del gato. Reemplazar una pantalla que ya cumple
 * fielmente un mockup aprobado por otra sin ver la referencia nueva habría sido una
 * apuesta, no una corrección. Los componentes están listos: para integrarlos basta con
 * reemplazar el cuerpo de `ComunidadesScreen.kt` por ellos (`HeroCreateCommunityCard` +
 * `CategoryChipRow` + una `LazyColumn` de `CommunityListItemCard`) en cuanto confirmes que
 * es la dirección visual que quieres, o me compartas la imagen de esa pantalla.
 */

import com.renea.arete_social.gui.theme.AmbienteAssets
import com.renea.arete_social.gui.theme.LocalCicloDia

private class TarjetaConEsquinaCortada(private val cortePx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - cortePx, 0f)
            lineTo(size.width, size.height * 0.35f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Tarjeta hero "Crear nueva comunidad" con esquina superior derecha cortada en diagonal y
 * foto del campus (día/noche) recortada dentro de esa forma.
 */
@Composable
fun HeroCreateCommunityCard(
    imagenCampusUrl: String?,
    onCrearComunidad: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Conserva el ciclo día/noche global (y su preferencia persistida) para elegir
    // el recurso ambiental correcto, aunque el hero ahora ocupe toda la barra.
    val cicloActual = LocalCicloDia.current
    val resId = AmbienteAssets.plazoletaComunidad(cicloActual)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(174.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF183B35))
    ) {
        if (!imagenCampusUrl.isNullOrBlank()) {
            AsyncImage(
                model = imagenCampusUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
        } else {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight().background(Color(0x990A211D)))
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Parchate", color = Color.White, fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Encuentra tu gente, crea tu parche",
                color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp
            )
        }
    }
}

/**
 * Tarjeta Hero integrada para la pantalla "Parche".
 * Integra el encabezado dinámico ("Parchate", o el nombre del chat/mural activo)
 * con las 3 pestañas compactas ("Comunidades", "Salas", "Murales"), optimizando
 * el espacio vertical de la pantalla.
 */
@Composable
fun HeroParcheIntegradoCard(
    pestanaActiva: Int,
    onPestanaSeleccionada: (Int) -> Unit,
    modifier: Modifier = Modifier,
    titulo: String = "Parchate",
    subtitulo: String? = "Encuentra tu gente, crea tu parche",
    imagenCampusUrl: String? = null,
    onAtras: (() -> Unit)? = null
) {
    val cicloActual = LocalCicloDia.current
    val resId = AmbienteAssets.plazoletaComunidad(cicloActual)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF132D28))
    ) {
        if (!imagenCampusUrl.isNullOrBlank()) {
            AsyncImage(
                model = imagenCampusUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }

        // Overlay oscuro para contraste del texto y controles
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xB30A1C18))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cabecera superior: botón de regreso opcional + Título y Subtítulo dinámico
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onAtras != null) {
                    Surface(
                        onClick = onAtras,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "‹",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        color = Color.White,
                        fontSize = if (titulo.length > 20) 19.sp else 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1
                    )
                    if (!subtitulo.isNullOrBlank()) {
                        Text(
                            text = subtitulo,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Pestañas integradas con tipografía optimizada (Comunidades, Salas, Murales)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("Comunidades", "Eventos", "Salas", "Murales").forEachIndexed { index, nombre ->
                    val activa = pestanaActiva == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (activa) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onPestanaSeleccionada(index) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nombre,
                            color = if (activa) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                            fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Fila horizontal de las comunidades creadas mostradas como chips circulares interactivos. */
@Composable
fun CommunityChipRow(
    comunidades: List<Comunidad>,
    onComunidadSeleccionada: (String) -> Unit,
    onCrearComunidad: () -> Unit,
    modifier: Modifier = Modifier
) {

    val coloresBase = listOf(
        Color(0xFF34D399) to Color(0xFF064E3B), // Menta/Verde
        Color(0xFFE066A6) to Color(0xFF831843), // Rosa/Magenta
        Color(0xFFD9A54A) to Color(0xFF78350F), // Ámbar/Dorado
        Color(0xFF7C6FB0) to Color(0xFF312E81), // Púrpura/Azul
        Color(0xFF38BDF8) to Color(0xFF075985)  // Cian/Celeste
    )

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp).clickable(onClick = onCrearComunidad)
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 34.sp, fontWeight = FontWeight.Light)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Crear comunidad", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
            items(comunidades) { comunidad ->
                val colorIdx = kotlin.math.abs(comunidad.id.hashCode()) % coloresBase.size
                val (colorIcono, colorFondo) = coloresBase[colorIdx]
                val inicial = comunidad.nombre.take(1).uppercase()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onComunidadSeleccionada(comunidad.id) }
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colorFondo)
                                .border(1.5.dp, colorIcono, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!comunidad.fotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = comunidad.fotoUrl,
                                    contentDescription = comunidad.nombre,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = inicial,
                                    color = colorIcono,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .clip(CircleShape)
                                .background(AntiredGreenAccent)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = comunidad.nombre,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Hasta 3 avatares apilados con solapa negativa, más un "+N" si hay más miembros. */
@Composable
fun AvataresApilados(urls: List<String>, maxVisible: Int = 3, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        urls.take(maxVisible).forEachIndexed { index, url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
        if (urls.size > maxVisible) {
            Box(
                modifier = Modifier
                    .offset(x = (-8 * maxVisible).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+${urls.size - maxVisible}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tarjeta de comunidad con avatares apilados, conteo de miembros y botón "Unirse". */
@Composable
fun CommunityListItemCard(
    comunidad: Comunidad,
    activaAhora: Boolean,
    onUnirse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconoGrupo(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(comunidad.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(comunidad.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvataresApilados(urls = comunidad.avataresPreview, maxVisible = 3)
                    Spacer(Modifier.width(8.dp))
                    Text("${comunidad.miembrosCount} en la comunidad", style = MaterialTheme.typography.labelSmall)
                    if (activaAhora) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AntiredGreenAccent))
                        Spacer(Modifier.width(4.dp))
                        Text("Activo ahora", style = MaterialTheme.typography.labelSmall, color = AntiredGreenAccent)
                    }
                }
            }
            OutlinedButton(onClick = onUnirse, shape = RoundedCornerShape(50)) {
                Text("Unirse")
                Spacer(Modifier.width(4.dp))
                IconoChevronDerecha(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** "+" dibujado a mano — el proyecto evita la dependencia material-icons-extended (ver PetIcons.kt). */
@Composable
private fun IconoMas(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        drawLine(color, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.9f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Grupo de personas (mismo trazo que TabNavegacion.COMUNIDAD en AreteBottomBar.kt). */
@Composable
private fun IconoGrupo(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        drawCircle(color = color, radius = w * 0.17f, center = Offset(w * 0.42f, h * 0.30f), style = Stroke(width = stroke))
        val frente = Path().apply {
            moveTo(w * 0.16f, h * 0.86f)
            cubicTo(w * 0.18f, h * 0.58f, w * 0.30f, h * 0.54f, w * 0.42f, h * 0.54f)
            cubicTo(w * 0.54f, h * 0.54f, w * 0.66f, h * 0.58f, w * 0.68f, h * 0.86f)
        }
        drawPath(frente, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(color = color, radius = w * 0.13f, center = Offset(w * 0.72f, h * 0.28f), style = Stroke(width = stroke))
        val fondo = Path().apply {
            moveTo(w * 0.68f, h * 0.56f)
            cubicTo(w * 0.76f, h * 0.56f, w * 0.86f, h * 0.62f, w * 0.88f, h * 0.82f)
        }
        drawPath(fondo, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

/** Chevron/flecha ">" del botón "Unirse". */
@Composable
private fun IconoChevronDerecha(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.16f
        val path = Path().apply {
            moveTo(w * 0.30f, h * 0.15f)
            lineTo(w * 0.75f, h * 0.5f)
            lineTo(w * 0.30f, h * 0.85f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

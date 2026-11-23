package com.renea.arete_social.gui.auth.perfil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.gui.theme.RadarTextField
import com.renea.arete_social.gui.theme.LocalRadarColors

/**
 * Overlay interactivo que simula daño de información / interferencia digital cyber.
 */
@Composable
fun EfectoFiltroMatrix(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_anim")
    val glitchProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glitch_progress"
    )

    val scanlinesColor = LocalRadarColors.current.scrimSubtle

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Scanlines CRT finas y transparentes
            val scanStep = 4.dp.toPx()
            var y = 0f
            while (y < h) {
                drawLine(
                    color = scanlinesColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += scanStep
            }

            // 2. Barra de escaneo descendente transparente
            val scanY = glitchProgress * h
            drawRect(
                color = scanlinesColor,
                topLeft = Offset(0f, scanY - 6.dp.toPx()),
                size = Size(w, 12.dp.toPx())
            )

            // 3. Líneas sutiles de interferencia digital
            val glitch1Y = ((glitchProgress * 3.8f) % 1f) * h
            drawRect(
                color = scanlinesColor.copy(alpha = 0.25f),
                topLeft = Offset(0f, glitch1Y),
                size = Size(w, 2.dp.toPx())
            )

            val glitch2Y = (((glitchProgress + 0.45f) * 1.8f) % 1f) * h
            drawRect(
                color = scanlinesColor.copy(alpha = 0.2f),
                topLeft = Offset(w * 0.15f, glitch2Y),
                size = Size(w * 0.7f, 2.5.dp.toPx())
            )
        }

        // Viñeta muy suave en los bordes que no oscurece la foto
        val fondoFiltro = LocalRadarColors.current.fondo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            fondoFiltro.copy(alpha = 0.35f)
                        )
                    )
                )
        )

        // Etiqueta de telemetría digital en la esquina
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LocalRadarColors.current.panel.copy(alpha = 0.8f))
                .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = "// DATA_LOG: 0x7F",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 7.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Tarjeta de Foto con Filtro de Daño de Información Tipo Matrix.
 */
@Composable
fun TarjetaFotoMatrix(
    publicacion: PublicacionPerfil,
    esPropia: Boolean,
    usuarioIdActual: String,
    onLikeClick: () -> Unit,
    onEliminarClick: () -> Unit = {}
) {
    val yaDioLike = publicacion.usuariosLike.contains(usuarioIdActual)
    val bitmapDecodificado: Bitmap? = remember(publicacion.contenido) {
        val str = publicacion.contenido
        if (str.startsWith("data:image/")) {
            runCatching {
                val base64Data = str.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        ) {
            if (bitmapDecodificado != null) {
                Image(
                    bitmap = bitmapDecodificado.asImageBitmap(),
                    contentDescription = "Foto Digital",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = publicacion.contenido,
                    contentDescription = "Foto Digital",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            EfectoFiltroMatrix(modifier = Modifier.fillMaxSize())
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalRadarColors.current.panel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (yaDioLike) LocalRadarColors.current.successContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (yaDioLike) LocalRadarColors.current.onSuccessContainer else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { onLikeClick() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(if (yaDioLike) "💚" else "🤍", fontSize = 11.sp)
                        Text(
                            text = "${publicacion.likes}",
                            color = if (yaDioLike) LocalRadarColors.current.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Text(
                    text = "⏳ ${publicacion.diasRestantes()}d restantes",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Default
                )
            }

            if (esPropia) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(LocalRadarColors.current.dangerContainer)
                        .border(1.dp, LocalRadarColors.current.onDangerContainer.copy(alpha = 0.5f), CircleShape)
                        .clickable { onEliminarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑️", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Tarjeta de Nota Escrita con Letra Pequeña y Estilo Retro.
 */
@Composable
fun TarjetaNotaRetro(
    publicacion: PublicacionPerfil,
    esPropia: Boolean,
    usuarioIdActual: String,
    onLikeClick: () -> Unit,
    onEliminarClick: () -> Unit = {}
) {
    val yaDioLike = publicacion.usuariosLike.contains(usuarioIdActual)

    TarjetaTacticalBox(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "TRANSMISIÓN // ${publicacion.tiempoTranscurrido().uppercase()}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconoOndaCardiaca(
                        modifier = Modifier
                            .width(36.dp)
                            .height(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "⏳ ${publicacion.diasRestantes()}d",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = publicacion.contenido,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (yaDioLike) LocalRadarColors.current.successContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            if (yaDioLike) LocalRadarColors.current.onSuccessContainer else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onLikeClick() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⚡", fontSize = 11.sp)
                        Text(
                            text = "LIKE (${publicacion.likes})",
                            color = if (yaDioLike) LocalRadarColors.current.onSuccessContainer else MaterialTheme.colorScheme.primary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (esPropia) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(LocalRadarColors.current.dangerContainer)
                            .border(1.dp, LocalRadarColors.current.onDangerContainer.copy(alpha = 0.5f), CircleShape)
                            .clickable { onEliminarClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🗑️", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Cuadro de texto para redactar una nueva nota retro semanal según mockup Perfil.jpeg.
 */
@Composable
internal fun CajaEscribirNotaRetro(
    onPublicar: (String) -> Unit,
    onSubirImagen: () -> Unit
) {
    var texto by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
            .padding(15.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)
        ) {
            Column {
                RadarTextField(
                    valor = texto,
                    onValorCambia = { texto = it },
                    etiqueta = "Bitácora... ¿qué quieres dejar por aquí?",
                    lineasMaximas = 4,
                    estiloTexto = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                    tamanoEtiqueta = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onSubirImagen)
                            .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("▧", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                        Text("Añadir imagen", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(22.dp))
                            .background(if (texto.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = texto.isNotBlank()) { onPublicar(texto); texto = "" }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Publicar  ↗", color = if (texto.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Las publicaciones siguen la vigencia actual de Aréte.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 9.sp)
    }
}

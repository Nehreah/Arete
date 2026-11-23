package com.renea.arete_social.gui.auth.perfil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Diálogo interactivo para recortar, escalar, rotar y centrar perfectamente
 * la foto de perfil antes de subirla.
 */
@Composable
fun EditorRecorteFotoDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onFotoRecortada: (bytes: ByteArray, extension: String) -> Unit
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmapOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var rotacionGrados by remember { mutableIntStateOf(0) }
    var escala by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var procesandoRecorte by remember { mutableStateOf(false) }

    // Cargar Bitmap en background
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                contexto.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let {
                bitmapOriginal = it
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Cabecera
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AJUSTAR Y CENTRAR FOTO",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Arrastra para centrar • Pellizca o usa el control para zoom",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Área de Recorte / Canvas
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                escala = (escala * zoom).coerceIn(1f, 4f)
                                val maxOffset = 250f * (escala - 1f) + 120f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    bitmapOriginal?.let { btmp ->
                        Image(
                            bitmap = btmp.asImageBitmap(),
                            contentDescription = "Foto en edición",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = escala
                                    scaleY = escala
                                    translationX = offsetX
                                    translationY = offsetY
                                    rotationZ = rotacionGrados.toFloat()
                                }
                        )
                    }

                    // Máscara y Guías estilo Radar
                    val colorPrimario = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radio = size.minDimension * 0.44f
                        val centro = Offset(size.width / 2f, size.height / 2f)

                        // Oscurecer exterior del círculo
                        val circuloPath = Path().apply {
                            addOval(Rect(centro.x - radio, centro.y - radio, centro.x + radio, centro.y + radio))
                        }
                        clipPath(circuloPath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.65f))
                        }

                        // Borde de la mirilla
                        drawCircle(
                            color = colorPrimario,
                            radius = radio,
                            center = centro,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Guías de regla de tercios
                        val tercio = radio * 2f / 3f
                        val inicioX = centro.x - radio
                        val inicioY = centro.y - radio

                        drawLine(
                            color = colorPrimario.copy(alpha = 0.35f),
                            start = Offset(inicioX + tercio, inicioY),
                            end = Offset(inicioX + tercio, inicioY + radio * 2f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = colorPrimario.copy(alpha = 0.35f),
                            start = Offset(inicioX + tercio * 2f, inicioY),
                            end = Offset(inicioX + tercio * 2f, inicioY + radio * 2f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = colorPrimario.copy(alpha = 0.35f),
                            start = Offset(inicioX, inicioY + tercio),
                            end = Offset(inicioX + radio * 2f, inicioY + tercio),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = colorPrimario.copy(alpha = 0.35f),
                            start = Offset(inicioX, inicioY + tercio * 2f),
                            end = Offset(inicioX + radio * 2f, inicioY + tercio * 2f),
                            strokeWidth = 1f
                        )

                        // Cruz central de calibración
                        val cruzLen = 14.dp.toPx()
                        drawLine(
                            color = colorPrimario.copy(alpha = 0.8f),
                            start = Offset(centro.x - cruzLen, centro.y),
                            end = Offset(centro.x + cruzLen, centro.y),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            color = colorPrimario.copy(alpha = 0.8f),
                            start = Offset(centro.x, centro.y - cruzLen),
                            end = Offset(centro.x, centro.y + cruzLen),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                // Controles de Ajuste (Zoom, Rotar, Centrar)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Slider de Zoom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ZOOM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Slider(
                            value = escala,
                            onValueChange = { escala = it },
                            valueRange = 1f..4f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(escala * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Botones Rápidos (Rotar + Centrar)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadarOutlinedButton(
                            texto = "🔄 Rotar 90°",
                            onClick = { rotacionGrados = (rotacionGrados + 90) % 360 },
                            modifier = Modifier.weight(1f)
                        )
                        RadarOutlinedButton(
                            texto = "🎯 Re-Centrar",
                            onClick = {
                                escala = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Botones Finales
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadarOutlinedButton(
                        texto = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    RadarButton(
                        texto = if (procesandoRecorte) "RECORTANDO..." else "APLICAR RECORTE",
                        habilitado = !procesandoRecorte && bitmapOriginal != null,
                        onClick = {
                            val btmp = bitmapOriginal ?: return@RadarButton
                            procesandoRecorte = true
                            scope.launch(Dispatchers.IO) {
                                val bytes = generarRecorte(
                                    bitmap = btmp,
                                    escala = escala,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    rotacionGrados = rotacionGrados
                                )
                                withContext(Dispatchers.Main) {
                                    procesandoRecorte = false
                                    onFotoRecortada(bytes, "jpg")
                                }
                            }
                        },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

/**
 * Realiza la transformación geométrica del bitmap, rota, recorta al área central
 * cuadrada seleccionada por el usuario y comprime a JPEG.
 */
private fun generarRecorte(
    bitmap: Bitmap,
    escala: Float,
    offsetX: Float,
    offsetY: Float,
    rotacionGrados: Int
): ByteArray {
    // 1. Aplicar rotación si la hay
    val matrix = Matrix().apply {
        if (rotacionGrados != 0) {
            postRotate(rotacionGrados.toFloat())
        }
    }
    val rotado = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
    )

    // 2. Determinar el tamaño del corte cuadrado
    val ladoMin = min(rotado.width, rotado.height)
    val tamanoRecorte = (ladoMin / escala).toInt().coerceIn(1, ladoMin)

    // 3. Calcular coordenadas teniendo en cuenta el pan (offset)
    val centroX = rotado.width / 2f - (offsetX / 300f) * rotado.width
    val centroY = rotado.height / 2f - (offsetY / 300f) * rotado.height

    val x = (centroX - tamanoRecorte / 2f).toInt().coerceIn(0, rotado.width - tamanoRecorte)
    val y = (centroY - tamanoRecorte / 2f).toInt().coerceIn(0, rotado.height - tamanoRecorte)

    // 4. Extraer el recorte centrado
    val recortado = Bitmap.createBitmap(rotado, x, y, tamanoRecorte, tamanoRecorte)

    // 5. Escalar a resolución estándar 512x512
    val finalBitmap = Bitmap.createScaledBitmap(recortado, 512, 512, true)

    val outputStream = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    return outputStream.toByteArray()
}

package com.renea.arete_social.gui.auth.perfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.roundToInt

// Proporción del recuadro de ajuste (ancho / alto). La portada del perfil es ancha, no cuadrada.
private const val ASPECTO_PORTADA = 3f / 2f
private const val ANCHO_SALIDA_MAX_PX = 1200
private const val LADO_MAX_ENTRADA_PX = 2400
private const val ZOOM_MAX = 5f

/**
 * Ajuste básico de la portada del perfil: arrastrar para encuadrar, pellizcar o usar el control
 * para acercar, girar 90° y volver a centrar. Al aplicar entrega la imagen ya recortada (JPEG) con
 * exactamente lo que se ve en el recuadro.
 */
@Composable
fun EditorPortadaDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onPortadaLista: (bytes: ByteArray, extension: String) -> Unit
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var falloCarga by remember { mutableStateOf(false) }
    var escala by remember { mutableFloatStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }
    var visor by remember { mutableStateOf(IntSize.Zero) }
    var procesando by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val cargado = withContext(Dispatchers.IO) { runCatching { cargarBitmapAcotado(contexto, uri) }.getOrNull() }
        if (cargado != null) bitmap = cargado else falloCarga = true
    }

    // Mantiene la imagen cubriendo siempre todo el recuadro (sin bordes vacíos al arrastrar).
    fun limitar(deseado: Offset, zoom: Float): Offset {
        val bmp = bitmap ?: return deseado
        if (visor.width == 0 || visor.height == 0) return deseado
        val base = max(visor.width.toFloat() / bmp.width, visor.height.toFloat() / bmp.height)
        val real = base * zoom
        val maxX = max(0f, (bmp.width * real - visor.width) / 2f)
        val maxY = max(0f, (bmp.height * real - visor.height) / 2f)
        return Offset(deseado.x.coerceIn(-maxX, maxX), deseado.y.coerceIn(-maxY, maxY))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AJUSTAR PORTADA",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Arrastra para encuadrar • Pellizca o usa el control para acercar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Recuadro de ajuste: lo que se ve aquí es lo que se guarda.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ASPECTO_PORTADA)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .onSizeChanged { visor = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                escala = (escala * zoom).coerceIn(1f, ZOOM_MAX)
                                desplazamiento = limitar(desplazamiento + pan, escala)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val actual = bitmap
                    when {
                        actual != null -> Image(
                            bitmap = actual.asImageBitmap(),
                            contentDescription = "Portada en edición",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = escala
                                    scaleY = escala
                                    translationX = desplazamiento.x
                                    translationY = desplazamiento.y
                                }
                        )
                        falloCarga -> Text(
                            "No se pudo abrir la imagen.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                        else -> Text(
                            "Cargando…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    // Guías de regla de tercios.
                    val guia = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (i in 1..2) {
                            val x = size.width * i / 3f
                            val y = size.height * i / 3f
                            drawLine(guia, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            drawLine(guia, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ZOOM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Slider(
                            value = escala,
                            onValueChange = {
                                escala = it
                                desplazamiento = limitar(desplazamiento, it)
                            },
                            valueRange = 1f..ZOOM_MAX,
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadarOutlinedButton(
                            texto = "🔄 Girar 90°",
                            habilitado = bitmap != null && !procesando,
                            onClick = {
                                bitmap?.let { original ->
                                    bitmap = girar90(original)
                                    escala = 1f
                                    desplazamiento = Offset.Zero
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RadarOutlinedButton(
                            texto = "🎯 Re-centrar",
                            onClick = {
                                escala = 1f
                                desplazamiento = Offset.Zero
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadarOutlinedButton(
                        texto = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    RadarButton(
                        texto = if (procesando) "GUARDANDO..." else "APLICAR PORTADA",
                        habilitado = !procesando && bitmap != null && visor.width > 0,
                        onClick = {
                            val origen = bitmap ?: return@RadarButton
                            val tamanoVisor = visor
                            val zoom = escala
                            val pan = desplazamiento
                            procesando = true
                            scope.launch(Dispatchers.IO) {
                                val bytes = runCatching { generarPortada(origen, tamanoVisor, zoom, pan) }.getOrNull()
                                withContext(Dispatchers.Main) {
                                    procesando = false
                                    if (bytes != null) onPortadaLista(bytes, "jpg") else falloCarga = true
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

/** Abre la imagen limitando su tamaño (evita agotar la memoria con fotos muy grandes) y respetando su orientación. */
private fun cargarBitmapAcotado(contexto: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val mayor = max(info.size.width, info.size.height)
            if (mayor > LADO_MAX_ENTRADA_PX) {
                val factor = LADO_MAX_ENTRADA_PX.toFloat() / mayor
                decoder.setTargetSize(
                    (info.size.width * factor).roundToInt().coerceAtLeast(1),
                    (info.size.height * factor).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var muestreo = 1
    while (max(limites.outWidth, limites.outHeight) / muestreo > LADO_MAX_ENTRADA_PX) muestreo *= 2
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
    return contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
}

private fun girar90(origen: Bitmap): Bitmap {
    val matriz = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(origen, 0, 0, origen.width, origen.height, matriz, true)
}

/**
 * Recorta del bitmap exactamente la zona visible en el recuadro. La imagen se muestra con
 * "cubrir" (escala base) más el zoom del usuario, centrada y desplazada [desplazamiento] píxeles.
 */
private fun generarPortada(origen: Bitmap, visor: IntSize, escala: Float, desplazamiento: Offset): ByteArray {
    val base = max(visor.width.toFloat() / origen.width, visor.height.toFloat() / origen.height)
    val real = base * escala

    val anchoVisible = min(visor.width / real, origen.width.toFloat())
    val altoVisible = min(visor.height / real, origen.height.toFloat())
    val centroX = origen.width / 2f - desplazamiento.x / real
    val centroY = origen.height / 2f - desplazamiento.y / real

    val x = (centroX - anchoVisible / 2f).coerceIn(0f, max(0f, origen.width - anchoVisible)).roundToInt()
    val y = (centroY - altoVisible / 2f).coerceIn(0f, max(0f, origen.height - altoVisible)).roundToInt()
    val w = anchoVisible.roundToInt().coerceIn(1, origen.width - x)
    val h = altoVisible.roundToInt().coerceIn(1, origen.height - y)

    val recorte = Bitmap.createBitmap(origen, x, y, w, h)

    // Sin agrandar: si el recorte es más chico que el máximo, se conserva su tamaño.
    val anchoFinal = min(ANCHO_SALIDA_MAX_PX, recorte.width)
    val altoFinal = (anchoFinal * visor.height.toFloat() / visor.width).roundToInt().coerceAtLeast(1)
    val final = Bitmap.createScaledBitmap(recorte, anchoFinal, altoFinal, true)

    return ByteArrayOutputStream().use { salida ->
        final.compress(Bitmap.CompressFormat.JPEG, 85, salida)
        salida.toByteArray()
    }
}

package com.renea.arete_social.feature.miausnake.ui.challenge

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR de retos. Generación LOCAL con ZXing (solo `core`, Apache-2.0, 100% software libre).
 * No depende de Google Play Services ni librerías propietarias.
 */
object QrSupport {
    /** Por encima de esto el QR sale demasiado denso para leerse bien: se ofrece texto o archivo. */
    const val MAX_CHARS = 1_100

    fun fits(text: String): Boolean = text.length <= MAX_CHARS

    /** Matriz de módulos (true = oscuro), o null si no cabe en un QR. */
    fun modules(text: String): Array<BooleanArray>? = runCatching {
        val hints: Map<EncodeHintType, Any> = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
    }.getOrNull()
}

/** QR negro sobre blanco con zona de silencio de 4 módulos (los lectores lo necesitan; no se tematiza). */
@Composable
fun QrImage(text: String, description: String, modifier: Modifier = Modifier) {
    val modules = remember(text) { QrSupport.modules(text) } ?: return
    val n = modules.size
    Canvas(
        modifier
            .aspectRatio(1f)
            .background(Color.White)
            .semantics { contentDescription = description }
    ) {
        val unit = size.width / (n + 8)
        for (y in 0 until n) for (x in 0 until n) {
            if (modules[y][x]) {
                drawRect(Color.Black, Offset((x + 4) * unit, (y + 4) * unit), Size(unit + 0.5f, unit + 0.5f))
            }
        }
    }
}

/**
 * Intenta abrir un lector de QR libre mediante intent del sistema (ZXing, Binary Eye, etc.).
 * Si no está disponible, guía al usuario para pegar el texto o abrir un archivo.
 */
fun scanQr(context: Context, onText: (String?) -> Unit, onError: (String) -> Unit) {
    val intent = Intent("com.google.zxing.client.android.SCAN").apply {
        putExtra("SCAN_MODE", "QR_CODE_MODE")
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            onError("Para importar el reto, pega el texto en el campo o abre el archivo correspondiente.")
        }
    }.onFailure {
        onError("Pega el texto del reto en el campo o abre el archivo correspondiente.")
    }
}

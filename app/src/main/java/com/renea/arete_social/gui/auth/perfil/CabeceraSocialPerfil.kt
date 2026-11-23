package com.renea.arete_social.gui.auth.perfil

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.Usuario

@Composable
internal fun TarjetaInteresesPerfil(usuario: Usuario) {
    val intereses = listOf(
        "♡" to ("Hobby" to usuario.hobby),
        "↗" to ("Deporte" to usuario.deporte),
        "♫" to ("Ritmo musical" to usuario.ritmoMusical),
        "▹" to ("Serie favorita" to usuario.serieFavorita),
        "▤" to ("Libro favorito" to usuario.libroFavorito),
        "✧" to ("Signo" to usuario.signoZodiacal)
    ).filter { !it.second.second.isNullOrBlank() }

    if (intereses.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 0.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Text("✦", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Un poco sobre mí", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            val filas = intereses.chunked(2)
            filas.forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { (icono, dato) ->
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(icono, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                Text(dato.first, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                            Text(dato.second.orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        }
                    }
                    if (fila.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Cabecera del perfil: portada a ancho completo (sin desplazamientos manuales),
 * botones de acción rectangulares sólidos y pestañas centradas que se solapan
 * sobre el borde inferior de la portada.
 */
@Composable
internal fun CabeceraSocialPerfil(
    fotoUrl: String?, nombre: String, descripcion: String,
    hobby: String?, deporte: String?, ritmoMusical: String?, serieFavorita: String?, libroFavorito: String?, signoZodiacal: String?,
    subiendoFoto: Boolean, onSeleccionarImagen: (Uri) -> Unit,
    portadaUrl: String?, subiendoPortada: Boolean,
    onSeleccionarPortada: (Uri) -> Unit, onQuitarPortada: () -> Unit,
    onAjustesClick: () -> Unit,
    pestanaActiva: Int, onPestanaCambia: (Int) -> Unit,
    numNotas: Int, numFotos: Int, numAmigos: Int, numSolicitudes: Int, numMensajes: Int,
    numCartasSinLeer: Int, numObras: Int
) {
    val selectorImagen = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onSeleccionarImagen(uri) }
    val selectorPortada = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onSeleccionarPortada(uri) }
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val gustos = listOf("Hobby" to hobby, "Deporte" to deporte, "Música" to ritmoMusical, "Serie" to serieFavorita, "Libro" to libroFavorito, "Signo" to signoZodiacal).filter { !it.second.isNullOrBlank() }

    val alturaPortada = AlturaPortadaPerfil
    val solapePestanas = SolapePestanasPerfil
    val alturaPestanas = AlturaPestanasPerfil
    val formaPortada = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

    val modeloPortada: Any? = remember(portadaUrl) {
        if (portadaUrl?.startsWith("data:image/") == true) {
            runCatching {
                val base64Data = portadaUrl.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull() ?: portadaUrl
        } else {
            portadaUrl
        }
    }

    val modeloFoto: Any? = remember(fotoUrl) {
        if (fotoUrl?.startsWith("data:image/") == true) {
            runCatching {
                val base64Data = fotoUrl.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull() ?: fotoUrl
        } else {
            fotoUrl
        }
    }

    // Alto total = portada + parte de las pestañas que queda por debajo de ella.
    Box(Modifier.fillMaxWidth().height(alturaPortada + alturaPestanas - solapePestanas)) {

        // ── Portada a todo el ancho ─────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(alturaPortada).clip(formaPortada)) {
            if (!portadaUrl.isNullOrBlank()) {
                AsyncImage(
                    model = modeloPortada,
                    contentDescription = "Imagen de portada",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Black.copy(alpha = .72f)))))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .78f), Color(0xFF17171B)))))
                Canvas(Modifier.fillMaxSize()) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, size.height * .30f)
                        cubicTo(size.width * .24f, size.height * .02f, size.width * .28f, size.height * .55f, size.width * .52f, size.height * .28f)
                        cubicTo(size.width * .72f, size.height * .02f, size.width * .82f, size.height * .55f, size.width, size.height * .26f)
                        lineTo(size.width, 0f); lineTo(0f, 0f); close()
                    }
                    drawPath(path, Color.White.copy(alpha = .10f))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .50f)))))
            }
        }

        // ── Acciones superiores (simétricas respecto al borde derecho) ──────
        Row(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!portadaUrl.isNullOrBlank() && !subiendoPortada) PerfilIconButton("✕", "Quitar portada", onQuitarPortada)
            PerfilIconButton(if (subiendoPortada) "…" else "▧", "Cambiar imagen de portada") {
                if (!subiendoPortada) selectorPortada.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            PerfilIconButton("⚙", "Ajustes", onAjustesClick)
        }

        // ── Avatar + datos, centrados en la zona libre de la portada ────────
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = solapePestanas + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(78.dp), contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(74.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
                        .border(2.5.dp, Color.White, CircleShape)
                        .clickable(enabled = !subiendoFoto) { selectorImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (!fotoUrl.isNullOrBlank()) AsyncImage(model = modeloFoto, contentDescription = "Foto de perfil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    else Text(nombre.firstOrNull()?.uppercase() ?: "U", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    if (subiendoFoto) CircularProgressIndicator(Modifier.size(24.dp), color = accent, strokeWidth = 2.dp)
                }
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(accent)
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .clickable(enabled = !subiendoFoto) { selectorImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nombre.ifBlank { "Usuario" }, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B31)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("Invitado", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (descripcion.isNotBlank()) Text(descripcion, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2)
                if (gustos.isNotEmpty()) {
                    Text(gustos.joinToString("  ·  ") { "${it.first}: ${it.second}" }, color = Color.White.copy(alpha = .92f), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 3)
                }
            }
        }

        // ── Pestañas: centradas, solapadas sobre el borde de la portada ─────
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(alturaPestanas).padding(horizontal = 20.dp)
        ) {
            BarraPestanasPerfil(pestanaActiva, onPestanaCambia, numNotas + numFotos, numAmigos, numSolicitudes, numMensajes, numCartasSinLeer, numObras)
        }
    }
}

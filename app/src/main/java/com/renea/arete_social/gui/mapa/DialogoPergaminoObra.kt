package com.renea.arete_social.gui.mapa

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.gui.theme.BotonPildora
import com.renea.arete_social.gui.theme.TipografiaLectura

private val ColorFondoPergamino = Color(0xFFFAF4E6)
private val ColorBordePergamino = Color(0xFFD8C7A5)
private val ColorTextoTituloPergamino = Color(0xFF332014)
private val ColorTextoCuerpoPergamino = Color(0xFF453022)
private val ColorTextoSecundarioPergamino = Color(0xFF7A6453)

/**
 * Ficha elegante estilo pergamino para leer cuentos o poemas compartidos en el mapa.
 */
@Composable
fun DialogoPergaminoObra(
    obra: ObraLibreria,
    onCerrar: () -> Unit,
    usuarioIdActual: String? = null,
    onResponder: ((autorId: String, autorNombre: String) -> Unit)? = null,
    onAbrirPdf: ((ObraLibreria, alObtener: (String) -> Unit) -> Unit)? = null
) {
    val contexto = LocalContext.current
    val forma = RoundedCornerShape(26.dp)

    fun abrirExterno(url: String) {
        val trimmed = url.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase()
        // SEC-05: Validar que el scheme sea estrictamente web seguro (http o https) para evitar inyecciones
        if (scheme == "http" || scheme == "https") {
            runCatching {
                contexto.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp),
            shape = forma,
            color = ColorFondoPergamino,
            tonalElevation = 6.dp,
            shadowElevation = 20.dp,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ColorBordePergamino)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cabecera pergamino: Tipo y botón cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📖", fontSize = 16.sp)
                        Text(
                            text = obra.tipo.etiqueta.uppercase(),
                            color = ColorTextoSecundarioPergamino,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ColorBordePergamino.copy(alpha = 0.35f))
                            .clickable(onClick = onCerrar),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = ColorTextoTituloPergamino, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Título y Autor
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = obra.titulo,
                        style = TipografiaLectura.titulo.copy(
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            color = ColorTextoTituloPergamino
                        )
                    )
                    val nombreAutor = obra.autorNombre?.ifBlank { null } ?: "Alguien cercano"
                    Text(
                        text = "Escrito por $nombreAutor",
                        color = ColorTextoSecundarioPergamino,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Contenido estilo libro / pergamino
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp, max = 340.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF3ECE0).copy(alpha = 0.5f))
                        .border(0.6.dp, ColorBordePergamino.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (obra.contenido.isNotBlank()) {
                        Text(
                            text = obra.contenido,
                            textAlign = TextAlign.Justify,
                            style = TipografiaLectura.cuerpo.copy(
                                color = ColorTextoCuerpoPergamino,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        )
                    } else {
                        Text(
                            text = "Esta obra se lee a través de su PDF adjunto.",
                            style = TipografiaLectura.firma.copy(color = ColorTextoSecundarioPergamino),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }

                // Enlace discreto
                obra.enlace?.let { url ->
                    Text(
                        text = "↗ para acompañar la lectura",
                        color = ColorTextoSecundarioPergamino,
                        fontSize = 11.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { abrirExterno(url) }
                    )
                }

                // Si tiene PDF
                if (obra.tienePdf && onAbrirPdf != null) {
                    BotonPildora(
                        texto = "Abrir PDF${obra.pdfNombre?.let { " · $it" } ?: ""}",
                        onClick = { onAbrirPdf(obra, ::abrirExterno) },
                        destacado = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Acciones inferiores: Responder y Cerrar
                val esPropia = usuarioIdActual != null && obra.autorId == usuarioIdActual
                val puedeResponder = onResponder != null && !esPropia
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (puedeResponder) {
                        val nombreParaChat = obra.autorNombre?.ifBlank { null } ?: "Autor"
                        BotonPildora(
                            texto = "✉  Responder",
                            onClick = {
                                onCerrar()
                                onResponder(obra.autorId, nombreParaChat)
                            },
                            destacado = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    BotonPildora(
                        texto = "Cerrar",
                        onClick = onCerrar,
                        destacado = !puedeResponder,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

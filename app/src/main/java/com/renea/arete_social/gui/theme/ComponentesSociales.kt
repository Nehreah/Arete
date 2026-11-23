package com.renea.arete_social.gui.theme

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Selector tipo píldora para sub-pestañas dentro de una sección del perfil. */
@Composable
fun SelectorPildoras(
    opciones: List<String>,
    seleccion: Int,
    onSeleccion: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        opciones.forEachIndexed { index, titulo ->
            val activa = seleccion == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (activa) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSeleccion(index) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = titulo,
                    color = if (activa) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Estado vacío con el mismo aspecto en todas las secciones sociales. */
@Composable
fun EstadoVacioSocial(icono: String, titulo: String, subtitulo: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icono, fontSize = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text(titulo, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Botón de acción en forma de píldora; [destacado] = relleno, si no = contorno. */
@Composable
fun BotonPildora(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    destacado: Boolean = true
) {
    val colores = MaterialTheme.colorScheme
    val fondo = when {
        !habilitado -> colores.surfaceVariant
        destacado -> colores.primary
        else -> colores.surface
    }
    val contenido = when {
        !habilitado -> colores.onSurfaceVariant
        destacado -> colores.onPrimary
        else -> colores.primary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(fondo)
            .border(1.dp, if (destacado && habilitado) fondo else colores.outlineVariant, RoundedCornerShape(22.dp))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = contenido, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private val FORMATO_FECHA = ThreadLocal.withInitial { SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es")) }

fun formatearFechaCorta(epochMs: Long): String = FORMATO_FECHA.get()!!.format(Date(epochMs))


/**
 * Avatar circular reutilizable: foto si existe, si no la inicial del nombre.
 * Con [onClick] se vuelve tocable (se usa para abrir el perfil de la persona desde chats,
 * listas y comunidades). Un único componente garantiza que la persona se vea igual en toda la app.
 */
@Composable
fun AvatarUsuario(
    foto: String?,
    nombre: String,
    modifier: Modifier = Modifier,
    tamano: Dp = 36.dp,
    onClick: (() -> Unit)? = null
) {
    val colores = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(colores.secondary)
            .border(1.dp, colores.primary.copy(alpha = 0.45f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!foto.isNullOrBlank()) {
            AsyncImage(
                model = foto,
                contentDescription = "Foto de $nombre",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(
                text = nombre.trim().firstOrNull()?.uppercase() ?: "?",
                color = colores.primary,
                fontSize = (tamano.value * 0.42f).sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

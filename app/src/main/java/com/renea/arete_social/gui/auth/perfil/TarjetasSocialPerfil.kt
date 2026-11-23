package com.renea.arete_social.gui.auth.perfil

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.SolicitudAmistad
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.feature.auth.MensajeDirectoItem

/**
 * Tarjeta de un amigo en la lista del perfil.
 */
@Composable
internal fun TarjetaAmigoSocial(
    amigo: Usuario,
    onAbrirChat: () -> Unit,
    onIniciarVideollamada: () -> Unit,
    onVerPerfil: () -> Unit,
    onEliminar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { onVerPerfil() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar del amigo
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!amigo.foto.isNullOrBlank()) {
                AsyncImage(
                    model = amigo.foto,
                    contentDescription = amigo.nombre,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = amigo.nombre.firstOrNull()?.uppercase() ?: "A",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Nombre y estado
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = amigo.nombre,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = amigo.descripcion.ifBlank { "Amigo en Aréte" }.take(30),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        // Botones de acción rápida
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Chat
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onAbrirChat() },
                contentAlignment = Alignment.Center
            ) {
                Text("💬", fontSize = 13.sp)
            }

            // Videollamada
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onIniciarVideollamada() },
                contentAlignment = Alignment.Center
            ) {
                Text("📹", fontSize = 13.sp)
            }

            // Eliminar amigo
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), CircleShape)
                    .clickable { onEliminar() },
                contentAlignment = Alignment.Center
            ) {
                Text("🗑️", fontSize = 12.sp)
            }
        }
    }
}

/**
 * Tarjeta de una solicitud de amistad pendiente.
 */
@Composable
internal fun TarjetaSolicitudAmistad(
    solicitud: SolicitudAmistad,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!solicitud.remitenteFoto.isNullOrBlank()) {
                AsyncImage(
                    model = solicitud.remitenteFoto,
                    contentDescription = solicitud.remitenteNombre,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = solicitud.remitenteNombre.firstOrNull()?.uppercase() ?: "S",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = solicitud.remitenteNombre,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Quiere ser tu amigo",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .clickable { onAceptar() }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("✓ ACEPTAR", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable { onRechazar() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * Tarjeta de un mensaje directo recibido en el perfil propio.
 */
@Composable
internal fun TarjetaMensajeRecibido(
    item: MensajeDirectoItem,
    onAbrirChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable { onAbrirChat() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!item.remitenteFoto.isNullOrBlank()) {
                AsyncImage(
                    model = item.remitenteFoto,
                    contentDescription = item.remitenteNombre,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = item.remitenteNombre.firstOrNull()?.uppercase() ?: "U",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.remitenteNombre,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.ultimoMensaje,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("💬 ABRIR", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

package com.renea.arete_social.gui.mensajeria

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.feature.auth.MensajeDirectoItem
import com.renea.arete_social.feature.mensajeria.InterlocutorUi
import com.renea.arete_social.gui.theme.AvatarUsuario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BarraSuperiorChat(
    titulo: String = "Chat",
    mostrarBotonSalas: Boolean = true,
    onAtras: () -> Unit,
    onAccionDerecha: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onAtras() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💬", fontSize = 14.sp)
                }
                Text(
                    text = titulo,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (mostrarBotonSalas && onAccionDerecha != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                    .clickable { onAccionDerecha() },
                contentAlignment = Alignment.Center
            ) {
                Text("⋮", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Selector segmentado de chats personales; las salas ahora están en Parche.
 */
@Composable
internal fun BarraPestanasChats(
    pestanaActiva: Int,
    onPestanaCambia: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemPestanaChat(
            icono = "💬",
            etiqueta = "Activos",
            activo = pestanaActiva == 0,
            onClick = { onPestanaCambia(0) },
            modifier = Modifier.weight(1f)
        )
        ItemPestanaChat(
            icono = "👥",
            etiqueta = "Amigos",
            activo = pestanaActiva == 1,
            onClick = { onPestanaCambia(1) },
            modifier = Modifier.weight(1f)
        )
        ItemPestanaChat(
            icono = "📍",
            etiqueta = "Cercanos",
            activo = pestanaActiva == 2,
            onClick = { onPestanaCambia(2) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun ItemPestanaChat(
    icono: String,
    etiqueta: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (activo) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                1.dp,
                if (activo) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = icono,
                fontSize = 13.sp
            )
            Text(
                text = etiqueta,
                color = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Cabecera de un chat 1-a-1: foto + nombre de la otra persona.
 */
@Composable
internal fun CabeceraInterlocutor(
    interlocutor: InterlocutorUi,
    esE2ee: Boolean,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarUsuario(foto = interlocutor.foto, nombre = interlocutor.nombre, tamano = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = interlocutor.nombre,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (esE2ee) Color(0xFF1E3A2F) else Color(0xFF3A2B1E))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (esE2ee) "🔒 E2EE" else "⚠️ Sin clave",
                        color = if (esE2ee) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (onClick != null) {
                Text(
                    text = "Ver perfil",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (onClick != null) {
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Item de conversación activa (Buzón) con avatar, punto verde online, preview y hora.
 */
@Composable
internal fun ItemChatActivo(
    item: MensajeDirectoItem,
    estaCercano: Boolean = false,
    onClick: () -> Unit,
    onClickAvatar: (() -> Unit)? = null,
    onEliminar: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            AvatarUsuario(
                foto = item.remitenteFoto,
                nombre = item.remitenteNombre,
                tamano = 48.dp,
                onClick = onClickAvatar
            )

            if (estaCercano) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.remitenteNombre,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.timestamp > 4_000_000_000_000L) "" else formatearHora(item.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }

            Text(
                text = item.ultimoMensaje,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (onEliminar != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onEliminar() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗑️",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "›",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Item de persona cercana detectada en el radar con acciones rápidas de chat y llamada.
 */
@Composable
internal fun ItemPersonaCercana(
    dispositivo: Dispositivo,
    onChatClick: () -> Unit,
    onLlamadaClick: () -> Unit,
    onPerfilClick: (() -> Unit)? = null
) {
    val listo = dispositivo.usuarioId != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (listo) Modifier.clickable(onClick = onChatClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (listo) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .then(if (onPerfilClick != null) Modifier.clickable(onClick = onPerfilClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dispositivo.nombre.firstOrNull()?.uppercase() ?: "P",
                    color = if (listo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(
                        if (listo) MaterialTheme.colorScheme.primary
                        else Color(0xFFFFC107)
                    )
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = dispositivo.nombre,
                color = if (listo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val distanciaTexto = if (!listo) {
                "🔄 Sincronizando perfil..."
            } else {
                dispositivo.distanciaMetros?.let { "A ${it.toInt()}m • En rango de radar" } ?: "En rango de radar"
            }
            Text(
                text = distanciaTexto,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (listo) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                    )
                    .border(
                        1.dp,
                        if (listo) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .then(if (listo) Modifier.clickable(onClick = onChatClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(if (listo) "💬" else "⏳", fontSize = 13.sp)
            }

            if (listo) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                        .clickable(onClick = onLlamadaClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📹", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Item de sala de chat pública o protegida según Chats.jpeg.
 */
@Composable
internal fun ItemSalaChat(
    nombre: String,
    descripcion: String,
    esProtegida: Boolean,
    onClick: () -> Unit,
    icono: String? = null,
    etiqueta: String? = null,
    destacada: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    if (destacada) 2.dp else 1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = if (destacada) 0.9f else 0.35f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icono ?: if (esProtegida) "🔒" else "📡",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = nombre,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = descripcion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (etiqueta != null) {
                Text(
                    text = etiqueta,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "›",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ItemAmigoChat(
    usuario: Usuario,
    onClickChat: () -> Unit,
    onClickPerfil: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClickPerfil)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!usuario.foto.isNullOrBlank()) {
                    AsyncImage(
                        model = usuario.foto,
                        contentDescription = usuario.nombre,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = usuario.nombre.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = usuario.nombre,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = usuario.descripcion.ifBlank { "Amigo guardado" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClickChat)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "💬 MENSAJE",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

internal fun formatearHora(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

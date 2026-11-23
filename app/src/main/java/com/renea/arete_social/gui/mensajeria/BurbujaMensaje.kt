package com.renea.arete_social.gui.mensajeria

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.mensajeria.MensajeUi
import com.renea.arete_social.gui.theme.AvatarUsuario

@Composable
internal fun BurbujaMensaje(
    mensaje: MensajeUi,
    mostrarRemitente: Boolean,
    onVerPerfil: ((String) -> Unit)?,
    onVerComunidad: ((String) -> Unit)?,
    onVerEvento: ((String) -> Unit)?,
    onEliminarMensaje: ((MensajeUi) -> Unit)? = null
) {
    val texto = mensaje.contenido

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mensaje.esPropio) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Foto pequeña del remitente (solo mensajes ajenos). En mensajes consecutivos de la misma
        // persona se reserva el hueco para que las burbujas queden alineadas.
        if (!mensaje.esPropio) {
            if (mostrarRemitente) {
                AvatarUsuario(
                    foto = mensaje.remitenteFoto,
                    nombre = mensaje.remitenteNombre,
                    tamano = 32.dp,
                    onClick = onVerPerfil?.let { abrir -> { abrir(mensaje.remitenteId) } }
                )
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mensaje.esPropio) 16.dp else 4.dp,
                        bottomEnd = if (mensaje.esPropio) 4.dp else 16.dp
                    )
                )
                .then(
                    if (mensaje.esPropio && onEliminarMensaje != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onEliminarMensaje(mensaje) }
                        )
                    } else Modifier
                )
                .background(if (mensaje.esPropio) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (mensaje.esPropio) 0.4f else 0.15f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mensaje.esPropio) 16.dp else 4.dp,
                        bottomEnd = if (mensaje.esPropio) 4.dp else 16.dp
                    )
                )
                .padding(10.dp)
        ) {
            // Remitente interactivo (Click para abrir perfil y agregar a amigos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!mensaje.esPropio && mostrarRemitente) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onVerPerfil?.invoke(mensaje.remitenteId) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mensaje.remitenteNombre,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (mensaje.esPropio) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (mensaje.esE2ee) {
                        Text(
                            text = "🔒 E2EE",
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (mensaje.esPropio && onEliminarMensaje != null) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onEliminarMensaje(mensaje) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗑️", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Renderizado de tarjetas interactivas o texto normal
            when {
                texto.startsWith("[CARD_COMUNIDAD:") -> {
                    val partes = texto.removePrefix("[CARD_COMUNIDAD:").removeSuffix("]").split(":")
                    val comId = partes.getOrNull(0) ?: ""
                    val comNombre = partes.getOrNull(1) ?: "Comunidad"
                    val comTipo = partes.getOrNull(2) ?: "Pública"
                    val comDesc = partes.getOrNull(3) ?: ""

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏛️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(comNombre, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Comunidad • $comTipo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                            }
                        }
                        if (comDesc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(comDesc, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, maxLines = 2)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onVerComunidad?.invoke(comId) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏛️ VER COMUNIDAD / UNIRME", color = MaterialTheme.colorScheme.background, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                texto.startsWith("[CARD_EVENTO:") -> {
                    val partes = texto.removePrefix("[CARD_EVENTO:").removeSuffix("]").split(":")
                    val evId = partes.getOrNull(0) ?: ""
                    val evNombre = partes.getOrNull(1) ?: "Evento"
                    val evDesc = partes.getOrNull(3) ?: ""

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                            .border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(evNombre, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Evento en tu zona", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                            }
                        }
                        if (evDesc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(evDesc, color = MaterialTheme.colorScheme.onBackground, fontSize = 11.sp, maxLines = 2)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onVerEvento?.invoke(evId) }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📅 VER EVENTO / ASISTIR", color = MaterialTheme.colorScheme.background, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                texto.startsWith("[VOZ_PTT:") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎙️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nota de voz en tiempo real", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                else -> {
                    Text(mensaje.contenido, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                }
            }
        }
    }
}

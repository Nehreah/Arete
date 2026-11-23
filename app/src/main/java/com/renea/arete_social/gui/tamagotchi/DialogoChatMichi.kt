package com.renea.arete_social.gui.tamagotchi

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renea.arete_social.feature.tamagotchi.mensajes.MensajeMichiLocal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diálogo de chat propio de Michi con su dueño (solo lectura).
 * Michi deposita aquí todos sus avisos y mensajes históricos.
 */
@Composable
fun DialogoChatMichi(
    nombreMascota: String,
    mensajes: List<MensajeMichiLocal>,
    onCerrar: () -> Unit
) {
    val colores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = forma,
            color = colores.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colores.primary.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colores.primary.copy(alpha = 0.15f))
                                .border(1.dp, colores.primary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🐾", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "Notas de $nombreMascota",
                                color = colores.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Buzón de avisos de tu mascota",
                                color = colores.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = "✕",
                        color = colores.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onCerrar)
                            .padding(6.dp)
                    )
                }

                // Explicación de solo lectura
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colores.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ℹ $nombreMascota te deja aquí notas de lo que ve o siente. No puedes escribirle directamente, pero puedes cuidarla en su pantalla.",
                        color = colores.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                // Lista de mensajes de solo lectura
                if (mensajes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("💌", fontSize = 32.sp)
                            Text(
                                text = "Aún no hay notas de $nombreMascota",
                                color = colores.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Cuando detecte amigos, planes o alguien comparta o lea obras, aparecerán aquí.",
                                color = colores.onSurfaceVariant,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = false
                    ) {
                        items(mensajes, key = { it.id }) { msg ->
                            TarjetaBurbujaMensajeMichi(msg = msg)
                        }
                    }
                }

                // Botón de cerrar al pie
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCerrar) {
                        Text("Cerrar", color = colores.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatearHoraMichi(timestamp: Long): String = try {
    SimpleDateFormat("h:mm a · d MMM", Locale.getDefault()).format(Date(timestamp))
} catch (_: Exception) {
    ""
}

@Composable
private fun TarjetaBurbujaMensajeMichi(msg: MensajeMichiLocal) {
    val colores = MaterialTheme.colorScheme
    val horaTexto = formatearHoraMichi(msg.timestamp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(colores.primaryContainer.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(msg.tipo.icono, fontSize = 13.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(colores.surfaceVariant.copy(alpha = 0.75f))
                .border(0.6.dp, colores.outline.copy(alpha = 0.15f), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = msg.texto,
                color = colores.onSurface,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Text(
                text = horaTexto,
                color = colores.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

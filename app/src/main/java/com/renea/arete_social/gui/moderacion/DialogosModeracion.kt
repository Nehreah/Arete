package com.renea.arete_social.gui.moderacion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.model.MotivoReporte

/**
 * Diálogo modal para reportar contenido o usuarios inapropiados (normas de convivencia comunitaria).
 */
@Composable
fun DialogoReportar(
    titulo: String = "Reportar contenido o usuario",
    nombreObjetivo: String = "este usuario",
    mostrarOpcionBloquear: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmar: (motivo: MotivoReporte, descripcion: String, bloquearTambien: Boolean) -> Unit
) {
    var motivoSeleccionado by remember { mutableStateOf(MotivoReporte.CONTENIDO_INAPROPIADO) }
    var descripcion by remember { mutableStateOf("") }
    var bloquearTambien by remember { mutableStateOf(mostrarOpcionBloquear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🚩", fontSize = 20.sp)
                Text(
                    text = titulo,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "¿Por qué deseas reportar a $nombreObjetivo?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                // Lista de motivos
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(vertical = 4.dp)
                ) {
                    MotivoReporte.entries.forEach { motivo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { motivoSeleccionado = motivo }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (motivo == motivoSeleccionado),
                                onClick = { motivoSeleccionado = motivo },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = motivo.etiqueta,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = motivo.descripcion,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Campo opcional para detalles
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { if (it.length <= 500) descripcion = it },
                    label = { Text("Detalles adicionales (opcional)", fontSize = 12.sp) },
                    placeholder = { Text("Explica brevemente lo ocurrido...", fontSize = 12.sp) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Casilla de bloquear también
                if (mostrarOpcionBloquear) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bloquearTambien = !bloquearTambien }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = bloquearTambien,
                            onCheckedChange = { bloquearTambien = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Bloquear también a este usuario inmediatamente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Aviso de prioridad para abuso infantil / CSAM
                if (motivoSeleccionado == MotivoReporte.ABUSO_INFANTIL) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🚨 PRIORIDAD MÁXIMA - TOLERANCIA CERO A CSAM:\n" +
                                "Las denuncias de abuso o pornografía infantil se procesan de forma inmediata con remoción expedita, bloqueo irreversible de la cuenta y notificación a las autoridades pertinentes (NCMEC, Te Protejo y Policía Judicial).",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text(
                    text = "🔒 Tu reporte es confidencial. Nuestro equipo de moderación revisará la situación en un plazo máximo de 24 horas (prioridad inmediata en seguridad infantil).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 15.sp
                )

                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/Nehreah/Arete/blob/main/docs/POLITICA_PRIVACIDAD.md#seguridad-infantil")
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "🛡️ Ver Estándares de Seguridad Infantil",
                        fontSize = 11.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(motivoSeleccionado, descripcion, bloquearTambien) }
            ) {
                Text(
                    text = "Enviar reporte",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo de confirmación para bloquear a un usuario.
 */
@Composable
fun DialogoConfirmarBloqueo(
    nombre: String,
    onDismiss: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Bloquear a $nombre?") },
        text = {
            Text(
                text = "Al bloquear a esta persona:\n" +
                    "• No podrán enviarse mensajes directos.\n" +
                    "• No podrán verse mutuamente en el radar de cercanía ni en el mapa.\n" +
                    "• Se cancelarán las solicitudes y encuentros activos.\n\n" +
                    "Puedes revertir esta acción en cualquier momento desde su perfil.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(
                    text = "Bloquear",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo de confirmación para desbloquear a un usuario.
 */
@Composable
fun DialogoConfirmarDesbloqueo(
    nombre: String,
    onDismiss: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Desbloquear a $nombre?") },
        text = {
            Text(
                text = "Volverás a poder comunicarte con $nombre y compartir cercanía en el radar.",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(
                    text = "Desbloquear",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

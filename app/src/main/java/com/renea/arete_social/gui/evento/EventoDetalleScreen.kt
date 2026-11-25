package com.renea.arete_social.gui.evento

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.ParticipanteEvento
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.feature.eventos.EventoDetalleUiState
import com.renea.arete_social.feature.eventos.EventoDetalleViewModel
import com.renea.arete_social.gui.theme.RadarBody
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarErrorScreen
import com.renea.arete_social.gui.theme.RadarLabel
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarLoadingScreen
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarScreenColumn
import com.renea.arete_social.gui.theme.RadarSectionTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventoDetalleScreen(
    viewModel: EventoDetalleViewModel,
    onAbrirChat: (String) -> Unit,
    onVerEnMapa: ((Double, Double) -> Unit)? = null,
    onAtras: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val estado by viewModel.estado.collectAsState()
    var refrescando by remember { mutableStateOf(false) }
    if (estado !is EventoDetalleUiState.Cargando) refrescando = false

    var mostrarDialogoEditar by remember { mutableStateOf(false) }
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }
    var participanteParaGestionar by remember { mutableStateOf<ParticipanteEvento?>(null) }
    var menuGestionAbierto by remember { mutableStateOf(false) }

    val selectorNuevaPortada = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) {
                val actual = estado
                if (actual is EventoDetalleUiState.Listo) {
                    viewModel.actualizar(
                        nombre = actual.evento.nombre,
                        descripcion = actual.evento.descripcion,
                        fecha = actual.evento.fecha,
                        ubicacion = actual.evento.ubicacion,
                        visibilidad = actual.evento.visibilidad,
                        portadaBytes = bytes
                    )
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.recargar() },
        modifier = modifier
    ) {
        when (val actual = estado) {
            is EventoDetalleUiState.Cargando -> RadarLoadingScreen()

            is EventoDetalleUiState.Error -> RadarErrorScreen(mensaje = actual.mensaje)

            is EventoDetalleUiState.Listo -> {
                val yaUnido = actual.evento.participantesIds.contains(viewModel.usuarioId)

                RadarScreenColumn {
                    // Portada del evento
                    if (!actual.evento.fotoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(
                                    if (actual.puedeGestionar) {
                                        Modifier.clickable {
                                            selectorNuevaPortada.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    } else Modifier
                                )
                        ) {
                            AsyncImage(
                                model = actual.evento.fotoUrl,
                                contentDescription = "Portada del evento",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (actual.puedeGestionar) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("📷 Cambiar foto", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else if (actual.puedeGestionar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .clickable {
                                    selectorNuevaPortada.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📷", fontSize = 20.sp)
                                Text("Agregar foto de portada al evento", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Título y Menú de gestión
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadarSectionTitle(actual.evento.nombre)

                        if (actual.puedeGestionar) {
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { menuGestionAbierto = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⋮", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }

                                DropdownMenu(
                                    expanded = menuGestionAbierto,
                                    onDismissRequest = { menuGestionAbierto = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("✏️ Editar información") },
                                        onClick = {
                                            menuGestionAbierto = false
                                            mostrarDialogoEditar = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🖼️ Cambiar foto de portada") },
                                        onClick = {
                                            menuGestionAbierto = false
                                            selectorNuevaPortada.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                    if (!actual.evento.fotoUrl.isNullOrBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Quitar foto de portada") },
                                            onClick = {
                                                menuGestionAbierto = false
                                                viewModel.actualizar(
                                                    nombre = actual.evento.nombre,
                                                    descripcion = actual.evento.descripcion,
                                                    fecha = actual.evento.fecha,
                                                    ubicacion = actual.evento.ubicacion,
                                                    visibilidad = actual.evento.visibilidad,
                                                    portadaBytes = null,
                                                    onExito = {
                                                        // Limpiar foto url pasándole un valor nulo
                                                    }
                                                )
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("🗑️ Eliminar evento", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuGestionAbierto = false
                                            mostrarDialogoEliminar = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (actual.evento.descripcion.isNotBlank()) {
                        RadarBody(actual.evento.descripcion)
                    }

                    RadarLabel("🕓 ${formatearFechaHora(actual.evento.fecha)}")
                    RadarLabel(
                        when (VisibilidadEvento.efectiva(actual.evento.comunidadId, actual.evento.visibilidad)) {
                            VisibilidadEvento.PUBLICO -> "🌐 Evento público"
                            VisibilidadEvento.AMIGOS -> "🔒 Evento privado · solo amistades"
                            VisibilidadEvento.COMUNIDAD -> "👥 Evento de comunidad · solo miembros"
                        }
                    )
                    RadarLabel("👥 ${actual.evento.participantesIds.size} participantes")

                    // Botones de acción principales
                    if (actual.uniendose) {
                        RadarLoading(modifier = Modifier.height(60.dp))
                    } else if (yaUnido) {
                        RadarOutlinedButton(
                            texto = "Salir del evento",
                            onClick = { viewModel.salir() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        RadarButton(
                            texto = "Unirme al evento",
                            onClick = { viewModel.unirse() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    RadarButton(
                        texto = "Abrir chat del evento",
                        onClick = { onAbrirChat(actual.evento.chatId) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (onVerEnMapa != null) {
                        RadarOutlinedButton(
                            texto = "📍 Ver lugar en el mapa",
                            onClick = { onVerEnMapa(actual.evento.ubicacion.latitud, actual.evento.ubicacion.longitud) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Lista de participantes con roles
                    if (actual.participantesConRoles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "PARTICIPANTES Y ROLES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )

                        actual.participantesConRoles.forEach { part ->
                            val esCreador = part.usuarioId == actual.evento.creadorId
                            val rolEtiqueta = when {
                                esCreador -> "Organizador"
                                part.rol == "ADMIN" -> "Admin delegado"
                                else -> "Asistente"
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .clickable(enabled = actual.puedeGestionar && !esCreador && part.usuarioId != viewModel.usuarioId) {
                                        participanteParaGestionar = part
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!part.fotoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = part.fotoUrl,
                                                    contentDescription = part.nombre,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(text = "👤", fontSize = 14.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(text = part.nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when (rolEtiqueta) {
                                                        "Organizador" -> Color(0xFF2E2207)
                                                        "Admin delegado" -> Color(0xFF132B1E)
                                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = rolEtiqueta,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = when (rolEtiqueta) {
                                                    "Organizador" -> Color(0xFFFFD54F)
                                                    "Admin delegado" -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }

                                        if (actual.puedeGestionar && !esCreador && part.usuarioId != viewModel.usuarioId) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("⚙️", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Diálogo: Editar Evento
                if (mostrarDialogoEditar) {
                    var editNombre by remember { mutableStateOf(actual.evento.nombre) }
                    var editDescripcion by remember { mutableStateOf(actual.evento.descripcion) }

                    AlertDialog(
                        onDismissRequest = { mostrarDialogoEditar = false },
                        title = { Text("Editar evento", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = editNombre,
                                    onValueChange = { editNombre = it },
                                    label = { Text("Nombre del evento") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editDescripcion,
                                    onValueChange = { editDescripcion = it },
                                    label = { Text("Descripción") },
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (editNombre.isNotBlank()) {
                                        viewModel.actualizar(
                                            nombre = editNombre.trim(),
                                            descripcion = editDescripcion.trim(),
                                            fecha = actual.evento.fecha,
                                            ubicacion = actual.evento.ubicacion,
                                            visibilidad = actual.evento.visibilidad,
                                            portadaBytes = null
                                        )
                                        mostrarDialogoEditar = false
                                    }
                                }
                            ) {
                                Text("Guardar")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostrarDialogoEditar = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                // Diálogo: Eliminar Evento
                if (mostrarDialogoEliminar) {
                    AlertDialog(
                        onDismissRequest = { mostrarDialogoEliminar = false },
                        title = { Text("¿Eliminar evento?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                        text = {
                            Text("Esta acción eliminará el evento «${actual.evento.nombre}» permanentemente.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    mostrarDialogoEliminar = false
                                    viewModel.eliminar(onEliminado = onAtras)
                                }
                            ) {
                                Text("Eliminar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostrarDialogoEliminar = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }

                // Diálogo: Gestionar Rol de Participante en Evento
                participanteParaGestionar?.let { part ->
                    val esAdmin = part.rol == "ADMIN"
                    AlertDialog(
                        onDismissRequest = { participanteParaGestionar = null },
                        title = { Text("Gestionar a ${part.nombre}", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Rol en este evento: ${if (esAdmin) "Admin delegado" else "Asistente"}")
                                if (!esAdmin) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.asignarRol(part.usuarioId, "ADMIN")
                                            participanteParaGestionar = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("⭐ Asignar como Admin delegado del evento")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.asignarRol(part.usuarioId, "ASISTENTE")
                                            participanteParaGestionar = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("👤 Quitar rol de Admin (pasar a Asistente)")
                                    }
                                }

                                if (actual.puedeGestionar) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.expulsarParticipante(part.usuarioId)
                                            participanteParaGestionar = null
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFF5252)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🚪 Expulsar del evento", color = Color(0xFFFF5252))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { participanteParaGestionar = null }) {
                                Text("Cerrar")
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatearFechaHora(millis: Long): String {
    val formato = SimpleDateFormat("d 'de' MMMM, h:mm a", Locale.forLanguageTag("es-ES"))
    formato.timeZone = TimeZone.getTimeZone("UTC")
    return formato.format(Date(millis))
}

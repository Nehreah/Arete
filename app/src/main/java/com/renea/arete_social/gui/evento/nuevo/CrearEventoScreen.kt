package com.renea.arete_social.gui.evento.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.feature.eventos.CrearEventoUiState
import com.renea.arete_social.feature.eventos.CrearEventoViewModel
import com.renea.arete_social.feature.eventos.HoraEvento
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarChip
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarScreenColumn
import com.renea.arete_social.gui.theme.RadarSectionTitle
import com.renea.arete_social.gui.theme.RadarTextField
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearEventoScreen(
    viewModel: CrearEventoViewModel,
    onEventoCreado: (String) -> Unit,
    onCancelar: () -> Unit,
    ubicacionInicial: com.renea.arete_social.domain.model.Ubicacion? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val estado by viewModel.estado.collectAsState()
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var hora by remember { mutableStateOf(HoraEvento.CUATRO_PM) }
    var visibilidad by remember { mutableStateOf(VisibilidadEvento.PUBLICO) }
    var portadaUri by remember { mutableStateOf<Uri?>(null) }
    val selectorPortada = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) portadaUri = uri
    }
    var mostrarSelectorFecha by remember { mutableStateOf(false) }
    val estadoFecha = rememberDatePickerState()

    LaunchedEffect(estado) {
        val actual = estado
        if (actual is CrearEventoUiState.Creado) {
            onEventoCreado(actual.eventoId)
        }
    }

    if (mostrarSelectorFecha) {
        DatePickerDialog(
            onDismissRequest = { mostrarSelectorFecha = false },
            confirmButton = {
                TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarSelectorFecha = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estadoFecha)
        }
    }

    RadarScreenColumn(modifier = modifier) {
        RadarSectionTitle("Crear un evento")
        Text(
            text = "Organiza un encuentro y comparte cuándo y dónde tendrá lugar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        RadarTextField(
            valor = nombre,
            onValorCambia = { nombre = it },
            etiqueta = "Nombre (ej. Reunión de estudiantes)"
        )
        RadarTextField(
            valor = descripcion,
            onValorCambia = { descripcion = it },
            etiqueta = "Descripción (opcional)"
        )

        RadarSectionTitle("Foto de portada (opcional)")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable {
                    selectorPortada.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            if (portadaUri != null) {
                AsyncImage(
                    model = portadaUri,
                    contentDescription = "Portada del evento",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 28.sp)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Text(
                        "Toca para agregar foto de portada",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "O continuar sin foto",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (portadaUri != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cambiar foto",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        selectorPortada.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                Text("·", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Quitar foto (subir sin foto)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { portadaUri = null }
                )
            }
        }

        RadarSectionTitle("Fecha")
        RadarOutlinedButton(
            texto = estadoFecha.selectedDateMillis?.let { formatearFecha(it) } ?: "Elegir fecha",
            onClick = { mostrarSelectorFecha = true }
        )

        RadarSectionTitle("Hora")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoraEvento.entries.forEach { opcion ->
                RadarChip(
                    texto = opcion.etiqueta,
                    seleccionado = opcion == hora,
                    onClick = { hora = opcion }
                )
            }
        }

        RadarSectionTitle("Lugar del evento")
        Text(
            text = if (ubicacionInicial != null) {
                "📍 Lugar elegido en el mapa: ${String.format(Locale.US, "%.4f", ubicacionInicial.latitud)}, ${String.format(Locale.US, "%.4f", ubicacionInicial.longitud)}"
            } else {
                "📍 Sin punto geográfico asignado (evento general/virtual)"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        RadarSectionTitle("Privacidad")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadarChip(
                texto = "🌐 Público",
                seleccionado = visibilidad == VisibilidadEvento.PUBLICO,
                onClick = { visibilidad = VisibilidadEvento.PUBLICO }
            )
            RadarChip(
                texto = if (viewModel.esComunal) "👥 Solo la comunidad" else "🔒 Privado",
                seleccionado = if (viewModel.esComunal) {
                    visibilidad == VisibilidadEvento.COMUNIDAD || visibilidad == VisibilidadEvento.AMIGOS
                } else visibilidad == VisibilidadEvento.AMIGOS,
                onClick = { visibilidad = if (viewModel.esComunal) VisibilidadEvento.COMUNIDAD else VisibilidadEvento.AMIGOS }
            )
        }
        Text(
            text = when {
                visibilidad == VisibilidadEvento.PUBLICO -> "Cualquier persona podrá ver este evento y unirse."
                viewModel.esComunal -> "Solo las personas que pertenecen a esta comunidad podrán verlo."
                else -> "Solo tú y tus amistades podrán ver este evento."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        if (estado is CrearEventoUiState.Error) {
            Text(
                text = (estado as CrearEventoUiState.Error).mensaje,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (estado is CrearEventoUiState.Guardando) {
            RadarLoading(modifier = Modifier.height(60.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadarButton(
                    texto = "Crear evento",
                    onClick = {
                        val fechaDia = estadoFecha.selectedDateMillis ?: System.currentTimeMillis()
                        val bytes = portadaUri?.let { uri ->
                            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                        }
                        viewModel.crear(
                            nombre = nombre,
                            descripcion = descripcion,
                            fechaDiaMillis = fechaDia,
                            hora = hora,
                            ubicacion = ubicacionInicial,
                            visibilidad = visibilidad,
                            portadaBytes = bytes
                        )
                    }
                )
                RadarOutlinedButton(texto = "Cancelar", onClick = onCancelar)
            }
        }
    }
}

private fun formatearFecha(millis: Long): String {
    val formato = SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es-ES"))
    formato.timeZone = TimeZone.getTimeZone("UTC")
    return formato.format(Date(millis))
}

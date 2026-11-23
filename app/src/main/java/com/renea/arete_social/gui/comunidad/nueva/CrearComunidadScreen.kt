package com.renea.arete_social.gui.comunidad.nueva

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.comunidades.CrearComunidadUiState
import com.renea.arete_social.feature.comunidades.CrearComunidadViewModel

@Composable
fun CrearComunidadScreen(
    viewModel: CrearComunidadViewModel,
    onComunidadCreada: (String) -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val estado by viewModel.estado.collectAsState()
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val selectorAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri
    }
    val guardando = estado is CrearComunidadUiState.Guardando
    val colores = MaterialTheme.colorScheme

    LaunchedEffect(estado) {
        val actual = estado
        if (actual is CrearComunidadUiState.Creada) onComunidadCreada(actual.comunidadId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colores.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(colores.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", fontSize = 27.sp, color = colores.primary, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Crear comunidad", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = colores.onBackground)
                Text(
                    "Encuentra personas que compartan tus intereses.",
                    fontSize = 14.sp,
                    color = colores.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colores.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Información básica", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colores.onSurface)
                    Text("Puedes elegir una foto de avatar y ajustar los detalles.", fontSize = 13.sp, color = colores.onSurfaceVariant)
                }

                // Selector de foto de avatar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(colores.surfaceVariant)
                            .border(2.dp, colores.primary, CircleShape)
                            .clickable(enabled = !guardando) {
                                selectorAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUri != null) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = "Foto de comunidad",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📷", fontSize = 24.sp)
                                Text("Foto", fontSize = 11.sp, color = colores.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (avatarUri == null) "Elegir foto de avatar (opcional)" else "Cambiar foto",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colores.primary,
                            modifier = Modifier.clickable(enabled = !guardando) {
                                selectorAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        if (avatarUri != null) {
                            Text("·", fontSize = 12.sp, color = colores.onSurfaceVariant)
                            Text(
                                text = "Quitar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colores.error,
                                modifier = Modifier.clickable(enabled = !guardando) {
                                    avatarUri = null
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Nombre de la comunidad *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colores.onSurface)
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej. Psicología Univalle") },
                        singleLine = true,
                        enabled = !guardando,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colores.primary,
                            cursorColor = colores.primary
                        )
                    )
                    Text("Elige un nombre claro y fácil de reconocer.", fontSize = 12.sp, color = colores.onSurfaceVariant)
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Descripción", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colores.onSurface)
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("¿Qué reúne a esta comunidad?") },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !guardando,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colores.primary,
                            cursorColor = colores.primary
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Categoría", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colores.onSurface)
                    OutlinedTextField(
                        value = tipo,
                        onValueChange = { tipo = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej. académica, arte, deportes") },
                        singleLine = true,
                        enabled = !guardando,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colores.primary,
                            cursorColor = colores.primary
                        )
                    )
                    Text("Ideas para empezar", fontSize = 12.sp, color = colores.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Académica", "Arte", "Deportes").forEach { categoria ->
                            FilterChip(
                                selected = tipo.equals(categoria, ignoreCase = true),
                                onClick = { if (!guardando) tipo = categoria },
                                label = { Text(categoria) },
                                enabled = !guardando
                            )
                        }
                    }
                }
            }
        }

        if (estado is CrearComunidadUiState.Error) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colores.errorContainer)
            ) {
                Text(
                    text = (estado as CrearComunidadUiState.Error).mensaje,
                    modifier = Modifier.padding(14.dp),
                    color = colores.onErrorContainer,
                    fontSize = 14.sp
                )
            }
        }

        if (guardando) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colores.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colores.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Creando tu comunidad…", color = colores.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val bytes = avatarUri?.let { uri ->
                            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                        }
                        viewModel.crear(nombre.trim(), descripcion.trim(), tipo.trim(), bytes)
                    },
                    enabled = nombre.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colores.primary, contentColor = colores.onPrimary)
                ) {
                    Text("Crear comunidad", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("Cancelar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(colores.primary))
            Spacer(Modifier.width(7.dp))
            Text("Las comunidades crecen con la participación de todos", fontSize = 11.sp, color = colores.onSurfaceVariant)
        }
    }
}

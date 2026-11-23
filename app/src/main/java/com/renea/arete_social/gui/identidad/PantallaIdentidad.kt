package com.renea.arete_social.gui.identidad

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.renea.arete_social.feature.identidad.IdentidadUiState
import com.renea.arete_social.feature.identidad.IdentityViewModel
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarScaffold

/**
 * Pantalla de identidad soberana Aréte.
 *
 * Flujos:
 * 1. **Sin identidad** → botones "Crear nueva identidad" y "Restaurar desde frase".
 * 2. **Crear** → muestra las 12 palabras en cuadrícula 4×3; botón "Ya la anoté".
 * 3. **Restaurar** → formulario de 12 campos de texto para introducir la frase.
 * 4. **Cargada** → muestra la huella de la clave pública IK (últimos 8 hex).
 */
@Composable
fun PantallaIdentidad(
    viewModel: IdentityViewModel,
    onAtras: () -> Unit
) {
    val estado by viewModel.estado.collectAsState()

    RadarScaffold(titulo = "Identidad soberana", onAtras = onAtras) {
        AnimatedContent(
            targetState = estado,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "identidad_contenido"
        ) { uiState ->
            when (uiState) {
                is IdentidadUiState.Vacio -> PantallaVacia(
                    onCrear = viewModel::generarNuevaIdentidad,
                    onRestaurar = viewModel::restaurarDesde
                )
                is IdentidadUiState.Cargando -> PantallaCargando()
                is IdentidadUiState.Cargada -> {
                    if (uiState.palabras.isNotEmpty()) {
                        PantallaConfirmarFrase(
                            palabras = uiState.palabras,
                            onConfirmar = viewModel::confirmarFraseAnotada
                        )
                    } else {
                        PantallaCargada(
                            ikPublicHex = uiState.ikPublicHex,
                            onOlvidar = viewModel::olvidarIdentidad
                        )
                    }
                }
                is IdentidadUiState.Error -> PantallaError(
                    mensaje = uiState.mensaje,
                    onReintentar = { viewModel.olvidarIdentidad() }
                )
            }
        }
    }
}

// ─── Sub-pantallas ─────────────────────────────────────────────────────────────

@Composable
private fun PantallaVacia(onCrear: () -> Unit, onRestaurar: (List<String>) -> Unit) {
    // La restauración se muestra inline con un toggle de estado local
    var mostrandoRestaurar by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Sin identidad activa",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Tu identidad Aréte es una clave criptográfica derivada de 12 palabras. " +
                    "Nadie más puede generarla ni recuperarla: solo tú.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        if (!mostrandoRestaurar) {
            RadarButton(texto = "Crear nueva identidad", onClick = onCrear, modifier = Modifier.fillMaxWidth())
            RadarOutlinedButton(texto = "Restaurar desde frase", onClick = { mostrandoRestaurar = true }, modifier = Modifier.fillMaxWidth())
        } else {
            PanelRestaurar(onRestaurar = { lista -> onRestaurar(lista) }, onCancelar = { mostrandoRestaurar = false })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelRestaurar(onRestaurar: (List<String>) -> Unit, onCancelar: () -> Unit) {
    // 12 palabras editables
    val palabras = remember { List(12) { mutableStateOf("") } }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Escribe tus 12 palabras de recuperación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Grid 2 columnas
        for (fila in 0 until 6) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (col in 0..1) {
                    val idx = fila * 2 + col
                    var texto by palabras[idx]
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = texto,
                            onValueChange = { texto = it.trim().lowercase() },
                            label = { Text("${idx + 1}") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = if (idx < 11) ImeAction.Next else ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        RadarButton(
            texto = "Restaurar identidad",
            onClick = { onRestaurar(palabras.map { it.value }) },
            modifier = Modifier.fillMaxWidth()
        )
        RadarOutlinedButton(texto = "Cancelar", onClick = onCancelar, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantallaConfirmarFrase(palabras: List<String>, onConfirmar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Anota tus 12 palabras",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Esta frase es la ÚNICA forma de recuperar tu identidad. " +
                    "Escríbela en papel y guárdala en un lugar seguro. " +
                    "Nunca la compartas digitalmente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Grid 4 columnas × 3 filas
        FlowRow(
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            palabras.forEachIndexed { idx, palabra ->
                PalabraChip(numero = idx + 1, palabra = palabra)
            }
        }

        Spacer(Modifier.height(16.dp))

        RadarButton(
            texto = "Ya la anoté en papel",
            onClick = onConfirmar,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PalabraChip(numero: Int, palabra: String) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)) {
                    append("$numero ")
                }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) {
                    append(palabra)
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PantallaCargada(ikPublicHex: String, onOlvidar: () -> Unit) {
    // Huella: últimos 8 caracteres hex de la IK pública (legible sin revelar la clave completa)
    val huella = if (ikPublicHex.length >= 8) ikPublicHex.takeLast(8).uppercase() else ikPublicHex.uppercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Identidad activa",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Huella IK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = huella,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "…${ikPublicHex.takeLast(20)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Text(
            text = "Tu identidad está cargada en memoria. Para cambiarla, primero olvídala.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        RadarOutlinedButton(
            texto = "Olvidar identidad (cerrar sesión local)",
            onClick = onOlvidar,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PantallaCargando() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("Derivando identidad…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PantallaError(mensaje: String, onReintentar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))
        RadarOutlinedButton(texto = "Volver al inicio", onClick = onReintentar, modifier = Modifier.fillMaxWidth())
    }
}

package com.renea.arete_social.gui.lugar.nuevo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.renea.arete_social.feature.lugares.CrearLugarUiState
import com.renea.arete_social.feature.lugares.CrearLugarViewModel
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarScreenColumn
import com.renea.arete_social.gui.theme.RadarTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

@Composable
fun CrearLugarScreen(
    viewModel: CrearLugarViewModel,
    onLugarCreado: (String) -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.estado.collectAsState()
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    LaunchedEffect(estado) {
        val actual = estado
        if (actual is CrearLugarUiState.Creado) {
            onLugarCreado(actual.lugarId)
        }
    }

    RadarScreenColumn(modifier = modifier) {
        com.renea.arete_social.gui.theme.RadarSectionTitle("Crear un lugar")
        Text(
            text = "Añade un punto de interés para que otras personas puedan encontrarlo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RadarTextField(
            valor = nombre,
            onValorCambia = { nombre = it },
            etiqueta = "Nombre (ej. Biblioteca, Cafetería...)"
        )
        RadarTextField(
            valor = descripcion,
            onValorCambia = { descripcion = it },
            etiqueta = "Descripción (opcional)"
        )

        if (estado is CrearLugarUiState.Error) {
            Text(
                text = (estado as CrearLugarUiState.Error).mensaje,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (estado is CrearLugarUiState.Guardando) {
            RadarLoading(modifier = Modifier.height(60.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadarButton(texto = "Crear lugar", onClick = { viewModel.crear(nombre, descripcion) })
                RadarOutlinedButton(texto = "Cancelar", onClick = onCancelar)
            }
        }
    }
}

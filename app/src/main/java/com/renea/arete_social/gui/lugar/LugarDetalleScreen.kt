package com.renea.arete_social.gui.lugar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.renea.arete_social.feature.lugares.LugarDetalleUiState
import com.renea.arete_social.feature.lugares.LugarDetalleViewModel
import com.renea.arete_social.gui.theme.RadarBody
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarErrorScreen
import com.renea.arete_social.gui.theme.RadarLoadingScreen
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarScreenColumn
import com.renea.arete_social.gui.theme.RadarSectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LugarDetalleScreen(
    viewModel: LugarDetalleViewModel,
    onAbrirChat: (String) -> Unit,
    onCrearEventoAqui: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.estado.collectAsState()
    var refrescando by remember { mutableStateOf(false) }
    if (estado !is LugarDetalleUiState.Cargando) refrescando = false

    PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.recargar() },
        modifier = modifier
    ) {
        when (val actual = estado) {
            is LugarDetalleUiState.Cargando -> RadarLoadingScreen()

            is LugarDetalleUiState.Error -> RadarErrorScreen(mensaje = actual.mensaje)

            is LugarDetalleUiState.Listo -> RadarScreenColumn {
                RadarSectionTitle(actual.lugar.nombre)
                if (actual.lugar.descripcion.isNotBlank()) {
                    RadarBody(actual.lugar.descripcion)
                }
                // El chat del lugar (chatId) ya existe desde que se creó el Lugar
                // (Etapa 2); la pantalla de chat en sí se conectó en la Etapa 8.
                RadarButton(
                    texto = "Abrir chat del lugar",
                    onClick = { onAbrirChat(actual.lugar.chatId) },
                    modifier = Modifier.fillMaxWidth()
                )
                // Un Evento "ocurre en" un Lugar que ya existe (Etapa 7) — este es el
                // punto natural para crear uno.
                RadarOutlinedButton(
                    texto = "Crear evento aquí",
                    onClick = { onCrearEventoAqui(actual.lugar.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

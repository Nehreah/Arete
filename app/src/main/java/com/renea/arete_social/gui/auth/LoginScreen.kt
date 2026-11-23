package com.renea.arete_social.gui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.auth.SesionUiState
import com.renea.arete_social.feature.auth.SesionViewModel
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarTextField

@Composable
fun LoginScreen(
    viewModel: SesionViewModel,
    onLoginExitoso: (String) -> Unit,
    onIrARegistro: () -> Unit,
    onGestionarIdentidad: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.estado.collectAsState()
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mostrarDialogoInvitadoEula by remember { mutableStateOf(false) }

    LaunchedEffect(estado) {
        val actual = estado
        if (actual is SesionUiState.Autenticado) {
            onLoginExitoso(actual.usuarioId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "arete",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text(
            text = "INICIAR SESIÓN",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Conecta con las personas y comunidades que te rodean.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        RadarTextField(
            valor = usuario,
            onValorCambia = { usuario = it; viewModel.limpiarError() },
            etiqueta = "Usuario"
        )
        RadarTextField(
            valor = password,
            onValorCambia = { password = it; viewModel.limpiarError() },
            etiqueta = "Contraseña",
            transformacionVisual = PasswordVisualTransformation()
        )

        if (estado is SesionUiState.Error) {
            AvisoFormulario(mensaje = (estado as SesionUiState.Error).mensaje)
        }

        if (estado is SesionUiState.Cargando) {
            RadarLoading(modifier = Modifier.height(60.dp))
        } else {
            RadarButton(
                texto = "Entrar",
                onClick = { viewModel.iniciarSesion(usuario, password) },
                modifier = Modifier.fillMaxWidth()
            )
            RadarOutlinedButton(
                texto = "Soy invitado",
                onClick = { mostrarDialogoInvitadoEula = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        TextButton(onClick = onIrARegistro) {
            Text("No tengo cuenta — registrarme", color = MaterialTheme.colorScheme.onBackground)
        }

        if (onGestionarIdentidad != null) {
            TextButton(onClick = onGestionarIdentidad) {
                Text(
                    "🔑 Identidad soberana local",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.TextButton(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/Nehreah/AntiRed/blob/main/docs/POLITICA_PRIVACIDAD.md")
                )
                runCatching { context.startActivity(intent) }
            }
        ) {
            Text(
                text = "📜 Política de Privacidad y Normas (+18)",
                fontSize = 11.5.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (mostrarDialogoInvitadoEula) {
        DialogoEulaNormas(
            onDismiss = { mostrarDialogoInvitadoEula = false },
            onAceptar = {
                mostrarDialogoInvitadoEula = false
                viewModel.entrarComoInvitado()
            }
        )
    }
}

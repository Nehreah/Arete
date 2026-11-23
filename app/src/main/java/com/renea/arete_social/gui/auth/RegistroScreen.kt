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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.auth.SesionUiState
import com.renea.arete_social.feature.auth.SesionViewModel
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarTextField

@Composable
fun RegistroScreen(
    viewModel: SesionViewModel,
    onRegistroExitoso: (String) -> Unit,
    onIrALogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.estado.collectAsState()
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var aceptoTerminos by remember { mutableStateOf(false) }
    var mostrarDialogoEula by remember { mutableStateOf(false) }
    var errorTerminos by remember { mutableStateOf(false) }

    LaunchedEffect(estado) {
        val actual = estado
        if (actual is SesionUiState.Autenticado) {
            onRegistroExitoso(actual.usuarioId)
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
            text = "CREAR CUENTA",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Únete a Aréte y empieza a descubrir tu entorno.",
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

        // Casilla obligatoria de Términos y Normas Comunitarias (EULA / Seguridad Comunitaria)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = aceptoTerminos,
                onCheckedChange = {
                    aceptoTerminos = it
                    if (it) errorTerminos = false
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = if (errorTerminos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { mostrarDialogoEula = true }
            ) {
                Text(
                    text = "Declaro ser mayor de 18 años y acepto los Términos y Normas (EULA)",
                    color = if (errorTerminos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )
                Text(
                    text = "Exclusivo para mayores de 18 años. Tolerancia cero a contenido abusivo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        if (errorTerminos) {
            AvisoFormulario(mensaje = "Debes ser mayor de 18 años y aceptar los Términos de Servicio y Normas Comunitarias (EULA) para crear tu cuenta.")
        }

        if (estado is SesionUiState.Error) {
            AvisoFormulario(mensaje = (estado as SesionUiState.Error).mensaje)
        }

        if (estado is SesionUiState.Cargando) {
            RadarLoading(modifier = Modifier.height(60.dp))
        } else {
            RadarButton(
                texto = "Registrarme",
                onClick = {
                    if (!aceptoTerminos) {
                        errorTerminos = true
                    } else {
                        errorTerminos = false
                        viewModel.registrar(usuario, password)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        TextButton(onClick = onIrALogin) {
            Text("Ya tengo cuenta — iniciar sesión", color = MaterialTheme.colorScheme.onBackground)
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
                text = "📜 Ver Política de Privacidad y Normas en la web",
                fontSize = 11.5.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (mostrarDialogoEula) {
        DialogoEulaNormas(
            onDismiss = { mostrarDialogoEula = false },
            onAceptar = {
                aceptoTerminos = true
                errorTerminos = false
                mostrarDialogoEula = false
            }
        )
    }
}

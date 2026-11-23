package com.renea.arete_social.gui.auth.perfil

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.gui.auth.DialogoEulaNormas
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarTextField
import com.renea.arete_social.gui.theme.ModoTema
import com.renea.arete_social.gui.theme.PreferenciaTemaApp

@Composable
internal fun DialogoAjustesPerfil(
    nombre: String,
    onNombreCambia: (String) -> Unit,
    descripcion: String,
    onDescripcionCambia: (String) -> Unit,
    visibilidad: Visibilidad,
    onVisibilidadCambia: (Visibilidad) -> Unit,
    perfilPrivado: Boolean,
    onPerfilPrivadoCambia: (Boolean) -> Unit,
    compartirPorCercania: Boolean,
    onCompartirPorCercaniaCambia: (Boolean) -> Unit,
    guardando: Boolean,
    onGuardar: () -> Unit,
    onEditarGustos: () -> Unit,
    onGestionarIdentidad: (() -> Unit)? = null,
    onCerrarSesion: () -> Unit,
    onEliminarCuenta: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var mostrarEula by remember { mutableStateOf(false) }
    var mostrarConfirmarEliminar by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// AJUSTES DE PERFIL //",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Default,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "APARIENCIA",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable {
                            val siguiente = if (PreferenciaTemaApp.modo == ModoTema.CLARO) ModoTema.OSCURO else ModoTema.CLARO
                            PreferenciaTemaApp.establecer(siguiente)
                        }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo ${if (PreferenciaTemaApp.modo == ModoTema.OSCURO) "oscuro" else "claro"}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Toca para cambiar y guardar la apariencia", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Text(if (PreferenciaTemaApp.modo == ModoTema.OSCURO) "☾" else "☀", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(14.dp))

                RadarTextField(
                    valor = nombre,
                    onValorCambia = onNombreCambia,
                    etiqueta = "Tu nombre"
                )

                Spacer(modifier = Modifier.height(8.dp))

                RadarTextField(
                    valor = descripcion,
                    onValorCambia = onDescripcionCambia,
                    etiqueta = "Biografía o estado",
                    lineasMaximas = 3
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "VISIBILIDAD EN RADAR",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectorVisibilidadSocial(
                    seleccionado = visibilidad,
                    onSeleccionar = onVisibilidadCambia
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "PRIVACIDAD DEL PERFIL",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectorPrivacidadPerfil(
                    privado = perfilPrivado,
                    onCambia = onPerfilPrivadoCambia
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "COMPARTIR POR CERCANÍA",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(4.dp))
                SelectorCompartirPorCercania(
                    activado = compartirPorCercania,
                    onCambia = onCompartirPorCercaniaCambia
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (guardando) {
                    RadarLoading(modifier = Modifier.height(44.dp))
                } else {
                    RadarButton(
                        texto = "GUARDAR CAMBIOS",
                        onClick = {
                            onGuardar()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                RadarOutlinedButton(
                    texto = "Editar gustos",
                    onClick = onEditarGustos,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                RadarOutlinedButton(
                    texto = "📜 Términos y condiciones",
                    onClick = { mostrarEula = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val context = LocalContext.current
                RadarOutlinedButton(
                    texto = "🛡️ Política de Privacidad (Web)",
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Nehreah/AntiRed/blob/main/docs/POLITICA_PRIVACIDAD.md")
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (onGestionarIdentidad != null) {
                    RadarOutlinedButton(
                        texto = "🔑 Identidad soberana local",
                        onClick = onGestionarIdentidad,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                RadarOutlinedButton(
                    texto = "Cerrar sesión",
                    onClick = {
                        onDismiss()
                        onCerrarSesion()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { mostrarConfirmarEliminar = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🗑️ Eliminar cuenta y datos",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (mostrarEula) {
        DialogoEulaNormas(
            onDismiss = { mostrarEula = false },
            onAceptar = { mostrarEula = false }
        )
    }

    if (mostrarConfirmarEliminar) {
        DialogoConfirmarEliminarCuenta(
            onDismiss = { mostrarConfirmarEliminar = false },
            onConfirmar = {
                mostrarConfirmarEliminar = false
                onDismiss()
                onEliminarCuenta()
            }
        )
    }
}

@Composable
internal fun SelectorVisibilidadSocial(
    seleccionado: Visibilidad,
    onSeleccionar: (Visibilidad) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BotonPildoraVisibilidad(
            texto = "📡 Visible",
            activo = seleccionado == Visibilidad.VISIBLE,
            onClick = { onSeleccionar(Visibilidad.VISIBLE) },
            modifier = Modifier.weight(1f)
        )
        BotonPildoraVisibilidad(
            texto = "📍 Aprox.",
            activo = seleccionado == Visibilidad.APROXIMADA,
            onClick = { onSeleccionar(Visibilidad.APROXIMADA) },
            modifier = Modifier.weight(1f)
        )
        BotonPildoraVisibilidad(
            texto = "🕶️ Oculto",
            activo = seleccionado == Visibilidad.INVISIBLE,
            onClick = { onSeleccionar(Visibilidad.INVISIBLE) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Perfil público: cualquier persona ve tus estados, fotos y librería.
 * Perfil privado: solo tus amistades. Las cartas siempre son solo entre quien las escribe y quien las recibe.
 */
@Composable
internal fun SelectorPrivacidadPerfil(
    privado: Boolean,
    onCambia: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BotonPildoraVisibilidad(
                texto = "🌐 Público",
                activo = !privado,
                onClick = { onCambia(false) },
                modifier = Modifier.weight(1f)
            )
            BotonPildoraVisibilidad(
                texto = "🔒 Privado",
                activo = privado,
                onClick = { onCambia(true) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (privado) {
                "Solo tus amistades ven tu bitácora y tu librería."
            } else {
                "Cualquier persona puede ver tu bitácora y tu librería."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/**
 * Si está activado, quienes estén cerca de ti por Bluetooth reciben los lugares y eventos PÚBLICOS que
 * descubriste (los ven 24 h). Lo que otros comparten contigo siempre lo recibes.
 */
@Composable
internal fun SelectorCompartirPorCercania(
    activado: Boolean,
    onCambia: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BotonPildoraVisibilidad(
                texto = "📡 Activado",
                activo = activado,
                onClick = { onCambia(true) },
                modifier = Modifier.weight(1f)
            )
            BotonPildoraVisibilidad(
                texto = "⏸ Desactivado",
                activo = !activado,
                onClick = { onCambia(false) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (activado) {
                "Quien se cruce contigo por Bluetooth recibe los lugares y eventos públicos que descubriste."
            } else {
                "No compartes lo que descubriste. Igual recibes lo que otros compartan contigo."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun BotonPildoraVisibilidad(
    texto: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (activo) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .border(1.dp, if (activo) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = if (activo) FontWeight.Black else FontWeight.Normal
        )
    }
}

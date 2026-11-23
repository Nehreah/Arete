package com.renea.arete_social.gui.auth.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.LimitesCarta
import com.renea.arete_social.feature.auth.CartasPerfilUiState
import com.renea.arete_social.gui.theme.BotonPildora
import com.renea.arete_social.gui.theme.EstadoVacioSocial
import com.renea.arete_social.gui.theme.RadarTextField
import com.renea.arete_social.gui.theme.TipografiaLectura
import com.renea.arete_social.gui.theme.formatearFechaCorta
import kotlinx.coroutines.delay

/**
 * Pestaña "Cartas" del perfil de OTRA persona: la correspondencia entre ustedes dos
 * (las que le escribiste y las que te escribió) y el acceso para escribirle una nueva.
 * Sin lógica de negocio: pinta [estado] y emite eventos.
 */
@Composable
fun CartasPerfilPublico(
    estado: CartasPerfilUiState,
    nombreOtro: String,
    miUsuarioId: String,
    esAmigo: Boolean,
    perfilPrivado: Boolean = false,
    onEscribir: () -> Unit,
    onRecargar: () -> Unit,
    onAbrir: (Carta) -> Unit,
    onConsumirMensaje: () -> Unit,
    modifier: Modifier = Modifier
) {
    estado.mensaje?.let { mensaje ->
        LaunchedEffect(mensaje) { delay(3_500); onConsumirMensaje() }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!estado.disponible) {
            EstadoVacioSocial(
                icono = "✉",
                titulo = "Cartas no disponibles",
                subtitulo = "No es posible intercambiar cartas con este perfil."
            )
            return@Column
        }

        val puedeEscribir = !perfilPrivado || esAmigo
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonPildora(
                texto = "✎  Escribirle una carta",
                onClick = onEscribir,
                habilitado = puedeEscribir && !estado.enviando,
                modifier = Modifier.weight(1f)
            )
            BotonPildora("↻", onClick = onRecargar, destacado = false, habilitado = !estado.cargando)
        }

        if (perfilPrivado && !esAmigo) {
            Text(
                text = "Agrégalo como amistad para poder escribirle una carta a este perfil privado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (estado.errorCarga) {
            Text("No se pudieron cargar las cartas.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            }
        }

        if (estado.cartas.isEmpty() && !estado.cargando && !estado.errorCarga) {
            EstadoVacioSocial(
                icono = "🕊",
                titulo = "Aún no hay cartas con $nombreOtro",
                subtitulo = "Las cartas que se escriban entre ustedes aparecerán aquí. Solo ustedes dos pueden verlas."
            )
        } else {
            estado.cartas.forEach { carta ->
                TarjetaCartaPerfil(carta = carta, esRecibida = carta.destinatarioId == miUsuarioId) { onAbrir(carta) }
            }
        }
    }
}

@Composable
private fun TarjetaCartaPerfil(carta: Carta, esRecibida: Boolean, onClick: () -> Unit) {
    val colores = MaterialTheme.colorScheme
    val sinLeer = esRecibida && !carta.leida
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(forma)
            .background(colores.surface)
            .border(1.dp, if (sinLeer) colores.primary.copy(alpha = 0.55f) else colores.outline.copy(alpha = 0.2f), forma)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (sinLeer) colores.primary else colores.outlineVariant))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (esRecibida) "Te escribió" else "Tú le escribiste",
                    color = colores.onSurface,
                    fontSize = 13.sp,
                    fontWeight = if (sinLeer) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatearFechaCorta(carta.creadaEn), color = colores.onSurfaceVariant, fontSize = 10.sp)
            }
            Text(
                carta.contenido,
                style = TipografiaLectura.vistaPrevia,
                color = colores.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Lectura de una carta de la correspondencia. */
@Composable
fun DialogoLeerCartaPerfil(
    carta: Carta,
    esRecibida: Boolean,
    onCerrar: () -> Unit,
    onEliminar: () -> Unit
) {
    val colores = MaterialTheme.colorScheme
    // Quien envió solo puede retirar una carta mientras no se haya leído (lo exige el servidor).
    val puedeEliminar = esRecibida || !carta.leida
    Dialog(onDismissRequest = onCerrar) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colores.surface)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (esRecibida) "De ${carta.remitenteNombre}" else "Para ${carta.destinatarioNombre}",
                style = TipografiaLectura.titulo,
                color = colores.onSurface
            )
            Text(formatearFechaCorta(carta.creadaEn), style = TipografiaLectura.firma, color = colores.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false).height(280.dp).verticalScroll(rememberScrollState())) {
                Text(carta.contenido, style = TipografiaLectura.cuerpo, color = colores.onSurface)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (puedeEliminar) BotonPildora("Eliminar", onClick = onEliminar, destacado = false, modifier = Modifier.weight(1f))
                BotonPildora("Cerrar", onClick = onCerrar, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Redactor de carta con el destinatario ya fijado (la persona cuyo perfil se está viendo). */
@Composable
fun DialogoEscribirCartaPerfil(
    nombreDestinatario: String,
    enviando: Boolean,
    onEnviar: (texto: String, alTerminar: (error: String?) -> Unit) -> Unit,
    onCerrar: () -> Unit
) {
    val colores = MaterialTheme.colorScheme
    var texto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!enviando) onCerrar() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colores.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Carta para $nombreDestinatario", style = TipografiaLectura.titulo, color = colores.onSurface)
            RadarTextField(
                valor = texto,
                onValorCambia = { if (it.length <= LimitesCarta.MAX_CARACTERES) texto = it },
                etiqueta = "Querida amistad…",
                lineasMinimas = 6,
                lineasMaximas = 10,
                estiloTexto = TipografiaLectura.cuerpo.copy(color = colores.onSurface),
                tamanoEtiqueta = 12.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(error ?: "", color = colores.error, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("${texto.length}/${LimitesCarta.MAX_CARACTERES}", color = colores.onSurfaceVariant, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora("Cancelar", onClick = onCerrar, destacado = false, habilitado = !enviando, modifier = Modifier.weight(1f))
                BotonPildora(
                    texto = if (enviando) "Enviando…" else "Enviar  ✉",
                    onClick = {
                        error = null
                        onEnviar(texto) { fallo -> error = fallo }
                    },
                    habilitado = !enviando,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

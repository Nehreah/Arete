package com.renea.arete_social.gui.cartas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.renea.arete_social.feature.cartas.CartasUiState
import com.renea.arete_social.gui.theme.BotonPildora
import com.renea.arete_social.gui.theme.EstadoVacioSocial
import com.renea.arete_social.gui.theme.SelectorPildoras
import com.renea.arete_social.gui.theme.TipografiaLectura
import com.renea.arete_social.gui.theme.formatearFechaCorta
import kotlinx.coroutines.delay

/**
 * Contenido de la pestaña "Cartas" del perfil propio: solo el buzón (recibidas / enviadas).
 * Aquí NO se escribe: para escribir una carta se entra al perfil de una amistad.
 * Sin lógica: solo pinta [estado] y emite eventos.
 */
@Composable
fun CartasContenido(
    estado: CartasUiState,
    onRecargar: () -> Unit,
    onAbrir: (Carta) -> Unit,
    onEliminar: (Carta) -> Unit,
    onConsumirMensaje: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subpestana by remember { mutableIntStateOf(0) }
    var abierta by remember { mutableStateOf<Carta?>(null) }

    estado.mensaje?.let { mensaje ->
        LaunchedEffect(mensaje) { delay(3_500); onConsumirMensaje() }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!estado.disponible) {
            EstadoVacioSocial("✉", "Las cartas no están disponibles", "Esta función no se encuentra habilitada en este momento.")
            return@Column
        }

        SelectorPildoras(
            opciones = listOf("Recibidas (${estado.recibidas.size})", "Enviadas (${estado.enviadas.size})"),
            seleccion = subpestana,
            onSeleccion = { subpestana = it }
        )
        BotonPildora("↻  Actualizar", onClick = onRecargar, destacado = false, habilitado = !estado.cargando, modifier = Modifier.fillMaxWidth())
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            }
        }

        val lista = if (subpestana == 0) estado.recibidas else estado.enviadas
        if (lista.isEmpty() && !estado.cargando) {
            EstadoVacioSocial(
                icono = if (subpestana == 0) "📮" else "🕊",
                titulo = if (subpestana == 0) "Tu buzón está vacío" else "Aún no has enviado cartas",
                subtitulo = if (subpestana == 0) "Cuando una amistad te escriba, su carta aparecerá aquí." else "Entra al perfil de una amistad para escribirle: una carta se guarda, no se pierde en el chat."
            )
        } else {
            lista.forEach { carta ->
                TarjetaCarta(carta, esRecibida = subpestana == 0) {
                    abierta = carta
                    if (subpestana == 0) onAbrir(carta)
                }
            }
        }
    }

    abierta?.let { carta ->
        DialogoLeerCarta(
            carta = carta,
            esRecibida = subpestana == 0,
            onCerrar = { abierta = null },
            onEliminar = { onEliminar(carta); abierta = null }
        )
    }
}

@Composable
private fun TarjetaCarta(carta: Carta, esRecibida: Boolean, onClick: () -> Unit) {
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
                    text = if (esRecibida) "De ${carta.remitenteNombre}" else "Para ${carta.destinatarioNombre}",
                    color = colores.onSurface,
                    fontSize = 13.sp,
                    fontWeight = if (sinLeer) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatearFechaCorta(carta.creadaEn), color = colores.onSurfaceVariant, fontSize = 10.sp)
            }
            Text(carta.contenido, style = TipografiaLectura.vistaPrevia, color = colores.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DialogoLeerCarta(carta: Carta, esRecibida: Boolean, onCerrar: () -> Unit, onEliminar: () -> Unit) {
    val colores = MaterialTheme.colorScheme
    // Quien envió puede retirar una carta solo mientras no se haya leído (lo exige el servidor).
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
            Text(if (esRecibida) "De ${carta.remitenteNombre}" else "Para ${carta.destinatarioNombre}", style = TipografiaLectura.titulo, color = colores.onSurface)
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

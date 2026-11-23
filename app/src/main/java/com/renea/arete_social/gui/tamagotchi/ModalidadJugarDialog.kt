package com.renea.arete_social.gui.tamagotchi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Selector de modalidad de JUGAR: dos tarjetas grandes («Automático» y «Miau Snake») y Cancelar.
 * Targets de ≥ 88 dp de alto y rol de botón para lectores de pantalla.
 */
@Composable
fun ModalidadJugarDialog(selector: SelectorJugar, nombreMascota: String) {
    if (!selector.abierto) return
    Dialog(onDismissRequest = selector::cancelar) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("¿Cómo quieres jugar?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Elige cómo juega $nombreMascota ahora.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TarjetaModalidad(
                    titulo = "Automático",
                    detalle = "$nombreMascota juega sola, como siempre.",
                    onClick = selector::elegirAutomatico
                )
                TarjetaModalidad(
                    titulo = "Miau Snake",
                    detalle = "Tú controlas la partida. Si la juegas de verdad, cuenta como jugar.",
                    onClick = selector::elegirMiauSnake
                )
                TextButton(onClick = selector::cancelar, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun TarjetaModalidad(titulo: String, detalle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = "$titulo. $detalle" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            titulo, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(detalle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

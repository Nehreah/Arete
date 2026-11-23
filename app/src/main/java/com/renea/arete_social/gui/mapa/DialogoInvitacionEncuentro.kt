package com.renea.arete_social.gui.mapa

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.renea.arete_social.domain.model.SolicitudEncuentro

/**
 * Invitación a encontrarse. Hay que elegir: tocar fuera o el botón Atrás no cierran el diálogo,
 * porque aceptar comparte tu ubicación y rechazar debe ser una decisión explícita, no un descuido.
 */
@Composable
fun DialogoInvitacionEncuentro(
    solicitud: SolicitudEncuentro,
    onResponder: (acepta: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("📍 ${solicitud.remitenteNombre} quiere encontrarse contigo") },
        text = {
            Text(
                "Si aceptas, ${solicitud.remitenteNombre} y tú se verán en el mapa en tiempo real " +
                    "y se calculará una ruta entre los dos. Tu ubicación solo se comparte mientras dure " +
                    "el encuentro y puedes terminarlo cuando quieras."
            )
        },
        confirmButton = {
            TextButton(onClick = { onResponder(true) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = { onResponder(false) }) { Text("Rechazar") }
        }
    )
}

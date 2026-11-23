package com.renea.arete_social.gui.tamagotchi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.util.duracionLegible
import com.renea.arete_social.feature.tamagotchi.logic.RequisitoSuper
import com.renea.arete_social.feature.tamagotchi.logic.momentoDespertar
import com.renea.arete_social.feature.tamagotchi.logic.progresoSuper
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette
import com.renea.arete_social.gui.theme.RadarCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Camino al Súper»: las cinco estadísticas que cuentan (doradas al cumplirse), la pista más útil
 * para avanzar y la hora a la que despertará Michi. Mientras el Modo Súper está activo muestra el
 * tiempo que queda. La meta es «alcanzable pero no sencilla»: premia planificar cuándo duerme Michi.
 * Las reglas viven en feature/tamagotchi/logic/ModoSuper.kt.
 */
@Composable
internal fun PetSuperPanel(pet: PetEntity) {
    // Reloj propio de la tarjeta: el vencimiento del Súper y la hora de despertar no dependen de que la mascota cambie.
    val ahora by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    val progreso = remember(pet, ahora) { pet.progresoSuper(ahora) }
    val despertar = remember(pet, ahora) { pet.momentoDespertar(ahora) }

    RadarCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Camino al Súper",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (progreso.activo) {
                    PetPill("Súper · ${duracionLegible(progreso.restanteMillis)}", PetPalette.NeonGold)
                } else {
                    Text(
                        "${progreso.cumplidos}/${progreso.requisitos.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                progreso.requisitos.forEach { ChipRequisito(it, Modifier.weight(1f)) }
            }

            val mensaje = if (progreso.activo) {
                "Mientras dure, ves los lugares y eventos públicos de tu zona regional, aunque estén lejos."
            } else progreso.pista
            if (mensaje != null) {
                Text(mensaje, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            }

            if (despertar != null) {
                val hora = formatearHoraDespertar(despertar)
                Text(
                    if (pet.isSleeping) "Despertará hacia las $hora." else "Si la duermes ahora, despertará hacia las $hora.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

private fun formatearHoraDespertar(despertar: Long): String = try {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(despertar))
} catch (_: Exception) {
    ""
}

@Composable
private fun ChipRequisito(r: RequisitoSuper, modifier: Modifier = Modifier) {
    val color = if (r.cumplido) PetPalette.NeonGold else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (r.cumplido) 0.16f else 0.08f))
            .border(1.dp, color.copy(alpha = if (r.cumplido) 0.65f else 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(r.estadistica.titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.size(1.dp))
        Text("${r.valor}", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

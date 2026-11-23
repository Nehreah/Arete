package com.renea.arete_social.gui.tamagotchi

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.renea.arete_social.feature.tamagotchi.logic.nombreMascotaValido
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarTextField

/** Contenedor común de los diálogos: tarjeta del tema con título y subtítulo. */
@Composable
private fun PetDialogCard(
    titulo: String,
    subtitulo: String,
    onDismiss: () -> Unit,
    contenido: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val forma = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(forma)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), forma)
                .padding(20.dp)
        ) {
            Text(titulo, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            contenido()
        }
    }
}

/** Elegir qué darle de comer. La chispa social solo se puede usar si hay chispas. */
@Composable
fun FoodSelectorDialog(
    chispasDisponibles: Int,
    onSelectFood: (FoodType) -> Unit,
    onDismiss: () -> Unit
) {
    PetDialogCard("¿Qué le das de comer?", "Elige una opción", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FoodType.entries.forEach { food ->
                val usable = !food.costsSpark || chispasDisponibles > 0
                FoodRow(food, chispasDisponibles, usable) { onSelectFood(food) }
            }
            RadarOutlinedButton("Cancelar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FoodRow(food: FoodType, chispas: Int, usable: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(16.dp)
    val alfa = if (usable) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(scheme.background)
            .border(1.dp, scheme.outline.copy(alpha = 0.45f), forma)
            .clickable(enabled = usable, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background((if (food.costsSpark) PetPalette.Mood else PetPalette.Hunger).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (food.costsSpark) PetIcons.Spark(Modifier.size(20.dp), PetPalette.Mood)
            else PetIcons.Feed(Modifier.size(20.dp), PetPalette.Hunger)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(food.title, color = scheme.onSurface.copy(alpha = alfa), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(food.desc, color = scheme.onSurfaceVariant.copy(alpha = alfa), fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (food.costsSpark) {
                    if (chispas > 0) "Cuesta 1 chispa · tienes $chispas" else "Sin chispas: se ganan con amigos"
                } else "Gratis",
                color = if (food.costsSpark) PetPalette.Mood.copy(alpha = alfa) else scheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Cambiar el nombre de la mascota. */
@Composable
fun RenombrarMascotaDialog(
    nombreActual: String,
    onGuardar: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var texto by rememberSaveable { mutableStateOf(nombreActual) }
    val valido = nombreMascotaValido(texto) != null
    PetDialogCard("Cambiar nombre", "Hasta ${PetBalance.NAME_MAX_LENGTH} letras", onDismiss) {
        RadarTextField(
            valor = texto,
            onValorCambia = { texto = it.take(PetBalance.NAME_MAX_LENGTH) },
            etiqueta = "Nombre",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        RadarButton(
            texto = "Guardar",
            onClick = { onGuardar(texto) },
            habilitado = valido,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        RadarOutlinedButton("Cancelar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** Resumen que aparece cuando la mascota llega al 100 % de energía. */
@Composable
fun PetReportDialog(report: String, onDismiss: () -> Unit) {
    PetDialogCard("Resumen de tu mascota", "Lo que hay cerca de ti", onDismiss) {
        Text(report, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        RadarButton("¡Entendido!", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

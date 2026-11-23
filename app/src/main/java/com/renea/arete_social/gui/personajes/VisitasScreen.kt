package com.renea.arete_social.gui.personajes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla del centro de visitas y vínculos entre personajes.
 */
@Composable
fun VisitasScreen(
    amigosIds: List<String>,
    relaciones: List<Relacion>,
    recuerdos: List<Recuerdo>,
    onSolicitarVisita: (String) -> Unit,
    onRegresar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyberCyan = Color(0xFF00E5FF)
    val cyberTeal = Color(0xFF00695C)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0A0E17)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRegresar) {
                    Text("←", color = cyberCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "VISITAS Y RECUERDOS",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mundo Social Mesh // Solo Amigos",
                        color = cyberCyan,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sección 1: Amigos para visitar
                item {
                    Text(
                        text = "AMISTADES PARA VISITAR (${amigosIds.size})",
                        color = cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (amigosIds.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Aún no tienes amistades agregadas. Agrega amigos para visitar sus casas.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(amigosIds) { amigoId ->
                        val rel = relaciones.find { it.otroOwnerId == amigoId }
                        AmigoVisitaCard(
                            amigoId = amigoId,
                            relacion = rel,
                            onVisitar = { onSolicitarVisita(amigoId) }
                        )
                    }
                }

                // Sección 2: Libro de recuerdos
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "LIBRO DE RECUERDOS (${recuerdos.size})",
                        color = Color(0xFFFFD600),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (recuerdos.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Tus visitas generarán recuerdos firmados que aparecerán aquí.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(recuerdos) { recuerdo ->
                        RecuerdoItemCard(recuerdo = recuerdo)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmigoVisitaCard(
    amigoId: String,
    relacion: Relacion?,
    onVisitar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relacion?.nombreOtro ?: "Amigo ${amigoId.take(8)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("❤️", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Vínculo: ${relacion?.nivelVinculo?.titulo ?: "Conocido"} (${relacion?.confianza ?: 0} pts)",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = onVisitar,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🏠", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("Visitar", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RecuerdoItemCard(recuerdo: Recuerdo) {
    val formatoFecha = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val fechaStr = formatoFecha.format(Date(recuerdo.fechaMillis))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F2E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recuerdo.tipo.titulo,
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = fechaStr,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = recuerdo.textoCorto,
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lugar: ${recuerdo.lugar} // Con: ${recuerdo.conQuienNombre}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
                if (recuerdo.firmaMutua != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 10.sp)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "Recibo sellado",
                            color = Color(0xFF00FF9D),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

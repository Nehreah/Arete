package com.renea.arete_social.gui.personajes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.personajes.logic.MotorCasa
import com.renea.arete_social.feature.personajes.logic.ValidadorTextoSocial
import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.escena.CatalogoObjetosCasa
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import com.renea.arete_social.feature.personajes.model.visita.VisitaResultado

/**
 * Pantalla de visualización en modo lectura de la casa de un amigo visitado.
 */
@Composable
fun CasaVisitaScreen(
    resultadoVisita: VisitaResultado,
    onCompletarVisita: (accion: AccionObjeto, objetoCatalogoId: String?, nota: String?) -> Unit,
    onRegresar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyberCyan = Color(0xFF00E5FF)
    val cyberTeal = Color(0xFF00695C)

    var objetoSeleccionado by remember { mutableStateOf<ObjetoColocado?>(null) }
    var textoNota by remember { mutableStateOf("") }
    var errorNota by remember { mutableStateOf<String?>(null) }
    var accionElegida by remember { mutableStateOf(AccionObjeto.JUGAR) }

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
                        text = "VISITANDO A ${resultadoVisita.anfitrionNombre.uppercase()}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Modo Visitante (Solo Lectura) // Casa Autoritativa",
                        color = cyberCyan,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cuadrícula 8x8 de la casa del amigo
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, cyberTeal, RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF161E2E), Color(0xFF0B101B))
                        )
                    )
            ) {
                val cellSize = maxWidth / MotorCasa.GRID_TAMANO

                // Celdas de la cuadrícula
                for (x in 0 until MotorCasa.GRID_TAMANO) {
                    for (y in 0 until MotorCasa.GRID_TAMANO) {
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .align(Alignment.TopStart)
                                .padding(start = cellSize * x, top = cellSize * y)
                                .border(0.5.dp, Color(0x1A00E5FF))
                        )
                    }
                }

                // Objetos del anfitrión
                for (obj in resultadoVisita.casaSnapshot.objetos) {
                    val def = CatalogoObjetosCasa.obtenerPorId(obj.catalogoId) ?: continue
                    val anchoPx = cellSize * def.anchoTiles
                    val altoPx = cellSize * def.altoTiles

                    val esSeleccionado = objetoSeleccionado?.instanciaId == obj.instanciaId

                    Box(
                        modifier = Modifier
                            .size(width = anchoPx, height = altoPx)
                            .align(Alignment.TopStart)
                            .padding(start = cellSize * obj.x + 2.dp, top = cellSize * obj.y + 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (esSeleccionado) Color(0xFF00695C)
                                else Color(0xFF1A2436)
                            )
                            .border(
                                width = if (esSeleccionado) 2.dp else 1.dp,
                                color = if (esSeleccionado) Color(0xFFFFD600) else cyberCyan.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                objetoSeleccionado = obj
                                accionElegida = def.acciones.firstOrNull() ?: AccionObjeto.JUGAR
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = def.nombre,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Panel de Interacción y Recibo
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "DEJAR NOTA O SALUDO",
                        color = cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = textoNota,
                        onValueChange = {
                            textoNota = it
                            errorNota = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe un mensaje amistoso (máx 120 caracteres)...", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cyberCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    if (errorNota != null) {
                        Text(
                            text = errorNota.orEmpty(),
                            color = Color(0xFFFF5252),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val valRes = if (textoNota.isNotBlank()) {
                                ValidadorTextoSocial.validar(textoNota)
                            } else null

                            if (valRes != null && !valRes.esValido) {
                                errorNota = valRes.mensajeError
                                return@Button
                            }

                            val notaFinal = valRes?.textoLimpio?.takeIf { it.isNotBlank() } ?: "¡Visité tu casa y la pasé genial!"
                            onCompletarVisita(
                                accionElegida,
                                objetoSeleccionado?.catalogoId,
                                notaFinal
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cyberTeal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("Sellar Visita y Guardar Recuerdo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

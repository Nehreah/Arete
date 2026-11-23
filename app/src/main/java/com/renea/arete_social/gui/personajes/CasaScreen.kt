package com.renea.arete_social.gui.personajes

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import com.renea.arete_social.feature.personajes.model.escena.CatalogoObjetosCasa
import com.renea.arete_social.feature.personajes.model.escena.ObjetoCatalogo
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette
import java.util.UUID

/**
 * Pantalla interactiva de la Casa de Michi (Fase 2).
 *
 * Permite:
 * 1. Convivencia: Interactuar con los objetos colocados para mejorar stats de Michi.
 * 2. Edición: Colocar nuevos objetos del catálogo (máx 16) o reorganizar la habitación 8x8.
 */
@Composable
fun CasaScreen(
    pet: PetEntity,
    casaInicial: CasaSnapshot,
    onGuardarCasa: (CasaSnapshot) -> Unit,
    onActualizarPet: (PetEntity) -> Unit,
    onRegresar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var casaActual by remember { mutableStateOf(casaInicial) }
    var modoEditor by remember { mutableStateOf(false) }
    var mensajeInteraccion by remember { mutableStateOf<String?>(null) }
    var objetoSeleccionadoParaAccion by remember { mutableStateOf<ObjetoColocado?>(null) }
    var errorColocacion by remember { mutableStateOf<String?>(null) }

    val cyberCyan = Color(0xFF00E5FF)
    val cyberTeal = Color(0xFF00695C)
    val cyberGold = PetPalette.NeonGold

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0A0E17)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // ── Barra Superior ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRegresar) {
                        Text("←", color = cyberCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "CASA DE ${pet.name.uppercase()}",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (modoEditor) "Modo Decorador" else "Modo Convivencia",
                            color = if (modoEditor) cyberGold else cyberCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        if (modoEditor) {
                            onGuardarCasa(casaActual)
                        }
                        modoEditor = !modoEditor
                        objetoSeleccionadoParaAccion = null
                        errorColocacion = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (modoEditor) cyberGold else cyberTeal
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (modoEditor) "✓" else "🛠",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (modoEditor) "Guardar" else "Decorar",
                        color = if (modoEditor) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── HUD Rápido de Estado de Michi ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF131A29))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBadge("Energía", "${pet.energy}%", Color(0xFF00E5FF))
                StatBadge("Comida", "${pet.hunger}%", Color(0xFFFFD600))
                StatBadge("Higiene", "${pet.hygiene}%", Color(0xFF00FF9D))
                StatBadge("Ánimo", "${pet.happiness}%", Color(0xFFFF5252))
            }

            // Mensajes informativos / errores
            AnimatedVisibility(visible = mensajeInteraccion != null) {
                Text(
                    text = mensajeInteraccion.orEmpty(),
                    color = cyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            AnimatedVisibility(visible = errorColocacion != null) {
                Text(
                    text = errorColocacion.orEmpty(),
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Cuadrícula 8x8 de la Casa ────────────────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, if (modoEditor) cyberGold else cyberTeal, RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF161E2E), Color(0xFF0B101B))
                        )
                    )
            ) {
                val cellSize = maxWidth / MotorCasa.GRID_TAMANO

                // Líneas de la cuadrícula
                for (x in 0 until MotorCasa.GRID_TAMANO) {
                    for (y in 0 until MotorCasa.GRID_TAMANO) {
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .align(Alignment.TopStart)
                                .padding(
                                    start = cellSize * x,
                                    top = cellSize * y
                                )
                                .border(0.5.dp, Color(0x1A00E5FF))
                        )
                    }
                }

                // Objetos colocados en la habitación
                for (obj in casaActual.objetos) {
                    val def = CatalogoObjetosCasa.obtenerPorId(obj.catalogoId) ?: continue
                    val anchoPx = cellSize * def.anchoTiles
                    val altoPx = cellSize * def.altoTiles

                    Box(
                        modifier = Modifier
                            .size(width = anchoPx, height = altoPx)
                            .align(Alignment.TopStart)
                            .padding(
                                start = cellSize * obj.x + 2.dp,
                                top = cellSize * obj.y + 2.dp
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1F2B42), Color(0xFF141C2B))
                                )
                            )
                            .border(1.5.dp, cyberCyan.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (modoEditor) {
                                    // En modo editor, quitar el objeto para reubicarlo o retirarlo
                                    casaActual = casaActual.copy(
                                        objetos = casaActual.objetos.filter { it.instanciaId != obj.instanciaId }
                                    )
                                    errorColocacion = "Objeto retirado de la habitación."
                                } else {
                                    objetoSeleccionadoParaAccion = obj
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = def.nombre,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            if (modoEditor) {
                                Text(
                                    text = "Quitar [X]",
                                    color = Color(0xFFFF5252),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Panel Inferior Dinámico ──────────────────────────────────────
            if (modoEditor) {
                // Catálogo de objetos para agregar
                Text(
                    text = "CATÁLOGO DECORATIVO (${casaActual.objetos.size}/${MotorCasa.MAX_OBJETOS} Objetos)",
                    color = cyberGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CatalogoObjetosCasa.todos()) { item ->
                        ItemCatalogoCard(
                            item = item,
                            onAgregar = {
                                val primerLugar = encontrarPrimerLugarLibre(casaActual.objetos, item)
                                if (primerLugar == null) {
                                    errorColocacion = "No hay espacio suficiente para '${item.nombre}'."
                                } else {
                                    val nuevo = ObjetoColocado(
                                        instanciaId = UUID.randomUUID().toString(),
                                        catalogoId = item.id,
                                        x = primerLugar.first,
                                        y = primerLugar.second
                                    )
                                    val valRes = MotorCasa.validarColocacion(casaActual.objetos, nuevo)
                                    if (valRes.exito) {
                                        casaActual = casaActual.copy(objetos = casaActual.objetos + nuevo)
                                        errorColocacion = null
                                    } else {
                                        errorColocacion = valRes.motivo
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Acciones del objeto seleccionado en modo convivencia
                val sel = objetoSeleccionadoParaAccion
                if (sel != null) {
                    val def = CatalogoObjetosCasa.obtenerPorId(sel.catalogoId)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = def?.nombre ?: "Objeto",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { objetoSeleccionadoParaAccion = null }) {
                                    Text("✕", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                            Text(
                                text = def?.descripcion.orEmpty(),
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                def?.acciones?.forEach { accion ->
                                    Button(
                                        onClick = {
                                            val (petActualizada, msg) = MotorCasa.ejecutarAccionObjeto(pet, accion)
                                            onActualizarPet(petActualizada)
                                            mensajeInteraccion = msg
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = cyberTeal),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(accion.titulo, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Toca un mueble para que Michi lo use, o pulsa 'Decorar' para personalizar tu habitación.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(nombre: String, valor: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(nombre, fontSize = 10.sp, color = Color.Gray)
        Text(valor, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ItemCatalogoCard(
    item: ObjetoCatalogo,
    onAgregar: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onAgregar),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F2E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.nombre,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.anchoTiles}x${item.altoTiles} celdas",
                color = Color.Gray,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "+ Añadir",
                color = Color(0xFF00E5FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun encontrarPrimerLugarLibre(
    objetosExistentes: List<ObjetoColocado>,
    item: ObjetoCatalogo
): Pair<Int, Int>? {
    for (y in 0 until MotorCasa.GRID_TAMANO) {
        for (x in 0 until MotorCasa.GRID_TAMANO) {
            val candidato = ObjetoColocado(
                instanciaId = "test_lugar",
                catalogoId = item.id,
                x = x,
                y = y
            )
            if (MotorCasa.validarColocacion(objetosExistentes, candidato, ignorarInstanciaId = "test_lugar").exito) {
                return Pair(x, y)
            }
        }
    }
    return null
}

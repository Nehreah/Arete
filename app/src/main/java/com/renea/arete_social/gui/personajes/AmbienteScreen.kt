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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import com.renea.arete_social.feature.personajes.logic.MotorAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.AccionAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.CatalogoAmbientes
import com.renea.arete_social.feature.personajes.model.ambiente.PersonajeEnAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.TipoAmbiente
import com.renea.arete_social.feature.personajes.model.escena.CoordenadaTile

/**
 * Pantalla principal del Ambiente Compartido (Parque, Gimnasio, Cafetería).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbienteScreen(
    motor: MotorAmbiente,
    onVolver: () -> Unit,
    onCambiarAmbiente: ((TipoAmbiente) -> Unit)? = null,
    onBloquearUsuario: ((String) -> Unit)? = null
) {
    var motorActual by remember { mutableStateOf(motor) }
    var personajeSeleccionado by remember { mutableStateOf<PersonajeEnAmbiente?>(null) }
    var menuSeguridadVisible by remember { mutableStateOf(false) }
    var mensajeFeedback by remember { mutableStateOf<String?>(null) }

    val participantes = motorActual.participantes
    val miOwnerId = motorActual.miPersonaje.ownerId
    val definicion = motorActual.definicion

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B132B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Barra Superior ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    motorActual.salir()
                    onVolver()
                }) {
                    Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = definicion.nombre,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Aforo: ${participantes.size}/${definicion.aforoMaximo} personajes",
                        fontSize = 12.sp,
                        color = if (participantes.size >= definicion.aforoMaximo) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                }

                Box {
                    IconButton(onClick = { menuSeguridadVisible = true }) {
                        Text("⋮", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = menuSeguridadVisible,
                        onDismissRequest = { menuSeguridadVisible = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🚪 Salir de la sala") },
                            onClick = {
                                menuSeguridadVisible = false
                                motorActual.salir()
                                onVolver()
                            }
                        )
                        personajeSeleccionado?.let { target ->
                            if (target.ownerId != miOwnerId) {
                                val estaSilenciado = motorActual.estaSilenciado(target.ownerId)
                                DropdownMenuItem(
                                    text = { Text(if (estaSilenciado) "🔊 Desilenciar a ${target.nombre}" else "🔇 Silenciar a ${target.nombre}") },
                                    onClick = {
                                        menuSeguridadVisible = false
                                        if (estaSilenciado) {
                                            motorActual.desilenciar(target.ownerId)
                                            mensajeFeedback = "Has desilenciado a ${target.nombre}"
                                        } else {
                                            motorActual.silenciar(target.ownerId)
                                            mensajeFeedback = "Has silenciado a ${target.nombre}"
                                        }
                                    }
                                )
                                onBloquearUsuario?.let { bloquear ->
                                    DropdownMenuItem(
                                        text = { Text("⛔ Bloquear a ${target.nombre}", color = Color.Red) },
                                        onClick = {
                                            menuSeguridadVisible = false
                                            bloquear(target.ownerId)
                                            mensajeFeedback = "${target.nombre} ha sido bloqueado"
                                            motorActual.salir()
                                            onVolver()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Selector de Ambientes ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CatalogoAmbientes.TODOS.forEach { amb ->
                    val esActual = amb.tipo == definicion.tipo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (esActual) Color(0xFF3B82F6) else Color(0xFF1E293B))
                            .clickable(enabled = !esActual && onCambiarAmbiente != null) {
                                onCambiarAmbiente?.invoke(amb.tipo)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = amb.nombre.split(" ").first(),
                            fontSize = 12.sp,
                            fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                            color = if (esActual) Color.White else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Feedback Temporal ─────────────────────────────────────────────
            mensajeFeedback?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { mensajeFeedback = null }, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Cuadrícula Escénica del Ambiente ──────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(obtenerColorAmbiente(definicion.tipo))
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tileWidth = maxWidth / definicion.anchoTiles
                    val tileHeight = maxHeight / definicion.altoTiles

                    // Dibujar objetos fijos del catálogo
                    definicion.objetosFijos.forEach { obj ->
                        Box(
                            modifier = Modifier
                                .size(tileWidth, tileHeight)
                                .align(Alignment.TopStart)
                                .padding(
                                    start = tileWidth * obj.x,
                                    top = tileHeight * obj.y
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = obtenerEmojiObjeto(obj.catalogoId),
                                fontSize = (tileWidth.value * 0.5f).sp
                            )
                        }
                    }

                    // Dibujar personajes presentes
                    participantes.forEach { p ->
                        val esMio = p.ownerId == miOwnerId
                        val estaSeleccionado = p.ownerId == personajeSeleccionado?.ownerId
                        val silenciado = motorActual.estaSilenciado(p.ownerId)

                        Box(
                            modifier = Modifier
                                .size(tileWidth, tileHeight)
                                .align(Alignment.TopStart)
                                .padding(
                                    start = tileWidth * p.posicion.x,
                                    top = tileHeight * p.posicion.y
                                )
                                .clickable {
                                    personajeSeleccionado = if (estaSeleccionado) null else p
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(tileWidth * 0.75f)
                                        .clip(CircleShape)
                                        .background(if (esMio) Color(0xFF10B981) else if (silenciado) Color.DarkGray else Color(0xFF6366F1))
                                        .border(
                                            width = if (estaSeleccionado) 2.dp else 1.dp,
                                            color = if (estaSeleccionado) Color(0xFFF59E0B) else Color.White,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (silenciado) "🔇" else if (esMio) "🐱" else "🐾",
                                        fontSize = (tileWidth.value * 0.35f).sp
                                    )
                                }
                                Text(
                                    text = p.nombre.take(6),
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Panel Inferior de Actividades y Moderación ────────────────────
            personajeSeleccionado?.let { seleccionado ->
                if (seleccionado.ownerId != miOwnerId) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Amigo: ${seleccionado.nombre}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Temperamento: ${seleccionado.temperamento.name}",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }
                                IconButton(onClick = { personajeSeleccionado = null }) {
                                    Text("✕", color = Color.Gray, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Hacer actividad compartida juntos:",
                                fontSize = 12.sp,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(definicion.accionesDisponibles) { accion ->
                                    Button(
                                        onClick = {
                                            val res = motorActual.iniciarActividadConjunta(seleccionado.ownerId, accion)
                                            mensajeFeedback = if (res != null) {
                                                "¡Actividad '${accion.titulo}' completada con ${seleccionado.nombre}!"
                                            } else {
                                                "No se pudo realizar la actividad (límite diario alcanzado)."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                    ) {
                                        Text(text = accion.titulo, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Acciones individuales para el personaje propio
                    PanelAccionesIndividuales(motorActual, definicion.accionesDisponibles) { nuevaAccion ->
                        motorActual.cambiarAccion(nuevaAccion)
                        mensajeFeedback = "Estás realizando: ${nuevaAccion.titulo}"
                    }
                }
            } ?: run {
                PanelAccionesIndividuales(motorActual, definicion.accionesDisponibles) { nuevaAccion ->
                    motorActual.cambiarAccion(nuevaAccion)
                    mensajeFeedback = "Estás realizando: ${nuevaAccion.titulo}"
                }
            }
        }
    }
}

@Composable
private fun PanelAccionesIndividuales(
    motor: MotorAmbiente,
    acciones: List<AccionAmbiente>,
    onAccionSeleccionada: (AccionAmbiente) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Actividades en este espacio:",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(acciones) { accion ->
                    val esActual = motor.miPersonaje.accionActual == accion
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (esActual) Color(0xFF10B981) else Color(0xFF334155))
                            .clickable { onAccionSeleccionada(accion) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = accion.titulo,
                            fontSize = 11.sp,
                            fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun obtenerColorAmbiente(tipo: TipoAmbiente): Brush = when (tipo) {
    TipoAmbiente.PARQUE -> Brush.verticalGradient(listOf(Color(0xFF1B4332), Color(0xFF2D6A4F)))
    TipoAmbiente.GIMNASIO -> Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF334155)))
    TipoAmbiente.CAFETERIA -> Brush.verticalGradient(listOf(Color(0xFF3E2723), Color(0xFF4E342E)))
}

private fun obtenerEmojiObjeto(catId: String): String = when (catId) {
    "mueble_banca" -> "🪑"
    "dec_arbol" -> "🌳"
    "dec_fuente" -> "⛲"
    "gym_cinta" -> "🏃"
    "gym_pesas" -> "🏋️"
    "gym_colchoneta" -> "🧘"
    "cafe_barra" -> "☕"
    "cafe_mesa" -> "🪑"
    "cafe_maquina" -> "🍵"
    else -> "📦"
}

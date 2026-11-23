package com.renea.arete_social.gui.personajes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajesLocal
import com.renea.arete_social.feature.personajes.logic.GestorEventosMundo
import com.renea.arete_social.feature.personajes.logic.GestorLogrosSociales
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.logros.LogroSocial

/**
 * Hub central del mundo ficticio de las mascotas.
 *
 * Reúne de forma organizada:
 *  - Evento temporal rotativo activo (con multiplicadores de confianza).
 *  - Accesos rápidos a Mi Casa, Visitas y Ambientes Compartidos (Parque, Gimnasio, Cafetería).
 *  - Vitrina de logros sociales alcanzados por la mascota.
 *  - Lista de amistades y niveles de vínculo.
 */
@Composable
fun MundoSocialScreen(
    onVolver: () -> Unit,
    onAbrirCasa: () -> Unit,
    onAbrirVisitas: () -> Unit,
    onAbrirAmbiente: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repo = remember(context) { RepositorioPersonajesLocal(context) }
    val gestorLogros = remember(context, repo) { GestorLogrosSociales(context = context, repoPersonajes = repo) }

    val eventoActivo = remember { GestorEventosMundo.eventoActivo() }
    val logros = remember { gestorLogros.evaluarLogros() }
    val relaciones = remember { repo.obtenerRelaciones() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ── Cabecera ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVolver) {
                    Text("←", color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "MUNDO SOCIAL",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Espacios, visitas y eventos de tu mascota",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Banner del Evento Temporal Activo ─────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF7C3AED), Color(0xFF2563EB))
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "⚡ EVENTO MUNDIAL ACTIVO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDE047)
                                    )
                                    Text(
                                        text = "+${((eventoActivo.multiplicadorConfianza - 1f) * 100).toInt()}% Confianza",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = eventoActivo.titulo,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = eventoActivo.descripcion,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // ── Accesos a los Espacios de la Mascota ───────────────────────
                item {
                    Text(
                        text = "ESPACIOS DEL PERSONAJE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BotonEspacio(
                            titulo = "Mi Casa",
                            subtitulo = "Decorar y convivir",
                            icono = "🏠",
                            colorBorde = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = onAbrirCasa
                        )
                        BotonEspacio(
                            titulo = "Visitar",
                            subtitulo = "Casas de amigos",
                            icono = "🐾",
                            colorBorde = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f),
                            onClick = onAbrirVisitas
                        )
                        BotonEspacio(
                            titulo = "Ambientes",
                            subtitulo = "Parque y más",
                            icono = "🌳",
                            colorBorde = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f),
                            onClick = onAbrirAmbiente
                        )
                    }
                }

                // ── Vitrina de Logros Sociales ────────────────────────────────
                item {
                    val desbloqueadosCount = logros.count { it.desbloqueado }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LOGROS Y TROFEOS SOCIALES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$desbloqueadosCount de ${logros.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(logros) { logro ->
                            TarjetaLogro(logro)
                        }
                    }
                }

                // ── Amigos y Niveles de Vínculo ───────────────────────────────
                item {
                    Text(
                        text = "VÍNCULOS SOCIALES (${relaciones.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(8.dp))

                    if (relaciones.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Aún no tienes vínculos sociales. ¡Visita a amigos o comparte tiempo en el Parque para forjar amistades!",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            relaciones.forEach { rel ->
                                TarjetaVinculoAmigo(rel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonEspacio(
    titulo: String,
    subtitulo: String,
    icono: String,
    colorBorde: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, colorBorde.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icono, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = titulo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitulo,
                fontSize = 9.sp,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TarjetaLogro(logro: LogroSocial) {
    val fondo = if (logro.desbloqueado) Color(0xFF1E293B) else Color(0xFF161F2E)
    val borde = if (logro.desbloqueado) Color(0xFFF59E0B) else Color(0xFF334155)

    Card(
        modifier = Modifier
            .width(130.dp)
            .border(1.dp, borde, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = fondo),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (logro.desbloqueado) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (logro.desbloqueado) logro.icono else "🔒",
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = logro.titulo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (logro.desbloqueado) Color.White else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = logro.descripcion,
                fontSize = 9.sp,
                color = Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun TarjetaVinculoAmigo(relacion: Relacion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relacion.nombreOtro,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${relacion.nivelVinculo.titulo} · ${relacion.confianza} pts de confianza",
                    fontSize = 11.sp,
                    color = Color(0xFF10B981)
                )
            }
            Text(
                text = "${relacion.interaccionesTotales} actividades",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

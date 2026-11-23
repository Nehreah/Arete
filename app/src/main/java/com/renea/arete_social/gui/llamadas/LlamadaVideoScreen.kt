package com.renea.arete_social.gui.llamadas

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.EstadoLlamada
import com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio

/**
 * Pantalla completa de Llamada de Voz / Audio Persona a Persona (1 a 1).
 * Transmisión dúplex de audio continuo en tiempo real sobre UDP WiFi / Mesh.
 */
@Composable
fun LlamadaAudioScreen(
    gestorLlamadas: GestorLlamadasYAudio,
    nombreContacto: String,
    fotoContacto: String?,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val llamada by gestorLlamadas.llamadaActual.collectAsState()
    var altavozActivo by remember { mutableStateOf(gestorLlamadas.esAltavozActivo()) }

    val duracion = llamada?.duracionSegundos ?: 0
    val minutos = duracion / 60
    val segundos = duracion % 60
    val textoTiempo = String.format("%02d:%02d", minutos, segundos)

    val microSilenciado = llamada?.microfonoSilenciado ?: false
    val enLlamada = llamada?.estado == EstadoLlamada.EN_LLAMADA

    // Animación de pulso táctico alrededor del avatar
    val transition = rememberInfiniteTransition(label = "pulse_audio")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (enLlamada) 1.25f else 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ====================================================
        // FONDO RADAR Y EFECTO DE LÍNEAS CRT
        // ====================================================
        EfectoRuidoSenal(modifier = Modifier.fillMaxSize())

        // ====================================================
        // HUD SUPERIOR: INFORMACIÓN DE ENLACE Y ESTADO
        // ====================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.90f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador E2EE
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒 AUDIO 1-A-1 CIFRADO", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Calidad de Señal
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (enLlamada) "ENLACE ACTIVO" else "CONECTANDO...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when (llamada?.estado) {
                    EstadoLlamada.LLAMANDO -> "LLAMANDO..."
                    EstadoLlamada.ENTRANTE -> "LLAMADA ENTRANTE..."
                    EstadoLlamada.FINALIZADA -> "LLAMADA FINALIZADA"
                    EstadoLlamada.EN_LLAMADA -> textoTiempo
                    else -> "CONECTANDO..."
                },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        // ====================================================
        // CENTRO: AVATAR Y DATOS DEL CONTACTO
        // ====================================================
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Anillos de pulso de audio
                Box(
                    modifier = Modifier
                        .size(140.dp * pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(125.dp * (pulseScale * 0.95f))
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 2f), CircleShape)
                )

                // Avatar
                if (!fotoContacto.isNullOrBlank()) {
                    AsyncImage(
                        model = fotoContacto,
                        contentDescription = "Foto de $nombreContacto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nombreContacto.firstOrNull()?.uppercase() ?: "P",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = nombreContacto.uppercase(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (enLlamada) "Voz en vivo dúplex" else "Esperando conexión con el contacto...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        // ====================================================
        // BARRA INFERIOR DE CONTROLES
        // ====================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (llamada?.estado == EstadoLlamada.ENTRANTE) {
                // Botón Aceptar (Verde Neón)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(16.dp, CircleShape, ambientColor = Color(0xFF00E676), spotColor = Color(0xFF00E676))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Color(0xFF00E676), Color(0xFF00A844)))
                        )
                        .border(2.dp, Color(0xFF69F0AE), CircleShape)
                        .clickable { gestorLlamadas.contestarLlamada() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📞", fontSize = 28.sp)
                }
            } else {
                // Silenciar micrófono
                BotonControlLlamada(
                    icono = if (microSilenciado) "🔇" else "🎙️",
                    etiqueta = if (microSilenciado) "DESILENCIAR" else "SILENCIAR",
                    activo = !microSilenciado,
                    onClick = { gestorLlamadas.toggleMicrofonoLlamada() }
                )
            }

            // Botón Colgar (Rojo Neón)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(16.dp, CircleShape, ambientColor = Color.Red, spotColor = Color.Red)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFFF2A4B), Color(0xFFB3001E)))
                    )
                    .border(2.dp, Color(0xFFFF6B81), CircleShape)
                    .clickable {
                        gestorLlamadas.colgarLlamada()
                        onCerrar()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("📞", fontSize = 28.sp)
            }

            // Alternar Altavoz
            BotonControlLlamada(
                icono = if (altavozActivo) "🔊" else "🔈",
                etiqueta = if (altavozActivo) "ALTAVOZ" else "AURICULAR",
                activo = altavozActivo,
                onClick = { altavozActivo = gestorLlamadas.toggleAltavoz() }
            )
        }
    }
}

/** Alias para retrocompatibilidad */
@Composable
fun LlamadaVideoScreen(
    gestorLlamadas: GestorLlamadasYAudio,
    nombreContacto: String,
    fotoContacto: String?,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    LlamadaAudioScreen(
        gestorLlamadas = gestorLlamadas,
        nombreContacto = nombreContacto,
        fotoContacto = fotoContacto,
        onCerrar = onCerrar,
        modifier = modifier
    )
}

@Composable
private fun BotonControlLlamada(
    icono: String,
    etiqueta: String,
    activo: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (activo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icono, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = etiqueta,
            color = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Efecto animado de escaneo y líneas analógicas CRT.
 */
@Composable
fun EfectoRuidoSenal(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "crt_noise")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "line_offset"
    )

    Canvas(modifier = modifier) {
        val espaciado = 6.dp.toPx()
        val numLineas = (size.height / espaciado).toInt()
        val desfasaje = lineOffset * espaciado

        for (i in 0..numLineas) {
            val y = i * espaciado + desfasaje
            drawLine(
                color = Color.Black.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

package com.renea.arete_social.gui.mapa.osm

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.renea.arete_social.gui.mapa.osm.EstadoDescargaMapa
import com.renea.arete_social.gui.mapa.osm.GestorDescargaMapaOffline
import com.renea.arete_social.gui.theme.LocalRadarColors
import org.osmdroid.util.GeoPoint

/**
 * Diálogo interactivo para descargar y guardar en memoria no volátil
 * un radio de mapa (OpenStreetMap) para uso completamente sin internet.
 */
@Composable
fun DialogoDescargaMapaOffline(
    gestor: GestorDescargaMapaOffline,
    centroActual: GeoPoint,
    onDismiss: () -> Unit
) {
    val radarColors = LocalRadarColors.current
    val estado by gestor.estado.collectAsState()

    var radioKm by remember { mutableDoubleStateOf(3.0) }
    var teselasEstimadas by remember { mutableIntStateOf(0) }

    LaunchedEffect(radioKm, centroActual) {
        val caja = gestor.calcularCajaRadio(centroActual, radioKm)
        teselasEstimadas = gestor.estimarTeselas(caja, zoomMin = 13, zoomMax = 16)
    }

    Dialog(onDismissRequest = {
        if (estado !is EstadoDescargaMapa.EnProgreso) {
            gestor.limpiarEstado()
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = radarColors.panel,
            border = androidx.compose.foundation.BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🗺️ Descargar Mapa Offline",
                    color = radarColors.verdeBrillante,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Guarda las calles y caminos de esta zona en tu teléfono para navegar y usar el radar sin conexión a internet.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (val est = estado) {
                    is EstadoDescargaMapa.Inactivo -> {
                        Text(
                            text = "Radio de cobertura alrededor:",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1.5 to "1.5 km", 3.0 to "3 km", 6.0 to "6 km").forEach { (km, label) ->
                                val seleccionado = radioKm == km
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (seleccionado) radarColors.verde.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            width = 1.dp,
                                            color = if (seleccionado) radarColors.verdeBrillante else Color.White.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { radioKm = km }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (seleccionado) radarColors.verdeBrillante else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Teselas aproximadas: ~$teselasEstimadas (~${(teselasEstimadas * 0.02).toInt().coerceAtLeast(1)} MB)",
                            color = radarColors.verde,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val caja = gestor.calcularCajaRadio(centroActual, radioKm)
                                    gestor.iniciarDescarga(caja, zoomMin = 13, zoomMax = 16)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = radarColors.verde,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Descargar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is EstadoDescargaMapa.EnProgreso -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { est.porcentaje / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = radarColors.verdeBrillante,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${est.porcentaje}% descargado (${est.teselasDescargadas} / ${est.totalTeselas} teselas)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { gestor.cancelarDescarga() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.8f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Detener Descarga")
                        }
                    }

                    is EstadoDescargaMapa.Completado -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "✅ ¡Descarga completada!",
                            color = radarColors.verdeBrillante,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Se guardaron ${est.totalDescargadas} teselas permanentemente. Tu mapa funcionará aunque no tengas señal ni datos.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                gestor.limpiarEstado()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = radarColors.verde,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Listo", fontWeight = FontWeight.Bold)
                        }
                    }

                    is EstadoDescargaMapa.Error -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "❌ Error en la descarga",
                            color = Color.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = est.mensaje,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { gestor.limpiarEstado() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = radarColors.verde,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }
        }
    }
}

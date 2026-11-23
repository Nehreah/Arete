package com.renea.arete_social.gui.auth.perfil

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.model.CatalogoIntereses
import com.renea.arete_social.domain.model.CategoriaInteres
import com.renea.arete_social.data.local.perfil.GestorInteresesUsuario

/**
 * Componente modular para el Perfil que muestra la encuesta de gustos e intereses:
 * - Selección de categorías principales ("Fiestas", "Libros", "Deporte", "Tecnología", etc.).
 * - Selección de subcategorías específicas (ej: tipos de fiestas, tipos de deportes).
 * - Entrada libre para agregar gustos personalizados adicionales.
 * - Michi usa estas preferencias para avisar al usuario de eventos y personas cercanas afines.
 */
@Composable
fun SeccionInteresesUsuario(
    modifier: Modifier = Modifier
) {
    val perfilIntereses by GestorInteresesUsuario.intereses.collectAsState()
    var categoriaDesplegadaId by remember { mutableStateOf<String?>(null) }
    var nuevoGustoTexto by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Cabecera
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("🎯", fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "ENCUESTA DE GUSTOS E INTERESES",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Michi te avisará de eventos y personas afines a ti",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Lista de Categorías en formato de Chips / Tarjetas
        CatalogoIntereses.categorias.forEach { categoria ->
            val isSelected = perfilIntereses.categoriasSeleccionadas.contains(categoria.id)
            val isExpanded = categoriaDesplegadaId == categoria.id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        GestorInteresesUsuario.alternarCategoria(categoria.id)
                        if (!isSelected) {
                            categoriaDesplegadaId = categoria.id
                        }
                    }
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(categoria.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = categoria.titulo,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = categoria.descripcion,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // Indicador de selección
                    Text(
                        text = if (isSelected) "✓ Activo" else "+ Elegir",
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Subcategorías desplegables si la categoría está seleccionada
                if (isSelected && categoria.subcategorias.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Especificar tipos de ${categoria.id}:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isExpanded) "▲ Ocultar" else "▼ Ver tipos",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable {
                                categoriaDesplegadaId = if (isExpanded) null else categoria.id
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            categoria.subcategorias.forEach { sub ->
                                val isSubSelected = perfilIntereses.subcategoriasSeleccionadas.contains(sub.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSubSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.15f))
                                        .clickable {
                                            GestorInteresesUsuario.alternarSubcategoria(sub.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isSubSelected) "☑" else "☐",
                                        color = if (isSubSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${sub.emoji} ${sub.nombre}",
                                        color = if (isSubSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Gustos personalizados abiertos (Input libre)
        Text(
            text = "¿Otro gusto o especificar algo más?",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = nuevoGustoTexto,
                onValueChange = { nuevoGustoTexto = it },
                placeholder = { Text("Ej: Salsa choque, Anime, Senderismo...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        if (nuevoGustoTexto.isNotBlank()) {
                            GestorInteresesUsuario.agregarGustoPersonalizado(nuevoGustoTexto)
                            nuevoGustoTexto = ""
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Agregar", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Mostrar gustos personalizados agregados
        if (perfilIntereses.gustosPersonalizados.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                perfilIntereses.gustosPersonalizados.forEach { gusto ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(gusto, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.5.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "✕",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 9.sp,
                            modifier = Modifier.clickable {
                                GestorInteresesUsuario.eliminarGustoPersonalizado(gusto)
                            }
                        )
                    }
                }
            }
        }
    }
}

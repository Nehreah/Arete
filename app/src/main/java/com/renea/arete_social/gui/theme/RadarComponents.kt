package com.renea.arete_social.gui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// RADAR COMPONENTS — SolarPunk Edition
//
// Piezas de UI reutilizables que leen colores de
// MaterialTheme.colorScheme para responder al tema Claro/Oscuro
// (definidos en Theme.kt: AntiredDayColorScheme / AntiredNightColorScheme).
// ============================================================


/**
 * Contenedor estándar de pantalla: fondo del tema + cabecera [RadarTopBar]
 * (con botón atrás opcional y acciones) + área de contenido que ocupa el resto
 * de la pantalla. Reemplaza al TopAppBar de Material3 por defecto en todas las
 * pantallas fuera del mapa.
 */
@Composable
fun RadarScaffold(
    titulo: String,
    onAtras: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RadarTopBar(
            titulo = titulo,
            onAtras = onAtras,
            acciones = acciones
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            contenido()
        }
    }
}

/**
 * Columna con scroll + padding + espaciado estándar, para el contenido normal
 * (formularios, detalles) de una pantalla dentro de [RadarScaffold].
 */
@Composable
fun RadarScreenColumn(
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AreteDimens.space5),
        verticalArrangement = Arrangement.spacedBy(AreteDimens.space4),
        content = contenido
    )
}

@Composable
fun RadarTopBar(
    titulo: String,
    onAtras: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val esNuevoDiseno = DisenoConfig.NUEVO_DISENO

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .border(
                width = if (esNuevoDiseno) AreteDimens.borderHairline else AreteDimens.borderDefault,
                color = outline.copy(alpha = if (esNuevoDiseno) 0.18f else 0.35f)
            )
            .padding(horizontal = AreteDimens.space4, vertical = if (esNuevoDiseno) AreteDimens.space4 else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onAtras != null) {
            Box(
                modifier = Modifier
                    .size(if (esNuevoDiseno) AreteDimens.controlModern else AreteDimens.controlSmall)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = if (esNuevoDiseno) 0.12f else 0.18f))
                    .border(1.dp, primary.copy(alpha = if (esNuevoDiseno) 0.3f else 0.5f), CircleShape)
                    .clickable(onClick = onAtras),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(AreteDimens.space3))
        }

        Text(
            text = if (esNuevoDiseno) titulo else titulo.uppercase(),
            color = if (esNuevoDiseno) MaterialTheme.colorScheme.onBackground else primary,
            fontSize = if (esNuevoDiseno) 18.sp else 15.sp,
            fontWeight = if (esNuevoDiseno) FontWeight.Bold else FontWeight.Black,
            letterSpacing = if (esNuevoDiseno) 0.sp else 2.sp,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = acciones
        )
    }
}

/** Título de sección dentro de una pantalla (p. ej. "Grupos activos"). */
@Composable
fun RadarSectionTitle(texto: String, modifier: Modifier = Modifier) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    Text(
        text = if (esNuevo) texto else texto.uppercase(),
        color = if (esNuevo) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
        fontSize = if (esNuevo) 15.sp else 13.sp,
        fontWeight = if (esNuevo) FontWeight.Bold else FontWeight.Black,
        letterSpacing = if (esNuevo) 0.sp else 1.5.sp,
        modifier = modifier
    )
}

/** Etiqueta pequeña y tenue (p. ej. encima de un campo o un dato secundario). */
@Composable
fun RadarLabel(texto: String, modifier: Modifier = Modifier) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    Text(
        text = if (esNuevo) texto else texto.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = if (esNuevo) FontWeight.Medium else FontWeight.Bold,
        letterSpacing = if (esNuevo) 0.3.sp else 1.5.sp,
        modifier = modifier
    )
}

/** Párrafo de cuerpo normal, en onBackground sobre fondo del tema. */
@Composable
fun RadarBody(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 15.sp,
        modifier = modifier
    )
}

/** Divisor visual sutil para listas y pantallas modernas. */
@Composable
fun RadarDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        thickness = 0.8.dp,
        modifier = modifier
    )
}

/** Botón principal: relleno con color primary del tema, texto onPrimary. */
@Composable
fun RadarButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(if (esNuevo) 12.dp else 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.outline,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    ) {
        Text(
            text = if (esNuevo) texto else texto.uppercase(),
            fontWeight = if (esNuevo) FontWeight.SemiBold else FontWeight.Black,
            letterSpacing = if (esNuevo) 0.sp else 1.sp
        )
    }
}

/** Botón secundario: contorno del color primary del tema, sin relleno. */
@Composable
fun RadarOutlinedButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(if (esNuevo) 12.dp else 14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (esNuevo) 1.dp else 1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = if (habilitado) 0.55f else 0.2f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    ) {
        Text(
            text = if (esNuevo) texto else texto.uppercase(),
            fontWeight = if (esNuevo) FontWeight.SemiBold else FontWeight.Bold,
            letterSpacing = if (esNuevo) 0.sp else 1.sp
        )
    }
}

/** Campo de texto con colores del tema (borde/label/cursor en primary). */
@Composable
fun RadarTextField(
    valor: String,
    onValorCambia: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    transformacionVisual: VisualTransformation = VisualTransformation.None,
    lineasMaximas: Int = 1,
    lineasMinimas: Int = 1,
    estiloTexto: androidx.compose.ui.text.TextStyle? = null,
    tamanoEtiqueta: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambia,
        label = { Text(etiqueta, fontSize = tamanoEtiqueta) },
        textStyle = estiloTexto ?: androidx.compose.material3.LocalTextStyle.current,
        singleLine = lineasMaximas == 1,
        minLines = lineasMinimas,
        maxLines = lineasMaximas,
        visualTransformation = transformacionVisual,
        shape = RoundedCornerShape(if (esNuevo) 12.dp else 14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (esNuevo) 0.35f else 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Panel/tarjeta con surface del tema y borde outline — contenedor genérico de contenido. */
@Composable
fun RadarCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    contenido: @Composable ColumnScope.() -> Unit
) {
    val esNuevo = DisenoConfig.NUEVO_DISENO
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (esNuevo) 16.dp else 18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (esNuevo) 0.8.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (esNuevo) 0.2f else 0.35f),
                shape = RoundedCornerShape(if (esNuevo) 16.dp else 18.dp)
            )
            .padding(padding),
        content = contenido
    )
}

/** Chip seleccionable (usado para visibilidad, duración de grupo, hora de evento...). */
@Composable
fun RadarChip(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorActivo: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (seleccionado) colorActivo.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (seleccionado) colorActivo.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = texto,
            color = if (seleccionado) colorActivo else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Spinner en línea, del tamaño de su [modifier]. Para usar dentro de formularios
 * mientras se guarda algo. No ocupa toda la pantalla: usa [RadarLoadingScreen]
 * para el estado "Cargando" de una pantalla completa.
 */
@Composable
fun RadarLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Pantalla completa de carga (fondo + spinner centrado), para el estado Cargando de una pantalla entera. */
@Composable
fun RadarLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Texto de error dentro de una tarjeta con borde. */
@Composable
fun RadarErrorText(mensaje: String, modifier: Modifier = Modifier) {
    RadarCard(modifier = modifier) {
        Text(
            text = mensaje,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Pantalla completa de error (fondo + RadarErrorText), para estados a nivel de pantalla. */
@Composable
fun RadarErrorScreen(mensaje: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        RadarErrorText(mensaje)
    }
}

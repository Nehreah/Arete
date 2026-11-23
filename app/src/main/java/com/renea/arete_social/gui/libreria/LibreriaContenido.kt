package com.renea.arete_social.gui.libreria

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renea.arete_social.domain.model.ArchivoPdf
import com.renea.arete_social.domain.model.LimitesLibreria
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.TipoObra
import com.renea.arete_social.feature.libreria.LibreriaUiState
import com.renea.arete_social.gui.theme.BotonPildora
import com.renea.arete_social.gui.theme.EstadoVacioSocial
import com.renea.arete_social.gui.theme.RadarTextField
import com.renea.arete_social.gui.theme.SelectorPildoras
import com.renea.arete_social.gui.theme.TipografiaLectura
import com.renea.arete_social.gui.theme.formatearFechaCorta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TIPOS = TipoObra.values().toList()

/** Contenido de la pestaña "Librería": dos sub-pestañas (cuentos y poemarios). */
@Composable
fun LibreriaContenido(
    estado: LibreriaUiState,
    onPublicar: (TipoObra, titulo: String, contenido: String, enlace: String, pdf: ArchivoPdf?, alTerminar: (String?, ObraLibreria?) -> Unit) -> Unit,
    onEliminar: (ObraLibreria) -> Unit,
    onCompartirEnMapa: (ObraLibreria) -> Unit = {},
    onAbrirPdf: (ObraLibreria, alObtener: (String) -> Unit) -> Unit,
    onRecargar: () -> Unit,
    onConsumirMensaje: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subpestana by remember { mutableIntStateOf(0) }
    var escribiendo by remember { mutableStateOf(false) }
    var leyendo by remember { mutableStateOf<ObraLibreria?>(null) }
    var obraRecienCreadaParaMapa by remember { mutableStateOf<ObraLibreria?>(null) }
    val tipoActual = TIPOS[subpestana]

    estado.mensaje?.let { m -> LaunchedEffect(m) { delay(3_500); onConsumirMensaje() } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!estado.disponible) {
            EstadoVacioSocial("📚", "La librería no está disponible", "Esta función no se encuentra habilitada en este momento.")
            return@Column
        }
        SelectorPildoras(
            opciones = TIPOS.map { "${it.etiquetaPlural} (${estado.deTipo(it).size})" },
            seleccion = subpestana,
            onSeleccion = { subpestana = it }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonPildora("＋  Escribir o subir PDF", onClick = { escribiendo = true }, modifier = Modifier.weight(1f))
            BotonPildora("↻", onClick = onRecargar, destacado = false, habilitado = !estado.cargando)
        }
        estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
            }
        }

        val obras = estado.deTipo(tipoActual)
        if (obras.isEmpty() && !estado.cargando) {
            EstadoVacioSocial(
                icono = if (tipoActual == TipoObra.CUENTO) "📖" else "🪶",
                titulo = "Tus ${tipoActual.etiquetaPlural.lowercase()} vivirán aquí",
                subtitulo = "Escríbelos directamente o sube el PDF. Puedes añadir un enlace discreto para una mejor lectura."
            )
        } else {
            obras.forEach { obra ->
                TarjetaObra(
                    obra = obra,
                    onCompartirEnMapa = onCompartirEnMapa,
                    onClick = { leyendo = obra }
                )
            }
        }
    }

    if (escribiendo) {
        FormularioObra(
            tipoInicial = tipoActual,
            guardando = estado.guardando,
            onGuardar = { tipo, t, c, e, pdf, fin ->
                onPublicar(tipo, t, c, e, pdf) { error, obraCreada ->
                    if (error == null) {
                        escribiendo = false
                        if (obraCreada != null) {
                            obraRecienCreadaParaMapa = obraCreada
                        }
                    }
                    fin(error, obraCreada)
                }
            },
            onCerrar = { escribiendo = false }
        )
    }
    obraRecienCreadaParaMapa?.let { obra ->
        AlertDialog(
            onDismissRequest = { obraRecienCreadaParaMapa = null },
            title = { Text("¿Deseas leerlo en el mapa?") },
            text = { Text("Tu Michi se quedará quieto leyendo tu ${obra.tipo.etiqueta.lowercase()} sobre el mapa durante 24 horas para que personas cercanas puedan leerlo.") },
            confirmButton = {
                TextButton(onClick = {
                    onCompartirEnMapa(obra)
                    obraRecienCreadaParaMapa = null
                }) { Text("Sí, compartir") }
            },
            dismissButton = {
                TextButton(onClick = { obraRecienCreadaParaMapa = null }) { Text("No") }
            }
        )
    }
    leyendo?.let { obra ->
        LectorObra(
            obra = obra,
            onAbrirPdf = onAbrirPdf,
            onCompartirEnMapa = onCompartirEnMapa,
            onEliminar = { onEliminar(obra); leyendo = null },
            onCerrar = { leyendo = null }
        )
    }
}

@Composable
private fun TarjetaObra(
    obra: ObraLibreria,
    onCompartirEnMapa: ((ObraLibreria) -> Unit)? = null,
    onClick: () -> Unit
) {
    val colores = MaterialTheme.colorScheme
    val forma = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(colores.surface)
            .border(1.dp, colores.outline.copy(alpha = 0.2f), forma)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(obra.titulo, style = TipografiaLectura.titulo, color = colores.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (obra.estaActivaEnMapa) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(colores.primary.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("📖 En el mapa", color = colores.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (obra.tienePdf) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(colores.primary.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("PDF", color = colores.primary, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        if (obra.contenido.isNotBlank()) {
            Text(obra.contenido, style = TipografiaLectura.vistaPrevia, color = colores.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatearFechaCorta(obra.creadaEn), style = TipografiaLectura.firma, color = colores.onSurfaceVariant.copy(alpha = 0.8f))
            if (onCompartirEnMapa != null) {
                Text(
                    text = if (obra.estaActivaEnMapa) "✓ Leyendo en mapa" else "📖 Leer en el mapa",
                    color = if (obra.estaActivaEnMapa) colores.primary else colores.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onCompartirEnMapa(obra) }.padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun LectorObra(
    obra: ObraLibreria,
    onAbrirPdf: (ObraLibreria, (String) -> Unit) -> Unit,
    onCompartirEnMapa: ((ObraLibreria) -> Unit)? = null,
    // null = lectura de la obra de otra persona: no se puede eliminar.
    onEliminar: (() -> Unit)?,
    onCerrar: () -> Unit
) {
    val colores = MaterialTheme.colorScheme
    val contexto = LocalContext.current
    var confirmarBorrado by remember { mutableStateOf(false) }
    fun abrirExterno(url: String) {
        runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colores.surface)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(obra.tipo.etiqueta.uppercase(), color = colores.primary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(obra.titulo, style = TipografiaLectura.titulo.copy(fontSize = 21.sp, lineHeight = 27.sp), color = colores.onSurface)
            Column(Modifier.weight(1f, fill = false).height(360.dp).verticalScroll(rememberScrollState())) {
                if (obra.contenido.isNotBlank()) {
                    Text(obra.contenido, style = TipografiaLectura.cuerpo, color = colores.onSurface)
                } else {
                    Text("Esta obra se lee en su PDF adjunto.", style = TipografiaLectura.firma, color = colores.onSurfaceVariant)
                }
            }
            // Enlace discreto: pequeño, sin botón, al pie del texto.
            obra.enlace?.let { url ->
                Text(
                    "↗ para acompañar la lectura",
                    color = colores.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { abrirExterno(url) }
                )
            }
            if (obra.tienePdf) {
                BotonPildora("Abrir PDF${obra.pdfNombre?.let { " · $it" } ?: ""}", onClick = { onAbrirPdf(obra, ::abrirExterno) }, destacado = false, modifier = Modifier.fillMaxWidth())
            }
            if (onCompartirEnMapa != null) {
                BotonPildora(
                    if (obra.estaActivaEnMapa) "✓ Leyéndose en el mapa" else "📖 Leer en el mapa",
                    onClick = { onCompartirEnMapa(obra) },
                    destacado = !obra.estaActivaEnMapa,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onEliminar != null) {
                    BotonPildora("Eliminar", onClick = { confirmarBorrado = true }, destacado = false, modifier = Modifier.weight(1f))
                }
                BotonPildora("Cerrar", onClick = onCerrar, modifier = Modifier.weight(1f))
            }
        }
    }
    if (confirmarBorrado && onEliminar != null) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Eliminar \"${obra.titulo}\"?") },
            text = { Text("Se borrará de tu librería junto con su PDF. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmarBorrado = false; onEliminar() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FormularioObra(
    tipoInicial: TipoObra,
    guardando: Boolean,
    onGuardar: (TipoObra, String, String, String, ArchivoPdf?, (String?, ObraLibreria?) -> Unit) -> Unit,
    onCerrar: () -> Unit
) {
    val colores = MaterialTheme.colorScheme
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var tipo by remember { mutableStateOf(tipoInicial) }
    var titulo by remember { mutableStateOf("") }
    var contenido by remember { mutableStateOf("") }
    var enlace by remember { mutableStateOf("") }
    var pdf by remember { mutableStateOf<ArchivoPdf?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectorPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            when (val r = leerPdf(contexto.contentResolver, uri)) {
                is ResultadoLecturaPdf.Listo -> { pdf = r.archivo; error = null }
                is ResultadoLecturaPdf.Fallo -> error = r.mensaje
            }
        }
    }

    Dialog(onDismissRequest = { if (!guardando) onCerrar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colores.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nueva obra", style = TipografiaLectura.titulo, color = colores.onSurface)
            SelectorPildoras(TIPOS.map { it.etiqueta }, TIPOS.indexOf(tipo), { tipo = TIPOS[it] })
            RadarTextField(titulo, { if (it.length <= LimitesLibreria.MAX_TITULO) titulo = it }, "Título", tamanoEtiqueta = 12.sp)
            RadarTextField(
                valor = contenido,
                onValorCambia = { if (it.length <= LimitesLibreria.MAX_CONTENIDO) contenido = it },
                etiqueta = if (tipo == TipoObra.POEMARIO) "Tus versos…" else "Tu cuento…",
                lineasMinimas = 8,
                lineasMaximas = 14,
                estiloTexto = TipografiaLectura.cuerpo.copy(color = colores.onSurface),
                tamanoEtiqueta = 12.sp
            )
            RadarTextField(enlace, { enlace = it }, "Enlace para acompañar la lectura (opcional)", tamanoEtiqueta = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora(
                    if (pdf == null) "📎 Adjuntar PDF" else "Cambiar PDF",
                    onClick = { selectorPdf.launch(arrayOf("application/pdf")) },
                    destacado = false,
                    habilitado = !guardando
                )
                pdf?.let {
                    Text(it.nombre, color = colores.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("✕", color = colores.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.clickable { pdf = null }.padding(6.dp))
                }
            }
            error?.let { Text(it, color = colores.error, fontSize = 11.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora("Cancelar", onClick = onCerrar, destacado = false, habilitado = !guardando, modifier = Modifier.weight(1f))
                BotonPildora(
                    if (guardando) "Guardando…" else "Guardar",
                    onClick = { error = null; onGuardar(tipo, titulo, contenido, enlace, pdf) { fallo, _ -> error = fallo } },
                    habilitado = !guardando,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


/**
 * Librería de OTRA persona, solo lectura: mismas dos sub-pestañas (cuentos y poemarios), sin
 * escribir ni eliminar. El servidor solo entrega las obras (y firma los PDF) si el perfil es público
 * o si son amistades.
 */
@Composable
fun LibreriaSoloLectura(
    obras: List<ObraLibreria>,
    cargando: Boolean,
    error: Boolean,
    nombreAutor: String,
    onAbrirPdf: (ObraLibreria, alObtener: (String) -> Unit) -> Unit,
    onRecargar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subpestana by remember { mutableIntStateOf(0) }
    var leyendo by remember { mutableStateOf<ObraLibreria?>(null) }
    val tipoActual = TIPOS[subpestana]
    val delTipo = obras.filter { it.tipo == tipoActual }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectorPildoras(
            opciones = TIPOS.map { tipo -> "${tipo.etiquetaPlural} (${obras.count { it.tipo == tipo }})" },
            seleccion = subpestana,
            onSeleccion = { subpestana = it }
        )
        if (cargando) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
            }
        }
        if (error && !cargando) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "No se pudo cargar la librería.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                BotonPildora("↻", onClick = onRecargar, destacado = false)
            }
        }
        if (delTipo.isEmpty() && !cargando && !error) {
            EstadoVacioSocial(
                icono = if (tipoActual == TipoObra.CUENTO) "📖" else "🪶",
                titulo = "Sin ${tipoActual.etiquetaPlural.lowercase()}",
                subtitulo = "$nombreAutor aún no ha publicado ${tipoActual.etiquetaPlural.lowercase()} en su librería."
            )
        } else {
            delTipo.forEach { obra -> TarjetaObra(obra) { leyendo = obra } }
        }
    }

    leyendo?.let { obra ->
        LectorObra(obra, onAbrirPdf = onAbrirPdf, onEliminar = null, onCerrar = { leyendo = null })
    }
}

package com.renea.arete_social.gui.comunidad

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.data.local.mural.RepositorioMuralesLocal
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.domain.model.MotivoReporte
import com.renea.arete_social.domain.model.PublicacionMural
import com.renea.arete_social.domain.model.TipoReporte
import com.renea.arete_social.domain.repository.RepositorioModeracion
import com.renea.arete_social.gui.moderacion.DialogoReportar
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuralesScreen(
    repositorio: RepositorioMuralesLocal,

    usuarioId: String,
    autorNombre: String,
    comunidades: List<Comunidad>,
    modifier: Modifier = Modifier,
    muralAbiertoExterno: Boolean? = null,
    onCambioMural: ((abierto: Boolean, comunidadId: String?, titulo: String) -> Unit)? = null,
    ocultarEncabezado: Boolean = false,
    repositorioModeracion: RepositorioModeracion? = null,
    comunidadIdFija: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comunidadId by remember(comunidadIdFija) { mutableStateOf<String?>(comunidadIdFija) }
    var muralAbierto by remember(comunidadIdFija) { mutableStateOf(comunidadIdFija != null) }
    var texto by remember { mutableStateOf("") }
    var publicaciones by remember { mutableStateOf<List<PublicacionMural>>(emptyList()) }
    var fotoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    fun recargar() {
        scope.launch {
            cargando = true
            error = null
            runCatching { repositorio.listar(comunidadId) }
                .onSuccess { publicaciones = it }
                .onFailure { error = it.message ?: "No se pudieron cargar las publicaciones." }
            cargando = false
        }
    }

    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
                    ?: throw IllegalStateException("No se pudo abrir la foto")
            }.onSuccess { bitmap ->
                val escalada = if (bitmap.width > 1600 || bitmap.height > 1600) {
                    val ratio = minOf(1600f / bitmap.width, 1600f / bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else bitmap
                val out = ByteArrayOutputStream()
                escalada.compress(Bitmap.CompressFormat.JPEG, 82, out)
                fotoBytes = out.toByteArray()
                aviso = "Foto lista para publicar."
                if (escalada !== bitmap) escalada.recycle()
                bitmap.recycle()
            }.onFailure { error = it.message ?: "No se pudo leer la foto." }
        }
    }

    // Cámara: URI temporal en MediaStore para foto de alta resolución
    var uriCamara by remember { mutableStateOf<Uri?>(null) }

    fun crearUriCamara(): Uri? = runCatching {
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "antired_foto_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
    }.getOrNull()

    val camaraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
        val uri = uriCamara
        if (guardada && uri != null) {
            // Marcar como no pendiente en Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val valores = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, valores, null, null)
            }
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("No se pudo leer la foto de cámara")
            }.onSuccess { bitmap ->
                val escalada = if (bitmap.width > 1600 || bitmap.height > 1600) {
                    val ratio = minOf(1600f / bitmap.width, 1600f / bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                } else bitmap
                val out = ByteArrayOutputStream()
                escalada.compress(Bitmap.CompressFormat.JPEG, 82, out)
                fotoBytes = out.toByteArray()
                aviso = "Foto tomada. Lista para publicar."
                if (escalada !== bitmap) escalada.recycle()
                bitmap.recycle()
            }.onFailure { error = it.message ?: "No se pudo procesar la foto." }
        } else if (!guardada && uri != null) {
            // Usuario canceló: eliminar la URI temporal para no dejar basura en la galería
            runCatching { context.contentResolver.delete(uri, null, null) }
            uriCamara = null
        }
    }

    val permisoCamaraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            val uri = crearUriCamara()
            if (uri != null) { uriCamara = uri; camaraLauncher.launch(uri) }
            else error = "No se pudo crear el archivo para la foto."
        } else {
            error = "Se necesita permiso de cámara para tomar fotos."
        }
    }

    fun lanzarCamara() {
        val permiso = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED) {
            val uri = crearUriCamara()
            if (uri != null) { uriCamara = uri; camaraLauncher.launch(uri) }
            else error = "No se pudo crear el archivo para la foto."
        } else {
            permisoCamaraLauncher.launch(permiso)
        }
    }

    LaunchedEffect(muralAbiertoExterno) {
        if (muralAbiertoExterno != null) {
            muralAbierto = muralAbiertoExterno
        }
    }

    LaunchedEffect(comunidadId, muralAbierto) {
        if (muralAbierto) recargar()
    }

    PullToRefreshBox(
        isRefreshing = cargando && muralAbierto,
        onRefresh = { if (muralAbierto) recargar() },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
        if (!muralAbierto) {
            Text("MURALES", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 23.sp)
            Text("Elige un espacio para ver y compartir publicaciones", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    MuralSelectorCard(
                        icono = "🏛️", titulo = "Mural Aréte", subtitulo = "Publicaciones visibles para toda la red de Aréte",
                        seleccionado = false, onClick = {
                            comunidadId = null
                            muralAbierto = true
                            aviso = null
                            error = null
                            onCambioMural?.invoke(true, null, "Mural Aréte")
                        }
                    )
                }
                if (comunidades.isNotEmpty()) {
                    item {
                        Text(
                            "MURALES DE TUS COMUNIDADES",
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    items(comunidades, key = { "comunidad-${it.id}" }) { comunidad ->
                        MuralSelectorCard(
                            icono = "👥", titulo = comunidad.nombre,
                            subtitulo = "Publicaciones compartidas dentro de esta comunidad",
                            seleccionado = false,
                            onClick = {
                                comunidadId = comunidad.id
                                muralAbierto = true
                                aviso = null
                                error = null
                                onCambioMural?.invoke(true, comunidad.id, comunidad.nombre)
                            }
                        )
                    }
                } else {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Cuando formes parte de una comunidad, su mural aparecerá aquí.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            val tituloMural = if (comunidadId == null) "Mural Aréte" else comunidades.firstOrNull { it.id == comunidadId }?.nombre ?: "Mural de comunidad"
            if (!ocultarEncabezado) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp).clickable {
                            muralAbierto = false
                            fotoBytes = null
                            error = null
                            aviso = null
                            onCambioMural?.invoke(false, null, "Parchate")
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tituloMural, color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (comunidadId == null) "Mural principal de Aréte" else "Mural de comunidad", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it.take(2000) },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("¿Qué quieres compartir?") }, minLines = 2
                    )
                    if (fotoBytes != null) Text("✓ Foto adjunta", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    aviso?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Button(onClick = { selectorFoto.launch("image/*") }, enabled = !cargando) { Text("🖼️ Galería") }
                        Button(onClick = { lanzarCamara() }, enabled = !cargando) { Text("📷 Cámara") }
                        Button(enabled = !cargando && (texto.isNotBlank() || fotoBytes != null), onClick = {
                            val bytes = fotoBytes
                            scope.launch {
                                cargando = true
                                error = null
                                aviso = null
                                runCatching {
                                    if (bytes != null) repositorio.publicarFoto(usuarioId, autorNombre, bytes, comunidadId, texto)
                                    else repositorio.publicarTexto(usuarioId, autorNombre, texto, comunidadId)
                                }.onSuccess {
                                    texto = ""
                                    fotoBytes = null
                                    aviso = "Publicado en el mural."
                                    recargar()
                                }.onFailure { error = it.message ?: "No se pudo publicar. Comprueba que perteneces a esa comunidad." }
                                cargando = false
                            }
                        }) { Text("Publicar") }
                    }
                }
            }
            if (cargando) CircularProgressIndicator(Modifier.size(26.dp).align(Alignment.CenterHorizontally))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (!cargando && publicaciones.isEmpty()) item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(18.dp)) {
                        Text(
                            "Todavía no hay publicaciones aquí. ¡Sé la primera persona en dejar algo!",
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(publicaciones, key = { it.id }) { post ->
                    PublicacionMuralCard(
                        post = post,
                        esPropia = post.autorId == usuarioId,
                        onReportar = if (repositorioModeracion != null && post.autorId != usuarioId) {
                            { motivo, descripcion ->
                                scope.launch {
                                    runCatching {
                                        repositorioModeracion.enviarReporte(
                                            reportadoId = post.autorId,
                                            tipo = TipoReporte.PUBLICACION,
                                            referenciaId = post.id,
                                            motivo = motivo,
                                            descripcion = descripcion
                                        )
                                    }
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun MuralSelectorCard(
    icono: String,
    titulo: String,
    subtitulo: String,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (seleccionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            RoundedCornerShape(20.dp)
        ).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text(icono, fontSize = 23.sp) }
            Column(Modifier.weight(1f)) {
                Text(titulo, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 27.sp)
        }
    }
}

@Composable
private fun PublicacionMuralCard(
    post: PublicacionMural,
    esPropia: Boolean = true,
    onReportar: ((motivo: MotivoReporte, descripcion: String) -> Unit)? = null
) {
    var mostrarDialogoReportar by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(post.autorNombre.ifBlank { "Alguien de Aréte" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(post.creadaEn)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                // Botón reportar solo en publicaciones ajenas
                if (!esPropia && onReportar != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { mostrarDialogoReportar = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚩", fontSize = 16.sp)
                    }
                }
            }
            if (post.contenido.isNotBlank()) Text(post.contenido, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface)
            post.imagenUrl?.let { AsyncImage(model = it, contentDescription = "Foto del mural", modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))) }
        }
    }

    if (mostrarDialogoReportar && onReportar != null) {
        DialogoReportar(
            titulo = "Reportar publicación",
            nombreObjetivo = post.autorNombre.ifBlank { "este usuario" },
            mostrarOpcionBloquear = false,
            onDismiss = { mostrarDialogoReportar = false },
            onConfirmar = { motivo, descripcion, _ ->
                mostrarDialogoReportar = false
                onReportar(motivo, descripcion)
            }
        )
    }
}

package com.renea.arete_social.gui.comunidad

import android.net.Uri
import com.renea.arete_social.domain.model.RolComunidad
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.feature.comunidades.ArchivoComunidad
import com.renea.arete_social.feature.comunidades.ComunidadDetalleUiState
import com.renea.arete_social.feature.comunidades.ComunidadDetalleViewModel
import com.renea.arete_social.feature.comunidades.EventoComunidad
import com.renea.arete_social.feature.comunidades.MiembroComunidad
import com.renea.arete_social.feature.comunidades.PublicacionMuro

/**
 * Pantalla de Detalle de Comunidad profesional y conectada a datos reales:
 * - Cabecera táctica inmersiva con botón circular < de retroceso, campana y menú ⋮.
 * - Avatar circular con borde neón esmeralda y punto activo.
 * - Nombre, subtítulo "Comunidad abierta", descripción y métricas reales (miembros, zona, online).
 * - Pestañas segmentadas: Inicio, Miembros, Eventos, Archivos con indicador activo.
 * - Acciones tácticas de unirse/salir de la comunidad y abrir el chat grupal cifrado.
 * - Publicador interactivo "¿Qué quieres compartir?" con selector de fotos local y botón de publicación.
 * - Chips de filtro: Todos, Nuevos, Eventos, Avisos, Debate.
 * - Pestaña Inicio: Muro interactivo con likes, comentarios y fotos cargadas con Coil.
 * - Pestaña Miembros: Lista de integrantes con conexión directa a su perfil público real.
 * - Pestaña Eventos: Agenda de eventos con botón interactivo de asistencia y navegación directa al radar GPS en el mapa.
 * - Pestaña Archivos: Repositorio táctico de archivos e imágenes de la comunidad.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ComunidadDetalleScreen(
    viewModel: ComunidadDetalleViewModel,
    onAbrirChat: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAtras: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {},
    onVerEventoEnMapa: (EventoComunidad) -> Unit = {},
    onCrearEventoComunitario: () -> Unit = {},
    repositorioMurales: com.renea.arete_social.data.local.mural.RepositorioMuralesLocal? = null,
    repositorioModeracion: com.renea.arete_social.domain.repository.RepositorioModeracion? = null,
    autorNombre: String = "Miembro"
) {
    val estado by viewModel.estado.collectAsState()
    var refrescando by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (estado !is ComunidadDetalleUiState.Cargando) refrescando = false

    LaunchedEffect(Unit) {
        viewModel.recargar()
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.recargar() },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val actual = estado) {
            is ComunidadDetalleUiState.Cargando -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CARGANDO COMUNIDAD...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is ComunidadDetalleUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ERROR DE COMUNIDAD",
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = actual.mensaje,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            is ComunidadDetalleUiState.Listo -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(Unit) {
                    viewModel.sincronizarNotificaciones(context)
                }
                ComunidadDetalleContenido(
                    comunidad = actual.comunidad,
                    uniendose = actual.uniendose,
                    esMiembro = actual.esMiembro,
                    solicitudEnviada = actual.solicitudEnviada,
                    miRol = actual.miRol,

                    notificacionesSilenciadas = actual.notificacionesSilenciadas,
                    publicaciones = actual.publicaciones,
                    miembros = actual.miembros,
                    eventos = actual.eventos,
                    archivos = actual.archivos,
                    solicitudesPendientes = actual.solicitudesPendientes,
                    usuarioIdActual = viewModel.usuarioId,
                    puedeGestionar = actual.puedeGestionar,
                    repositorioMurales = repositorioMurales,
                    repositorioModeracion = repositorioModeracion,
                    autorNombre = autorNombre,
                    onToggleMembresia = { viewModel.toggleMembresia() },
                    onAlternarNotificaciones = { viewModel.alternarNotificaciones(context) },
                    onActualizarComunidad = { nombre, desc, tipo ->
                        viewModel.actualizarComunidad(nombre, desc, tipo)
                    },
                    onCambiarAvatar = { bytes ->
                        viewModel.actualizarComunidad(
                            nombre = actual.comunidad.nombre,
                            descripcion = actual.comunidad.descripcion,
                            tipo = actual.comunidad.tipo,
                            fotoBytes = bytes
                        )
                    },
                    onAsignarRol = { usuarioDestinoId, nuevoRol ->
                        viewModel.asignarRol(usuarioDestinoId, nuevoRol)
                    },
                    onTransferirLiderazgo = { nuevoLiderId ->
                        viewModel.transferirLiderazgo(nuevoLiderId)
                    },
                    onEliminarComunidad = {
                        viewModel.eliminarComunidad(onEliminada = onAtras)
                    },
                    onPublicar = { texto, uri, cat -> viewModel.publicar(context, texto, uri, cat) },
                    onSubirArchivo = { uri -> viewModel.subirArchivo(context, uri) },
                    onToggleLike = { id -> viewModel.toggleLike(id) },
                    onToggleAsistir = { id -> viewModel.toggleAsistir(id) },
                    onAbrirChat = onAbrirChat,
                    onAtras = onAtras,
                    onVerPerfil = onVerPerfil,
                    onVerEventoEnMapa = onVerEventoEnMapa,
                    onCrearEventoComunitario = onCrearEventoComunitario,
                    onAprobarSolicitud = { id -> viewModel.aprobarSolicitud(id) },
                    onRechazarSolicitud = { id -> viewModel.rechazarSolicitud(id) },
                    onExpulsarMiembro = { id -> viewModel.expulsarMiembro(id) },
                    onDescargarArchivo = { archivoId, peerId -> viewModel.descargarArchivoLan(archivoId, peerId) },
                    onSincronizarArchivos = { peerId -> viewModel.solicitarArchivosLan(peerId) }
                )

            }
        }
    }
}

@Composable
private fun ComunidadDetalleContenido(
    comunidad: Comunidad,
    uniendose: Boolean,
    esMiembro: Boolean,
    solicitudEnviada: Boolean = false,
    miRol: String = "MIEMBRO",

    puedeGestionar: Boolean = false,
    notificacionesSilenciadas: Boolean = false,
    publicaciones: List<PublicacionMuro>,
    miembros: List<MiembroComunidad>,
    eventos: List<EventoComunidad>,
    archivos: List<ArchivoComunidad>,
    solicitudesPendientes: List<String> = emptyList(),
    usuarioIdActual: String = "",
    repositorioMurales: com.renea.arete_social.data.local.mural.RepositorioMuralesLocal? = null,
    repositorioModeracion: com.renea.arete_social.domain.repository.RepositorioModeracion? = null,
    autorNombre: String = "Miembro",
    onToggleMembresia: () -> Unit,
    onAlternarNotificaciones: () -> Unit,
    onActualizarComunidad: (String, String, String) -> Unit,
    onCambiarAvatar: (ByteArray) -> Unit,
    onAsignarRol: (String, String) -> Unit,
    onTransferirLiderazgo: (String) -> Unit,
    onEliminarComunidad: () -> Unit,
    onPublicar: (String, String?, String) -> Unit,
    onSubirArchivo: (Uri) -> Unit,
    onToggleLike: (String) -> Unit,
    onToggleAsistir: (String) -> Unit,
    onAbrirChat: (String) -> Unit,
    onAtras: () -> Unit,
    onVerPerfil: (String) -> Unit,
    onVerEventoEnMapa: (EventoComunidad) -> Unit,
    onCrearEventoComunitario: () -> Unit,
    onAprobarSolicitud: (String) -> Unit = {},
    onRechazarSolicitud: (String) -> Unit = {},
    onExpulsarMiembro: (String) -> Unit = {},
    onDescargarArchivo: (String, String) -> Unit = { _, _ -> },
    onSincronizarArchivos: (String) -> Unit = {}
) {

    val context = androidx.compose.ui.platform.LocalContext.current
    var tabSeleccionada by remember { mutableIntStateOf(0) }
    val tabs = listOf("Gaceta", "Mural", "Eventos", "Archivos")

    // Diálogos de gestión
    var mostrarDialogoEditar by remember { mutableStateOf(false) }
    var mostrarDialogoEliminar by remember { mutableStateOf(false) }
    var mostrarDialogoMiembros by remember { mutableStateOf(false) }
    var miembroParaGestionar by remember { mutableStateOf<MiembroComunidad?>(null) }

    val selectorAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) {
                onCambiarAvatar(bytes)
            }
        }
    }

    // Estado del publicador de muro
    var textoPublicacion by remember { mutableStateOf("") }
    var imagenSeleccionadaUri by remember { mutableStateOf<Uri?>(null) }

    val selectorImagen = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imagenSeleccionadaUri = uri
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // ====================================================
        // BANNER SUPERIOR CON CONTROLES
        // ====================================================
        item {
            ComunidadBannerHeader(
                comunidad = comunidad,
                miembrosCount = miembros.size,
                miRol = miRol,
                notificacionesSilenciadas = notificacionesSilenciadas,
                onAlternarNotificaciones = onAlternarNotificaciones,
                onEditarComunidad = { mostrarDialogoEditar = true },
                onCambiarAvatarClick = {
                    selectorAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onEliminarComunidad = { mostrarDialogoEliminar = true },
                onSalirComunidad = onToggleMembresia,
                onAtras = onAtras,
                onVerMiembrosClick = if (puedeGestionar) { { mostrarDialogoMiembros = true } } else null
            )
        }

        // ====================================================
        // BANDEJA DE APROBACIÓN (solo para líder/admin, comunidades privadas)
        // ====================================================
        if (puedeGestionar && solicitudesPendientes.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1200))
                        .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(text = "🔔", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SOLICITUDES DE MEMBRESÍA · ${solicitudesPendientes.size}",
                            color = Color(0xFFFFC107),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                    solicitudesPendientes.forEach { solicitanteId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👤 ${solicitanteId.take(16)}…",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Botón Aprobar
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D2B0D))
                                    .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                                    .clickable { onAprobarSolicitud(solicitanteId) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓ ACEPTAR",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Botón Rechazar
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2B0D0D))
                                    .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                                    .clickable { onRechazarSolicitud(solicitanteId) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕ DENEGAR",
                                    color = Color(0xFFFF5252),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ====================================================
        // PESTAÑAS SEGMENTADAS
        // ====================================================
        item {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                tabs.forEachIndexed { index, titulo ->
                    val esActiva = tabSeleccionada == index
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { tabSeleccionada = index }
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icono = when (index) {
                                0 -> "📰"
                                1 -> "🏛️"
                                2 -> "📅"
                                else -> "📁"
                            }
                            Text(text = icono, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = titulo,
                                color = if (esActiva) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (esActiva) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .width(36.dp)
                                .background(
                                    if (esActiva) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }

        // ====================================================
        // BOTONES DE ACCIÓN: CHAT + ESTADO DE MIEMBRO REAL
        // ====================================================
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onAbrirChat(comunidad.chatId) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💬 CHAT DE COMUNIDAD",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }

                val esNuevo = com.renea.arete_social.gui.theme.DisenoConfig.NUEVO_DISENO
                val fondoBoton = when {
                    uniendose -> MaterialTheme.colorScheme.surfaceVariant
                    solicitudEnviada -> Color(0xFF1A1200)
                    esMiembro -> if (esNuevo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    esNuevo -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                }
                val textoBotonColor = when {
                    uniendose -> MaterialTheme.colorScheme.onSurfaceVariant
                    solicitudEnviada -> Color(0xFFFFC107)
                    esMiembro -> if (esNuevo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    esNuevo -> MaterialTheme.colorScheme.onPrimary
                    else -> Color.White
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fondoBoton)
                        .border(
                            1.dp,
                            when {
                                solicitudEnviada -> Color(0xFFFFC107).copy(alpha = 0.6f)
                                esMiembro -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !uniendose && !solicitudEnviada, onClick = onToggleMembresia)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            uniendose -> "PROCESANDO..."
                            solicitudEnviada -> "⏳ SOLICITUD ENVIADA"
                            esMiembro -> "✓ MIEMBRO ACTIVO"
                            else -> "+ UNIRSE A LA COMUNIDAD"
                        },
                        color = textoBotonColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

            }
        }

        // ====================================================
        // CONTENIDO SEGÚN PESTAÑA SELECCIONADA
        // ====================================================
        when (tabSeleccionada) {
            0 -> {
                // TAB 0: GACETA OFICIAL (Publicaciones oficiales; solo publica líder/admin)
                if (puedeGestionar) {
                    item {
                        // PUBLICADOR INTERACTIVO
                        Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✎", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (textoPublicacion.isEmpty()) {
                                    Text(
                                        text = "¿Qué quieres compartir?",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                BasicTextField(
                                    value = textoPublicacion,
                                    onValueChange = { textoPublicacion = it },
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Botón para seleccionar foto
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { selectorImagen.launch("image/*") }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🖼️", fontSize = 16.sp)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Botón de publicar
                            val puedePublicar = textoPublicacion.isNotBlank() || imagenSeleccionadaUri != null
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (puedePublicar) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        if (puedePublicar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .clickable(enabled = puedePublicar) {
                                        onPublicar(
                                            textoPublicacion,
                                            imagenSeleccionadaUri?.toString(),
                                            "General"
                                        )
                                        textoPublicacion = ""
                                        imagenSeleccionadaUri = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "➤",
                                    color = if (puedePublicar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Vista previa de imagen seleccionada
                        imagenSeleccionadaUri?.let { uri ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Foto adjunta",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Foto lista para adjuntar",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF220808))
                                        .clickable { imagenSeleccionadaUri = null },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "✕", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                }

                // FEED DE PUBLICACIONES DE LA GACETA
                if (publicaciones.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Todavía no hay publicaciones en la gaceta oficial.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(publicaciones, key = { it.id }) { pub ->
                        PublicacionMuroCardItem(
                            pub = pub,
                            onToggleLike = { onToggleLike(pub.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GACETA OFICIAL",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            1 -> {
                // TAB 1: MURAL PROPIO DE LA COMUNIDAD (Abierto a todos los miembros)
                item {
                    if (repositorioMurales != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(580.dp)
                        ) {
                            MuralesScreen(
                                repositorio = repositorioMurales,
                                usuarioId = usuarioIdActual,
                                autorNombre = autorNombre,
                                comunidades = listOf(comunidad),
                                comunidadIdFija = comunidad.id,
                                ocultarEncabezado = true,
                                repositorioModeracion = repositorioModeracion,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Text(
                            text = "Mural no disponible en este momento.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            2 -> {
                // TAB 2: EVENTOS REALES GEOLOCALIZADOS
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AGENDA DE EVENTOS [ 0${eventos.size} ]",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        val puedeCrearEventos = RolComunidad.desdeCodigo(miRol).puedeCrearEventos()
                        if (puedeCrearEventos) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCrearEventoComunitario)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+ CREAR EVENTO",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                items(eventos, key = { it.id }) { evento ->
                    EventoCardItem(
                        evento = evento,
                        onToggleAsistir = { onToggleAsistir(evento.id) },
                        onVerEnMapa = { onVerEventoEnMapa(evento) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            3 -> {
                // TAB 3: ARCHIVOS / FOTOS
                item {
                    ArchivosTabContenido(
                        publicaciones = publicaciones,
                        archivos = archivos,
                        onSubirArchivo = onSubirArchivo,
                        liderId = comunidad.liderId,
                        onDescargarArchivo = onDescargarArchivo,
                        onSincronizarArchivos = onSincronizarArchivos
                    )
                }
            }
        }
    }

    // Diálogo: Lista de miembros (solo accesible para líderes y administradores)
    if (mostrarDialogoMiembros) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoMiembros = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Miembros (${miembros.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = RolComunidad.desdeCodigo(miRol).texto.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    if (miembros.isEmpty()) {
                        Text(
                            text = "No hay miembros en esta comunidad.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(miembros, key = { it.id }) { miembro ->
                                MiembroCardItem(
                                    miembro = miembro,
                                    esLider = RolComunidad.desdeCodigo(miRol).esLiderODueno() || puedeGestionar,
                                    esPropio = miembro.id == usuarioIdActual,
                                    onVerPerfil = {
                                        mostrarDialogoMiembros = false
                                        onVerPerfil(miembro.id)
                                    },
                                    onGestionar = {
                                        miembroParaGestionar = miembro
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarDialogoMiembros = false }) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Diálogo: Editar Comunidad
    if (mostrarDialogoEditar) {
        var editNombre by remember { mutableStateOf(comunidad.nombre) }
        var editDescripcion by remember { mutableStateOf(comunidad.descripcion) }
        var editTipo by remember { mutableStateOf(comunidad.tipo) }

        AlertDialog(
            onDismissRequest = { mostrarDialogoEditar = false },
            title = { Text("Editar comunidad", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editNombre,
                        onValueChange = { editNombre = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescripcion,
                        onValueChange = { editDescripcion = it },
                        label = { Text("Descripción") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNombre.isNotBlank()) {
                            onActualizarComunidad(editNombre.trim(), editDescripcion.trim(), editTipo.trim())
                            mostrarDialogoEditar = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEditar = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo: Eliminar Comunidad
    if (mostrarDialogoEliminar) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEliminar = false },
            title = { Text("¿Eliminar comunidad?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Text("Esta acción eliminará la comunidad «${comunidad.nombre}» permanentemente y no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarDialogoEliminar = false
                        onEliminarComunidad()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEliminar = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo: Gestionar Rol de Miembro
    miembroParaGestionar?.let { m ->
        val esLider = RolComunidad.desdeCodigo(miRol).esLiderODueno()
        AlertDialog(
            onDismissRequest = { miembroParaGestionar = null },
            title = { Text("Gestionar a ${m.nombre}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rol actual: ${m.rol}", fontWeight = FontWeight.Medium)
                    if (esLider) {
                        if (m.rol != "Admin delegado") {
                            OutlinedButton(
                                onClick = {
                                    onAsignarRol(m.id, "MODERADOR")
                                    miembroParaGestionar = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("⭐ Asignar como Admin delegado")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onAsignarRol(m.id, "MIEMBRO")
                                    miembroParaGestionar = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("👤 Quitar rol de Admin (pasar a Miembro)")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onTransferirLiderazgo(m.id)
                                miembroParaGestionar = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("👑 Transferir liderazgo")
                        }
                    }

                    if (esLider || puedeGestionar) {
                        OutlinedButton(
                            onClick = {
                                onExpulsarMiembro(m.id)
                                miembroParaGestionar = null
                            },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF5252)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚪 Expulsar de la comunidad", color = Color(0xFFFF5252))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { miembroParaGestionar = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun ComunidadBannerHeader(
    comunidad: Comunidad,
    miembrosCount: Int,
    miRol: String,
    notificacionesSilenciadas: Boolean,
    onAlternarNotificaciones: () -> Unit,
    onEditarComunidad: () -> Unit,
    onCambiarAvatarClick: () -> Unit,
    onEliminarComunidad: () -> Unit,
    onSalirComunidad: () -> Unit,
    onAtras: () -> Unit,
    onVerMiembrosClick: (() -> Unit)? = null
) {
    var menuAbierto by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(top = 12.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Column {
            // Fila superior: Atrás + Bell + Menú
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x66081A11))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(onClick = onAtras),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "‹", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Campana de notificaciones interactiva
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (notificacionesSilenciadas) Color(0x33FF5252) else Color(0x66081A11))
                            .border(1.dp, if (notificacionesSilenciadas) Color(0xFFFF5252) else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClick = onAlternarNotificaciones),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (notificacionesSilenciadas) "🔕" else "🔔", fontSize = 14.sp)
                    }

                    // Tres puntos con menú desplegable
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x66081A11))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { menuAbierto = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⋮", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                        DropdownMenu(
                            expanded = menuAbierto,
                            onDismissRequest = { menuAbierto = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            val puedeGestionar = RolComunidad.desdeCodigo(miRol).puedeGestionar()
                            val esLider = RolComunidad.desdeCodigo(miRol).esLiderODueno()

                            if (puedeGestionar) {
                                if (onVerMiembrosClick != null) {
                                    DropdownMenuItem(
                                        text = { Text("👥 Ver miembros ($miembrosCount)", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            menuAbierto = false
                                            onVerMiembrosClick()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("✏️ Editar información", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        menuAbierto = false
                                        onEditarComunidad()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🖼️ Cambiar foto de avatar", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        menuAbierto = false
                                        onCambiarAvatarClick()
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (notificacionesSilenciadas) "🔔 Activar notificaciones" else "🔕 Silenciar notificaciones",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    menuAbierto = false
                                    onAlternarNotificaciones()
                                }
                            )

                            if (esLider) {
                                DropdownMenuItem(
                                    text = { Text("🗑️ Eliminar comunidad", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuAbierto = false
                                        onEliminarComunidad()
                                    }
                                )
                            } else if (miRol.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("🚪 Salir de la comunidad", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuAbierto = false
                                        onSalirComunidad()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Avatar central con halo esmeralda + Datos de comunidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val puedeGestionar = RolComunidad.desdeCodigo(miRol).puedeGestionar()

                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .shadow(12.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                            .then(
                                if (puedeGestionar) Modifier.clickable(onClick = onCambiarAvatarClick) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!comunidad.fotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = comunidad.fotoUrl,
                                contentDescription = "Avatar de la comunidad",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = "🌲", fontSize = 32.sp)
                        }
                    }

                    // Dot indicador o icono de edición si es líder/admin
                    if (puedeGestionar) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .clickable(onClick = onCambiarAvatarClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📷", fontSize = 11.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = comunidad.nombre,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Comunidad abierta",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = comunidad.descripcion.ifBlank { "Gente de la zona. Noticias, eventos, ideas y todo lo que nos une." },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badge de miembros (Interactivo para líder y admins, informativo para el resto)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                val modificadorMiembros = if (onVerMiembrosClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onVerMiembrosClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                }

                Row(
                    modifier = modificadorMiembros,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👥", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$miembrosCount miembros",
                        color = if (onVerMiembrosClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (onVerMiembrosClick != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "▾",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicacionMuroCardItem(
    pub: PublicacionMuro,
    onToggleLike: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header: Avatar + Nombre con punto + Tiempo + Menú
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👤", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pub.autor,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pub.tiempo,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            if (pub.pendiente) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "⏳ Pendiente de envío",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (pub.esEvento) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📅 Evento",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "⋮",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Texto de la publicación
            if (pub.texto.isNotBlank()) {
                Text(
                    text = pub.texto,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            }

            // Imagen adjunta con Coil si existe
            pub.imagenUri?.let { uriString ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = uriString,
                        contentDescription = "Imagen de la publicación",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fila de interacciones: Likes, Comentarios, Compartir
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleLike)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pub.likedPorMi) "❤️" else "♡",
                        color = if (pub.likedPorMi) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${pub.likes}",
                        color = if (pub.likedPorMi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (pub.likedPorMi) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💬", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${pub.comentarios}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }

                Text(text = "↗", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MiembroCardItem(
    miembro: MiembroComunidad,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    esLider: Boolean = false,
    esPropio: Boolean = false,
    onVerPerfil: () -> Unit,
    onGestionar: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onVerPerfil)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!miembro.fotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = miembro.fotoUrl,
                            contentDescription = miembro.nombre,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(text = miembro.avatarEmoji, fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = miembro.nombre,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (miembro.online) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    CircleShape
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (miembro.online) "En línea" else "Desconectado",
                        color = if (miembro.online) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Etiqueta de Rol táctico
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (miembro.rol) {
                                "Líder" -> Color(0xFF2E2207)
                                "Admin delegado" -> Color(0xFF132B1E)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                        .border(
                            1.dp,
                            when (miembro.rol) {
                                "Líder" -> Color(0xFFFFC107)
                                "Admin delegado" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surface
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = miembro.rol,
                        color = when (miembro.rol) {
                            "Líder" -> Color(0xFFFFD54F)
                            "Admin delegado" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (esLider && !esPropio && miembro.rol != "Líder") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onGestionar),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙️", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EventoCardItem(
    evento: EventoComunidad,
    onToggleAsistir: () -> Unit,
    onVerEnMapa: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = evento.fecha,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "👥 ${evento.asistentes} van",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = evento.titulo,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = evento.ubicacion,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = evento.descripcion,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón para centrar / ver en el mapa radar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable(onClick = onVerEnMapa)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📍 VER EN RADAR",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Botón para unirse/cancelar asistencia
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (evento.asistire) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (evento.asistire) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(onClick = onToggleAsistir)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (evento.asistire) "✓ ASISTIENDO" else "[ + ASISTIR ]",
                        color = if (evento.asistire) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivosTabContenido(
    publicaciones: List<PublicacionMuro>,
    archivos: List<ArchivoComunidad>,
    onSubirArchivo: (Uri) -> Unit,
    liderId: String = "",
    onDescargarArchivo: (String, String) -> Unit = { _, _ -> },
    onSincronizarArchivos: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onSubirArchivo(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REPOSITORIO [ ARCHIVOS ]",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (liderId.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable {
                                onSincronizarArchivos(liderId)
                                android.widget.Toast.makeText(context, "Sincronizando archivos por LAN...", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🔄 LAN",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .clickable { filePickerLauncher.launch("*/*") }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+ SUBIR",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (archivos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO HAY ARCHIVOS ALMACENADOS EN ESTA COMUNIDAD.\n¡SÉ EL PRIMERO EN SUBIR UN DOCUMENTO!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            archivos.forEach { archivo ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val icon = when {
                                archivo.nombre.endsWith(".pdf", ignoreCase = true) -> "📄 "
                                archivo.nombre.endsWith(".png", ignoreCase = true) || archivo.nombre.endsWith(".jpg", ignoreCase = true) || archivo.nombre.endsWith(".jpeg", ignoreCase = true) -> "🖼️ "
                                archivo.nombre.endsWith(".zip", ignoreCase = true) || archivo.nombre.endsWith(".rar", ignoreCase = true) -> "📦 "
                                else -> "📁 "
                            }
                            Text(
                                text = "$icon${archivo.nombre}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val tamanoKb = if (archivo.tamanoBytes > 0) "${archivo.tamanoBytes / 1024} KB" else "Desconocido"
                            val autor = if (archivo.autorNombre.isNotBlank()) " • Autor: ${archivo.autorNombre}" else ""
                            Text(
                                text = "$tamanoKb$autor",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val archivoLocal = if (archivo.url.isNotBlank()) java.io.File(archivo.url) else null
                        val estaEnLocal = archivoLocal != null && archivoLocal.exists()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .clickable {
                                    if (estaEnLocal) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(archivo.url), archivo.tipoMime.ifBlank { "*/*" })
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No se pudo abrir el archivo", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        if (liderId.isNotBlank()) {
                                            onDescargarArchivo(archivo.id, liderId)
                                            android.widget.Toast.makeText(context, "Solicitando descarga por LAN...", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "No hay pares LAN disponibles", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (estaEnLocal) "⬇ ABRIR" else "⬇ LAN",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Galería de fotos subidas en publicaciones del muro
        val fotosSubidas = publicaciones.mapNotNull { it.imagenUri }
        if (fotosSubidas.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "FOTOS COMPARTIDAS EN EL MURO [ 0${fotosSubidas.size} ]",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fotosSubidas.forEach { uri ->
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Foto compartida",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

package com.renea.arete_social.gui.auth.perfil

import androidx.compose.material3.MaterialTheme
import com.renea.arete_social.gui.theme.ModoTema
import com.renea.arete_social.gui.theme.PreferenciaTemaApp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.TipoPublicacionPerfil
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.feature.auth.PerfilUiState
import com.renea.arete_social.feature.auth.PerfilViewModel
import com.renea.arete_social.feature.cartas.CartasViewModel
import com.renea.arete_social.feature.libreria.LibreriaViewModel
import com.renea.arete_social.gui.cartas.CartasContenido
import com.renea.arete_social.gui.libreria.LibreriaContenido
import com.renea.arete_social.gui.theme.SelectorPildoras
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.renea.arete_social.gui.theme.AmbienteAssets
import com.renea.arete_social.gui.theme.LocalCicloDia
import com.renea.arete_social.gui.theme.LocalRadarColors
import com.renea.arete_social.gui.theme.RadarButton
import com.renea.arete_social.gui.theme.RadarErrorScreen
import com.renea.arete_social.gui.theme.RadarGreen
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarLoadingScreen
import com.renea.arete_social.gui.theme.RadarOutlinedButton
import com.renea.arete_social.gui.theme.RadarTextField
import com.renea.arete_social.gui.auth.DialogoEulaNormas


/**
 * Perfil Propio: Rediseño simple, moderno y social:
 * - Avatar con foto / inicial y botón rápido para cambiar imagen.
 * - Acceso directo a contenido social sin barra de métricas.
 * - Lista de "Mis Amigos" con botones rápidos para Chat, Videollamada y Perfil.
 * - Edición de datos personales (Nombre, Bio, Visibilidad).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    viewModel: PerfilViewModel,
    cartasViewModel: CartasViewModel,
    libreriaViewModel: LibreriaViewModel,
    modifier: Modifier = Modifier,
    onAbrirChat: ((chatId: String, nombre: String) -> Unit)? = null,
    onAbrirChatConUsuario: ((usuarioId: String, nombre: String) -> Unit)? = null,
    onIniciarVideollamada: ((contactoId: String, nombre: String, foto: String?) -> Unit)? = null,
    onVerPerfilAmigo: ((usuarioId: String) -> Unit)? = null,
    onGestionarIdentidad: (() -> Unit)? = null,
    onCerrarSesion: () -> Unit = {},
    onEliminarCuenta: () -> Unit = {}
) {
    val estado by viewModel.estado.collectAsState()
    val amigos by viewModel.amigos.collectAsState()
    var refrescando by remember { mutableStateOf(false) }
    if (estado !is PerfilUiState.Cargando) refrescando = false

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.recargar() },
        modifier = modifier
    ) {
        when (val actual = estado) {
            is PerfilUiState.Cargando -> RadarLoadingScreen()

            is PerfilUiState.Error -> RadarErrorScreen(mensaje = actual.mensaje)

            is PerfilUiState.Listo -> {
            var nombre by remember(actual.usuario.id) { mutableStateOf(actual.usuario.nombre) }
            var descripcion by remember(actual.usuario.id) { mutableStateOf(actual.usuario.descripcion) }
            var uriParaRecorte by remember { mutableStateOf<Uri?>(null) }
            var uriParaPortada by remember { mutableStateOf<Uri?>(null) }
            var confirmarQuitarPortada by remember { mutableStateOf(false) }
            val contextoPerfil = LocalContext.current
            val preferenciasEncuesta = remember(contextoPerfil) {
                contextoPerfil.getSharedPreferences("antired_perfil_inicial", Context.MODE_PRIVATE)
            }
            val claveEncuestaOmitida = "omitida_${actual.usuario.id}"
            val perfilIncompleto = actual.usuario.hobby.isNullOrBlank() || actual.usuario.deporte.isNullOrBlank() ||
                actual.usuario.ritmoMusical.isNullOrBlank() || actual.usuario.serieFavorita.isNullOrBlank() ||
                actual.usuario.libroFavorito.isNullOrBlank() || actual.usuario.signoZodiacal.isNullOrBlank()
            var mostrarPerfilInicial by remember(actual.usuario.id) {
                mutableStateOf(perfilIncompleto && if (viewModel.sesionEsInvitado) {
                    !com.renea.arete_social.feature.auth.GestorPerfilDatosTemporales.fueEncuestaOmitida(actual.usuario.id)
                } else {
                    !preferenciasEncuesta.getBoolean(claveEncuestaOmitida, false)
                })
            }
            fun marcarEncuestaResuelta() {
                if (viewModel.sesionEsInvitado) {
                    com.renea.arete_social.feature.auth.GestorPerfilDatosTemporales.marcarEncuestaOmitida(actual.usuario.id)
                } else {
                    preferenciasEncuesta.edit().putBoolean(claveEncuestaOmitida, true).apply()
                }
                mostrarPerfilInicial = false
            }
            if (mostrarPerfilInicial) {
                PerfilInicialDialog(
                    usuario = actual.usuario,
                    onGuardar = { nuevoNombre, hobby, deporte, ritmo, serie, libro, signo ->
                        if (nuevoNombre != actual.usuario.nombre) viewModel.actualizarPerfil(nuevoNombre, actual.usuario.descripcion)
                        viewModel.actualizarDatosPersonales(hobby, deporte, ritmo, serie, libro, signo)
                        marcarEncuestaResuelta()
                    },
                    onCerrar = { marcarEncuestaResuelta() }
                )
            }

            // Diálogo de recorte y centrado interactivo de foto
            uriParaRecorte?.let { uri ->
                EditorRecorteFotoDialog(
                    uri = uri,
                    onDismiss = { uriParaRecorte = null },
                    onFotoRecortada = { bytes, extension ->
                        uriParaRecorte = null
                        viewModel.subirFoto(bytes, extension)
                    }
                )
            }

            // Ajuste de la portada (arrastrar, zoom, girar) antes de guardarla
            uriParaPortada?.let { uri ->
                EditorPortadaDialog(
                    uri = uri,
                    onDismiss = { uriParaPortada = null },
                    onPortadaLista = { bytes, extension ->
                        uriParaPortada = null
                        viewModel.subirPortada(bytes, extension)
                    }
                )
            }

            if (confirmarQuitarPortada) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmarQuitarPortada = false },
                    title = { Text("¿Quitar la portada?") },
                    text = { Text("Volverás a la portada por defecto.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            confirmarQuitarPortada = false
                            viewModel.quitarPortada()
                        }) { Text("Quitar") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmarQuitarPortada = false }) { Text("Cancelar") }
                    }
                )
            }

            var pestanaSocial by remember { mutableIntStateOf(0) }
            var subPestanaAmigos by remember { mutableIntStateOf(0) }
            var mostrarAjustes by remember { mutableStateOf(false) }
            val solicitudes by viewModel.solicitudes.collectAsState()
            val mensajesRecibidos by viewModel.mensajesRecibidos.collectAsState()
            val publicaciones by viewModel.publicaciones.collectAsState()
            val estadoCartas by cartasViewModel.estado.collectAsState()
            val estadoLibreria by libreriaViewModel.estado.collectAsState()
            // Al entrar a Cartas se refresca el buzón (no hay empuje en tiempo real).
            LaunchedEffect(pestanaSocial) { if (pestanaSocial == PESTANA_CARTAS) cartasViewModel.recargar() }

            val context = LocalContext.current
            // Avisos del ViewModel (p. ej. no se pudo publicar o cambiar la privacidad).
            LaunchedEffect(viewModel) {
                viewModel.avisos.collect { aviso ->
                    android.widget.Toast.makeText(context, aviso, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            val launcherFotoMatrix = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        val inputStream = context.contentResolver.openInputStream(uri) ?: return@runCatching
                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        if (originalBitmap != null) {
                            val maxDim = 600
                            val width = originalBitmap.width
                            val height = originalBitmap.height
                            val ratio = width.toFloat() / height.toFloat()
                            val (targetW, targetH) = if (width > height) {
                                val w = minOf(width, maxDim)
                                Pair(w, (w / ratio).toInt())
                            } else {
                                val h = minOf(height, maxDim)
                                Pair((h * ratio).toInt(), h)
                            }
                            val scaled = originalBitmap.scale(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
                            val out = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                            val compressedBytes = out.toByteArray()
                            val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                            val dataUrl = "data:image/jpeg;base64,$base64"
                            viewModel.publicarFotoMatrix(dataUrl)
                        }
                    }
                }
            }

            val misNotas = publicaciones.filter {
                it.usuarioId == actual.usuario.id &&
                        it.tipo == TipoPublicacionPerfil.TEXTO_RETRO &&
                        it.estaVigenteSemanal()
            }
            val misFotos = publicaciones.filter {
                it.usuarioId == actual.usuario.id &&
                        it.tipo == TipoPublicacionPerfil.FOTO_MATRIX &&
                        it.estaVigenteSemanal()
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ====================================================
                // 1. CABECERA SOCIAL DEL PERFIL
                // ====================================================
                CabeceraSocialPerfil(
                    fotoUrl = actual.usuario.foto,
                    nombre = actual.usuario.nombre,
                    descripcion = actual.usuario.descripcion,
                    hobby = actual.usuario.hobby,
                    deporte = actual.usuario.deporte,
                    ritmoMusical = actual.usuario.ritmoMusical,
                    serieFavorita = actual.usuario.serieFavorita,
                    libroFavorito = actual.usuario.libroFavorito,
                    signoZodiacal = actual.usuario.signoZodiacal,
                    subiendoFoto = actual.subiendoFoto,
                    onSeleccionarImagen = { uriParaRecorte = it },
                    portadaUrl = actual.usuario.portada,
                    subiendoPortada = actual.subiendoPortada,
                    onSeleccionarPortada = { uriParaPortada = it },
                    onQuitarPortada = { confirmarQuitarPortada = true },
                    onAjustesClick = { mostrarAjustes = true },
                    pestanaActiva = pestanaSocial,
                    onPestanaCambia = { pestanaSocial = it },
                    numNotas = misNotas.size,
                    numFotos = misFotos.size,
                    numAmigos = amigos.size,
                    numSolicitudes = solicitudes.size,
                    numMensajes = mensajesRecibidos.size,
                    numCartasSinLeer = estadoCartas.sinLeer,
                    numObras = estadoLibreria.obras.size
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                // ====================================================
                // 2. CONTENIDO SEGÚN PESTAÑA SOCIAL
                // ====================================================
                if (pestanaSocial == PESTANA_AMIGOS) {
                    SelectorPildoras(
                        opciones = listOf("Mis amigos (${amigos.size})", "Mis solicitudes (${solicitudes.size})"),
                        seleccion = subPestanaAmigos,
                        onSeleccion = { subPestanaAmigos = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (pestanaSocial == PESTANA_BITACORA) {
                    // BITÁCORA: el compositor y todas las publicaciones (texto e imagen)
                    // comparten una sola línea temporal.
                    CajaEscribirNotaRetro(
                        onPublicar = { viewModel.publicarNotaTexto(it) },
                        onSubirImagen = {
                            launcherFotoMatrix.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val publicacionesBitacora = (misNotas + misFotos).sortedByDescending { it.timestamp }
                    if (publicacionesBitacora.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✧", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tu bitácora empieza aquí",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Escribe una nota o comparte una imagen. Las publicaciones actuales conservan su vigencia de siete días.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            publicacionesBitacora.forEach { publicacion ->
                                if (publicacion.tipo == TipoPublicacionPerfil.TEXTO_RETRO) {
                                    TarjetaNotaRetro(
                                        publicacion = publicacion,
                                        esPropia = true,
                                        usuarioIdActual = actual.usuario.id,
                                        onLikeClick = { viewModel.toggleLike(publicacion.id) },
                                        onEliminarClick = { viewModel.eliminarPublicacion(publicacion.id) }
                                    )
                                } else {
                                    TarjetaFotoMatrix(
                                        publicacion = publicacion,
                                        esPropia = true,
                                        usuarioIdActual = actual.usuario.id,
                                        onLikeClick = { viewModel.toggleLike(publicacion.id) },
                                        onEliminarClick = { viewModel.eliminarPublicacion(publicacion.id) }
                                    )
                                }
                            }
                        }
                    }
                } else if (pestanaSocial == PESTANA_AMIGOS && subPestanaAmigos == 0) {
                    // Amigos confirmados; las solicitudes se consultan en el selector interno.
                    if (amigos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("👥", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Aún no tienes amigos agregados",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Toca el perfil de cualquier persona para enviarle una solicitud de amistad.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            amigos.forEach { amigo ->
                                TarjetaAmigoSocial(
                                    amigo = amigo,
                                    onAbrirChat = {
                                        onAbrirChatConUsuario?.invoke(amigo.id, amigo.nombre)
                                            ?: onAbrirChat?.invoke(amigo.id, amigo.nombre)
                                    },
                                    onIniciarVideollamada = {
                                        onIniciarVideollamada?.invoke(amigo.id, amigo.nombre, amigo.foto)
                                    },
                                    onVerPerfil = {
                                        onVerPerfilAmigo?.invoke(amigo.id)
                                    },
                                    onEliminar = {
                                        viewModel.eliminarAmigo(amigo.id)
                                    }
                                )
                            }
                        }
                    }
                } else if (pestanaSocial == PESTANA_AMIGOS && subPestanaAmigos == 1) {
                    // Solicitudes dentro de la sección Amigos.
                    if (solicitudes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📩", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No tienes solicitudes de amistad pendientes",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Cuando alguien te envíe una solicitud de amistad aparecerá aquí para que la aceptes.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            solicitudes.forEach { solicitud ->
                                TarjetaSolicitudAmistad(
                                    solicitud = solicitud,
                                    onAceptar = { viewModel.aceptarSolicitud(solicitud) },
                                    onRechazar = { viewModel.rechazarSolicitud(solicitud.remitenteId) }
                                )
                            }
                        }
                    }
                } else if (pestanaSocial == PESTANA_CARTAS) {
                    CartasContenido(
                        estado = estadoCartas,
                        onRecargar = cartasViewModel::recargar,
                        onAbrir = cartasViewModel::abrir,
                        onEliminar = cartasViewModel::eliminar,
                        onConsumirMensaje = cartasViewModel::consumirMensaje
                    )
                } else if (pestanaSocial == PESTANA_LIBRERIA) {
                    LibreriaContenido(
                        estado = estadoLibreria,
                        onPublicar = libreriaViewModel::publicar,
                        onEliminar = libreriaViewModel::eliminar,
                        onCompartirEnMapa = libreriaViewModel::compartirEnMapa,
                        onAbrirPdf = libreriaViewModel::abrirPdf,
                        onRecargar = libreriaViewModel::recargar,
                        onConsumirMensaje = libreriaViewModel::consumirMensaje
                    )
                } else {
                    // Pestaña de Mensajes Directos Recibidos
                    if (mensajesRecibidos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, RadarGreen.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💬", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bandeja de mensajes directos",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Aquí aparecen tus conversaciones privadas y mensajes directos 1-a-1.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            mensajesRecibidos.forEach { item ->
                                TarjetaMensajeRecibido(
                                    item = item,
                                    onAbrirChat = {
                                        onAbrirChat?.invoke(item.chatId, "Chat: ${item.remitenteNombre}")
                                    }
                                )
                            }
                        }
                    }
                }
                }
            }

            // ====================================================
            // DIÁLOGOS MODALES TÁCTICOS (AJUSTES; la visibilidad se cambia ahí)
            // ====================================================
            if (mostrarAjustes) {
                // Ajuste local del teléfono (no se guarda en el servidor): se aplica al instante.
                val contextoAjustes = androidx.compose.ui.platform.LocalContext.current
                var compartirPorCercania by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        com.renea.arete_social.data.local.ajustes.AjustesPases.compartirActivado(contextoAjustes)
                    )
                }
                var minutosInactividad by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        com.renea.arete_social.data.local.ajustes.AjustesInactividadUbicacion.minutosInactividad(contextoAjustes)
                    )
                }
                var soloTextoEnDatos by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        com.renea.arete_social.data.local.ajustes.AjustesRed.soloTextoEnDatosMoviles(contextoAjustes)
                    )
                }
                DialogoAjustesPerfil(
                    nombre = nombre,
                    onNombreCambia = { nombre = it },
                    descripcion = descripcion,
                    onDescripcionCambia = { descripcion = it },
                    visibilidad = actual.usuario.estadoVisibilidad,
                    onVisibilidadCambia = { viewModel.actualizarVisibilidad(it) },
                    perfilPrivado = actual.usuario.perfilPrivado,
                    onPerfilPrivadoCambia = { viewModel.actualizarPerfilPrivado(it) },
                    compartirPorCercania = compartirPorCercania,
                    onCompartirPorCercaniaCambia = {
                        compartirPorCercania = it
                        com.renea.arete_social.data.local.ajustes.AjustesPases.definirCompartir(contextoAjustes, it)
                    },
                    minutosInactividad = minutosInactividad,
                    onMinutosInactividadCambia = {
                        minutosInactividad = it
                        com.renea.arete_social.data.local.ajustes.AjustesInactividadUbicacion.definirMinutosInactividad(contextoAjustes, it)
                    },
                    soloTextoEnDatosMoviles = soloTextoEnDatos,
                    onSoloTextoEnDatosMovilesCambia = {
                        soloTextoEnDatos = it
                        com.renea.arete_social.data.local.ajustes.AjustesRed.definirSoloTextoEnDatosMoviles(contextoAjustes, it)
                    },
                    guardando = actual.guardando,
                    onGuardar = {
                        viewModel.actualizarPerfil(nombre, descripcion)
                        mostrarAjustes = false
                    },
                    onEditarGustos = {
                        mostrarAjustes = false
                        mostrarPerfilInicial = true
                    },
                    onGestionarIdentidad = onGestionarIdentidad?.let { callback ->
                        {
                            mostrarAjustes = false
                            callback()
                        }
                    },
                    onCerrarSesion = {
                        mostrarAjustes = false
                        onCerrarSesion()
                    },
                    onEliminarCuenta = {
                        mostrarAjustes = false
                        onEliminarCuenta()
                    },
                    onDismiss = { mostrarAjustes = false }
                )
            }
        }
    }
    }
}

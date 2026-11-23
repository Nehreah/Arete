package com.renea.arete_social.gui.auth.perfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.TipoPublicacionPerfil
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.feature.auth.BluetoothPerfilUiState
import com.renea.arete_social.feature.auth.PerfilPublicoUiState
import com.renea.arete_social.feature.auth.PerfilPublicoViewModel
import com.renea.arete_social.gui.libreria.LibreriaSoloLectura
import com.renea.arete_social.gui.moderacion.DialogoConfirmarBloqueo
import com.renea.arete_social.gui.moderacion.DialogoConfirmarDesbloqueo
import com.renea.arete_social.gui.moderacion.DialogoReportar
import com.renea.arete_social.gui.theme.BotonPildora
import com.renea.arete_social.gui.theme.EstadoVacioSocial
import com.renea.arete_social.gui.theme.RadarErrorScreen
import com.renea.arete_social.gui.theme.RadarLoading
import com.renea.arete_social.gui.theme.RadarLoadingScreen
import kotlinx.coroutines.delay

private const val TAB_PUB_BITACORA = 0
private const val TAB_PUB_CARTAS = 1
private const val TAB_PUB_LIBRERIA = 2

/**
 * Perfil de OTRA persona, con la misma estructura que el perfil propio (sin Amigos ni Mensajes):
 *  - Cabecera: portada a todo el ancho con foto, nombre, descripción y gustos encima,
 *    y las pestañas solapadas sobre su borde inferior.
 *  - Acciones: agregar / aceptar / rechazar / cancelar solicitud / eliminar amistad,
 *    mensaje, videollamada, carta y mascota.
 *  - Pestañas: Bitácora (notas y fotos de la semana), Librería y Cartas (la correspondencia entre ambos).
 *  - Si el perfil es privado y no son amistades, en vez de las pestañas se muestra un candado:
 *    el servidor tampoco entrega ese contenido.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PerfilPublicoScreen(
    viewModel: PerfilPublicoViewModel,
    modifier: Modifier = Modifier,
    onAbrirChat: (chatId: String, nombre: String) -> Unit = { _, _ -> },
    onIniciarVideollamada: (contactoId: String, nombre: String, foto: String?) -> Unit = { _, _, _ -> },
    onVisitarHabitacion: (usuarioId: String, nombre: String) -> Unit = { _, _ -> },
    onEncontrarnos: (usuarioId: String, nombre: String, ubicacion: com.renea.arete_social.domain.model.Ubicacion?) -> Unit = { _, _, _ -> }
) {
    val estado by viewModel.estado.collectAsState()
    val publicaciones by viewModel.publicaciones.collectAsState()
    val cartasEstado by viewModel.cartas.collectAsState()
    val libreriaEstado by viewModel.libreria.collectAsState()
    val bluetoothEstado by viewModel.bluetooth.collectAsState()
    var refrescando by remember { mutableStateOf(false) }
    if (estado !is PerfilPublicoUiState.Cargando) refrescando = false

    var pestana by remember { mutableIntStateOf(0) }
    var redactando by remember { mutableStateOf(false) }
    var cartaAbierta by remember { mutableStateOf<Carta?>(null) }
    var confirmarEliminarAmistad by remember { mutableStateOf(false) }
    var mostrarDialogoReportar by remember { mutableStateOf(false) }
    var mostrarDialogoBloquear by remember { mutableStateOf(false) }
    var mostrarDialogoDesbloquear by remember { mutableStateOf(false) }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.cargarContenido() },
        modifier = modifier
    ) {
        when (val actual = estado) {
            is PerfilPublicoUiState.Cargando -> RadarLoadingScreen()

            is PerfilPublicoUiState.Error -> RadarErrorScreen(mensaje = actual.mensaje)

            is PerfilPublicoUiState.Listo -> {
            val usuario = actual.usuario
            // Bitácora: notas y fotos juntas en una sola línea de tiempo, de la más nueva a la más antigua.
            val bitacora = publicaciones
                .filter { it.usuarioId == usuario.id && it.estaVigenteSemanal() }
                .sortedByDescending { it.timestamp }
            val mostrarCartas = !actual.esPropio

            // Pestañas visibles: Bitácora, Cartas (solo con otra persona) y Librería. Cada una guarda su id.
            val pestanas = buildList {
                add(TAB_PUB_BITACORA)
                if (mostrarCartas) add(TAB_PUB_CARTAS)
                add(TAB_PUB_LIBRERIA)
            }
            val pestanaActiva = if (pestana in pestanas) pestana else TAB_PUB_BITACORA
            val itemsPestanas = pestanas.map { id ->
                when (id) {
                    TAB_PUB_BITACORA -> Triple("▤", "Bitácora", bitacora.size)
                    TAB_PUB_CARTAS -> Triple("✎", "Cartas", cartasEstado.cartas.size)
                    else -> Triple("❦", "Librería", libreriaEstado.obras.size)
                }
            }

            Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CabeceraPerfil(
                    usuario = usuario,
                    esAmigo = actual.esAmigo,
                    esPropio = actual.esPropio,
                    mostrarGustos = actual.contenidoVisible,
                    conPestanas = actual.contenidoVisible,
                    items = itemsPestanas,
                    pestanaActiva = pestanas.indexOf(pestanaActiva),
                    onPestanaCambia = { pestana = pestanas[it] }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!actual.esPropio) {
                        AccionesPerfil(
                            actual = actual,
                            onAgregar = viewModel::enviarSolicitudAmistad,
                            onCancelarSolicitud = viewModel::cancelarSolicitudAmistad,
                            onAceptar = viewModel::aceptarSolicitudAmistad,
                            onRechazar = viewModel::rechazarSolicitudAmistad,
                            onEliminarAmigo = { confirmarEliminarAmistad = true },
                            onMensaje = { viewModel.abrirChatDirecto(onAbrirChat) },
                            onVideo = { onIniciarVideollamada(usuario.id, usuario.nombre, usuario.foto) },
                            onCarta = {
                                pestana = TAB_PUB_CARTAS
                                if (actual.esAmigo && cartasEstado.disponible) redactando = true
                            },
                            onMascota = { onVisitarHabitacion(usuario.id, usuario.nombre) },
                            onEncontrarnos = { onEncontrarnos(usuario.id, usuario.nombre, usuario.ubicacion) },
                            onBloquear = { mostrarDialogoBloquear = true },
                            onDesbloquear = { mostrarDialogoDesbloquear = true },
                            onReportar = { mostrarDialogoReportar = true }
                        )
                    }

                    // Aviso de moderación (éxito al reportar o bloquear).
                    actual.avisoModeracion?.let { aviso ->
                        LaunchedEffect(aviso) {
                            delay(4_000)
                            viewModel.consumirAvisoModeracion()
                        }
                        Text(
                            text = aviso,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Aviso breve si falló la última acción (solicitud, aceptar, abrir PDF…).
                    actual.error?.let { mensaje ->
                        LaunchedEffect(mensaje) {
                            delay(3_500)
                            viewModel.consumirError()
                        }
                        Text(
                            text = mensaje,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (actual.estaBloqueado) {
                        EstadoVacioSocial(
                            icono = "🚫",
                            titulo = "Usuario bloqueado",
                            subtitulo = "Has bloqueado a ${usuario.nombre}. Sus publicaciones y actividades están ocultas para ti."
                        )
                        return@Column
                    }

                    if (!actual.contenidoVisible) {
                        // Perfil privado y sin amistad: lo único visible es el candado.
                        PerfilPrivadoBloqueado(nombre = usuario.nombre, solicitudEnviada = actual.solicitudEnviada)
                        return@Column
                    }

                    // Sin internet: ver por Bluetooth el perfil de alguien cercano (solo texto).
                    if (!actual.esPropio) {
                        BloqueBluetooth(
                            estado = bluetoothEstado,
                            onBuscar = viewModel::buscarPorBluetooth,
                            onMensajeVisto = viewModel::consumirMensajeBluetooth
                        )
                    }

                    when (pestanaActiva) {
                        TAB_PUB_BITACORA -> {
                            if (bitacora.isEmpty()) {
                                EstadoVacioSocial(
                                    icono = "📜",
                                    titulo = "Bitácora vacía",
                                    subtitulo = "${usuario.nombre} no ha publicado notas ni fotos esta semana."
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    bitacora.forEach { publicacion ->
                                        if (publicacion.tipo == TipoPublicacionPerfil.TEXTO_RETRO) {
                                            TarjetaNotaRetro(
                                                publicacion = publicacion,
                                                esPropia = false,
                                                usuarioIdActual = viewModel.usuarioIdActual,
                                                onLikeClick = { viewModel.toggleLike(publicacion.id) }
                                            )
                                        } else {
                                            TarjetaFotoMatrix(
                                                publicacion = publicacion,
                                                esPropia = false,
                                                usuarioIdActual = viewModel.usuarioIdActual,
                                                onLikeClick = { viewModel.toggleLike(publicacion.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TAB_PUB_LIBRERIA -> LibreriaSoloLectura(
                            obras = libreriaEstado.obras,
                            cargando = libreriaEstado.cargando,
                            error = libreriaEstado.error,
                            nombreAutor = usuario.nombre,
                            onAbrirPdf = viewModel::abrirPdf,
                            onRecargar = viewModel::cargarLibreria
                        )

                        else -> CartasPerfilPublico(
                            estado = cartasEstado,
                            nombreOtro = usuario.nombre,
                            miUsuarioId = viewModel.usuarioIdActual,
                            esAmigo = actual.esAmigo,
                            perfilPrivado = usuario.perfilPrivado,
                            onEscribir = { redactando = true },
                            onRecargar = viewModel::cargarCartas,
                            onAbrir = { carta ->
                                cartaAbierta = carta
                                viewModel.abrirCarta(carta)
                            },
                            onConsumirMensaje = viewModel::consumirMensajeCartas
                        )
                    }
                }
            }

            if (redactando) {
                DialogoEscribirCartaPerfil(
                    nombreDestinatario = usuario.nombre,
                    enviando = cartasEstado.enviando,
                    onEnviar = { texto, fin ->
                        viewModel.enviarCarta(texto) { error ->
                            if (error == null) redactando = false
                            fin(error)
                        }
                    },
                    onCerrar = { redactando = false }
                )
            }

            cartaAbierta?.let { carta ->
                DialogoLeerCartaPerfil(
                    carta = carta,
                    esRecibida = carta.destinatarioId == viewModel.usuarioIdActual,
                    onCerrar = { cartaAbierta = null },
                    onEliminar = {
                        viewModel.eliminarCarta(carta)
                        cartaAbierta = null
                    }
                )
            }

            if (confirmarEliminarAmistad) {
                AlertDialog(
                    onDismissRequest = { confirmarEliminarAmistad = false },
                    title = { Text("¿Dejar de ser amigos?") },
                    text = { Text("${usuario.nombre} dejará de aparecer en tu lista de amigos. Podrás enviarle otra solicitud más adelante.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmarEliminarAmistad = false
                            viewModel.eliminarAmigo()
                        }) { Text("Eliminar amistad") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmarEliminarAmistad = false }) { Text("Cancelar") }
                    }
                )
            }

            if (mostrarDialogoReportar) {
                DialogoReportar(
                    titulo = "Reportar a ${usuario.nombre}",
                    nombreObjetivo = usuario.nombre,
                    mostrarOpcionBloquear = !actual.estaBloqueado,
                    onDismiss = { mostrarDialogoReportar = false },
                    onConfirmar = { motivo, descripcion, bloquearTambien ->
                        mostrarDialogoReportar = false
                        viewModel.enviarReporte(motivo, descripcion, bloquearTambien)
                    }
                )
            }

            if (mostrarDialogoBloquear) {
                DialogoConfirmarBloqueo(
                    nombre = usuario.nombre,
                    onDismiss = { mostrarDialogoBloquear = false },
                    onConfirmar = {
                        mostrarDialogoBloquear = false
                        viewModel.bloquearUsuario()
                    }
                )
            }

            if (mostrarDialogoDesbloquear) {
                DialogoConfirmarDesbloqueo(
                    nombre = usuario.nombre,
                    onDismiss = { mostrarDialogoDesbloquear = false },
                    onConfirmar = {
                        mostrarDialogoDesbloquear = false
                        viewModel.desbloquearUsuario()
                    }
                )
            }
        }
    }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cabecera
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CabeceraPerfil(
    usuario: Usuario,
    esAmigo: Boolean,
    esPropio: Boolean,
    mostrarGustos: Boolean,
    conPestanas: Boolean,
    items: List<Triple<String, String, Int>>,
    pestanaActiva: Int,
    onPestanaCambia: (Int) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val gustos = if (mostrarGustos) {
        listOf(
            "Hobby" to usuario.hobby,
            "Deporte" to usuario.deporte,
            "Música" to usuario.ritmoMusical,
            "Serie" to usuario.serieFavorita,
            "Libro" to usuario.libroFavorito,
            "Signo" to usuario.signoZodiacal
        ).filter { !it.second.isNullOrBlank() }
    } else emptyList()

    val alturaPortada = AlturaPortadaPerfil
    val solapePestanas = SolapePestanasPerfil
    val alturaPestanas = AlturaPestanasPerfil
    val formaPortada = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    val alturaTotal = if (conPestanas) alturaPortada + alturaPestanas - solapePestanas else alturaPortada

    Box(Modifier.fillMaxWidth().height(alturaTotal)) {

        // ── Portada a todo el ancho ─────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(alturaPortada).clip(formaPortada)) {
            if (!usuario.portada.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = usuario.portada,
                    contentDescription = "Portada de ${usuario.nombre}",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Black.copy(alpha = .72f)))))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .78f), Color(0xFF17171B)))))
                Canvas(Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(0f, size.height * .30f)
                        cubicTo(size.width * .24f, size.height * .02f, size.width * .28f, size.height * .55f, size.width * .52f, size.height * .28f)
                        cubicTo(size.width * .72f, size.height * .02f, size.width * .82f, size.height * .55f, size.width, size.height * .26f)
                        lineTo(size.width, 0f); lineTo(0f, 0f); close()
                    }
                    drawPath(path, Color.White.copy(alpha = .10f))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .50f)))))
            }
        }

        // ── Foto + datos, encima de la portada (igual que en el perfil propio) ──
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(
                    start = 20.dp, end = 20.dp, top = 20.dp,
                    bottom = if (conPestanas) solapePestanas + 4.dp else 20.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(74.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
                    .border(2.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!usuario.foto.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = usuario.foto,
                        contentDescription = "Foto de ${usuario.nombre}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(usuario.nombre.firstOrNull()?.uppercase() ?: "U", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = usuario.nombre.ifBlank { "Usuario" },
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                val insignia = when {
                    esPropio -> if (usuario.perfilPrivado) "🔒 Tu perfil privado" else "🌐 Tu perfil público"
                    esAmigo -> "👥 Amistad"
                    usuario.perfilPrivado -> "🔒 Perfil privado"
                    else -> "🌐 Perfil público"
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B31)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(insignia, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (usuario.descripcion.isNotBlank()) {
                    Text(usuario.descripcion, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2)
                }
                if (gustos.isNotEmpty()) {
                    Text(
                        gustos.joinToString("  ·  ") { "${it.first}: ${it.second}" },
                        color = Color.White.copy(alpha = .92f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 3
                    )
                }
            }
        }

        // ── Pestañas solapadas sobre el borde de la portada ─────────────────
        if (conPestanas) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(alturaPestanas).padding(horizontal = 20.dp)) {
                BarraPestanasSocial(items, emptyMap(), pestanaActiva, onPestanaCambia)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Acciones sociales
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccionesPerfil(
    actual: PerfilPublicoUiState.Listo,
    onAgregar: () -> Unit,
    onCancelarSolicitud: () -> Unit,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit,
    onEliminarAmigo: () -> Unit,
    onMensaje: () -> Unit,
    onVideo: () -> Unit,
    onCarta: () -> Unit,
    onMascota: () -> Unit,
    onEncontrarnos: () -> Unit,
    onBloquear: () -> Unit,
    onDesbloquear: () -> Unit,
    onReportar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (actual.estaBloqueado) {
            BotonPildora(
                texto = "🚫  Usuario bloqueado · Toca para desbloquear",
                onClick = onDesbloquear,
                destacado = false,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                BotonPildora("🚩 Reportar usuario", onClick = onReportar, destacado = false)
            }
            return@Column
        }

        // Fila principal: el estado de la amistad manda, con una acción clara por estado.
        when {
            actual.esAmigo -> BotonPildora(
                texto = "✓  Amigos",
                onClick = onEliminarAmigo,
                destacado = false,
                modifier = Modifier.fillMaxWidth()
            )

            actual.solicitudRecibida -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPildora("Aceptar solicitud", onClick = onAceptar, modifier = Modifier.weight(1f))
                BotonPildora("Rechazar", onClick = onRechazar, destacado = false, modifier = Modifier.weight(1f))
            }

            actual.solicitudEnviada -> BotonPildora(
                texto = "⏳  Solicitud enviada · Cancelar",
                onClick = onCancelarSolicitud,
                destacado = false,
                modifier = Modifier.fillMaxWidth()
            )

            else -> BotonPildora(
                texto = "＋  Enviar solicitud de amistad",
                onClick = onAgregar,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (actual.abriendoChat) {
            RadarLoading(modifier = Modifier.fillMaxWidth().height(40.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BotonPildora("💬 Mensaje", onClick = onMensaje, destacado = false, modifier = Modifier.weight(1f))
                // Sin amistad con un perfil privado no hay cartas: el servidor solo las permite entre amistades.
                if (actual.contenidoVisible) {
                    BotonPildora("✉ Carta", onClick = onCarta, destacado = false, modifier = Modifier.weight(1f))
                }
                BotonPildora("📹 Video", onClick = onVideo, destacado = false, modifier = Modifier.weight(1f))
                BotonPildora("🐾", onClick = onMascota, destacado = false)
            }
        }

        if (actual.esAmigo) {
            BotonPildora(
                texto = "📍  Encontrarnos en el mapa",
                onClick = onEncontrarnos,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Opciones de moderación y seguridad comunitaria
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPildora("🚫 Bloquear", onClick = onBloquear, destacado = false, modifier = Modifier.weight(1f))
            BotonPildora("🚩 Reportar", onClick = onReportar, destacado = false, modifier = Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Perfil privado
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PerfilPrivadoBloqueado(nombre: String, solicitudEnviada: Boolean) {
    EstadoVacioSocial(
        icono = "🔒",
        titulo = "Este perfil es privado",
        subtitulo = if (solicitudEnviada) {
            "Cuando $nombre acepte tu solicitud podrás ver su bitácora y su librería."
        } else {
            "Solo las amistades de $nombre ven su bitácora y su librería. Envíale una solicitud de amistad."
        }
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Perfil por Bluetooth (solo texto)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BloqueBluetooth(
    estado: BluetoothPerfilUiState,
    onBuscar: () -> Unit,
    onMensajeVisto: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BotonPildora(
            if (estado.buscando) "📡 Buscando por Bluetooth…" else "📡 Ver por Bluetooth",
            onClick = { if (!estado.buscando) onBuscar() },
            destacado = false,
            modifier = Modifier.fillMaxWidth()
        )
        estado.copiaRecibidaEn?.let { recibidaEn ->
            Text(
                text = "📶 Copia recibida por Bluetooth ${tiempoDesde(recibidaEn)}: solo texto. Las fotos y los PDF necesitan internet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        estado.mensaje?.let { mensaje ->
            LaunchedEffect(mensaje) {
                delay(4_000)
                onMensajeVisto()
            }
            Text(
                text = mensaje,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun tiempoDesde(marca: Long): String {
    val minutos = ((System.currentTimeMillis() - marca).coerceAtLeast(0)) / 60_000
    return when {
        minutos < 1 -> "ahora"
        minutos < 60 -> "hace $minutos min"
        minutos < 24 * 60 -> "hace ${minutos / 60} h"
        else -> "hace ${minutos / (24 * 60)} d"
    }
}

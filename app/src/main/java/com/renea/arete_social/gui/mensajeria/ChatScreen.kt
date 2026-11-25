package com.renea.arete_social.gui.mensajeria

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.MotivoReporte
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.feature.auth.MensajeDirectoItem
import com.renea.arete_social.feature.mensajeria.ChatUiState
import com.renea.arete_social.feature.mensajeria.ChatViewModel
import com.renea.arete_social.feature.mensajeria.InterlocutorUi
import com.renea.arete_social.feature.mensajeria.MensajeUi
import com.renea.arete_social.gui.moderacion.DialogoConfirmarBloqueo
import com.renea.arete_social.gui.moderacion.DialogoReportar
import com.renea.arete_social.gui.theme.AvatarUsuario
import com.renea.arete_social.gui.theme.RadarErrorScreen
import com.renea.arete_social.gui.theme.RadarLoadingScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    chatId: String = "",
    tituloChat: String = "Chat",
    gestorLlamadas: com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio? = null,
    onIniciarVideollamada: (() -> Unit)? = null,
    onVerPerfil: ((usuarioId: String) -> Unit)? = null,
    onVerComunidad: ((comunidadId: String) -> Unit)? = null,
    onVerEvento: ((eventoId: String) -> Unit)? = null,
    listaComunidades: List<Comunidad> = emptyList(),
    listaEventos: List<Evento> = emptyList(),
    mensajesRecibidos: List<MensajeDirectoItem> = emptyList(),
    personasCercanas: List<Dispositivo> = emptyList(),
    listaAmigos: List<com.renea.arete_social.domain.model.Usuario> = emptyList(),
    salas: List<Sala> = emptyList(),
    /** chatId del chat regional que se destaca (el de la región más cercana). */
    regionDestacadaChatId: String? = null,
    /** chatId de lugares/eventos visibles → origen y vencimiento (etiquetas de la lista). */
    visibilidadPorChat: Map<String, com.renea.arete_social.domain.model.VisibilidadMapa> = emptyMap(),
    onSeleccionarChat: ((chatId: String, titulo: String) -> Unit)? = null,
    /** Abre/crea el chat directo con una PERSONA (pestañas Amigos y Cercanos). */
    onSeleccionarUsuario: ((usuarioId: String, titulo: String) -> Unit)? = null,
    onVolverAlMapa: (() -> Unit)? = null,
    onCrearSala: (() -> Unit)? = null,
    esVistaHub: Boolean = false,
    vistaSoloSalas: Boolean = false,
    ocultarBarraSuperior: Boolean = false
) {
    val estado by viewModel.estado.collectAsState()
    val alertaTofuPeerId by viewModel.alertaTofuPeerId.collectAsState()
    val audioEstado by (gestorLlamadas?.audioChatEstado?.collectAsState()
        ?: remember { mutableStateOf(com.renea.arete_social.domain.model.EstadoAudioChat()) })

    var pestanaActiva by rememberSaveable { mutableIntStateOf(if (vistaSoloSalas) 3 else 0) }
    LaunchedEffect(vistaSoloSalas) { if (vistaSoloSalas) pestanaActiva = 0; if (pestanaActiva == 3) pestanaActiva = 0 }
    LaunchedEffect(chatId, esVistaHub) {
        if (!esVistaHub && chatId.isNotBlank() && chatId != com.renea.arete_social.domain.repository.ID_CHAT_GENERAL) {
            pestanaActiva = 0
        }
    }
    var textoNuevo by rememberSaveable { mutableStateOf("") }
    var mostrarDialogoAdjuntar by remember { mutableStateOf(false) }
    val estadoLista = rememberLazyListState()
    var refrescando by remember { mutableStateOf(false) }
    if (estado !is ChatUiState.Cargando) refrescando = false

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.reanudar() },
        modifier = modifier
    ) {
        when (val actual = estado) {
            is ChatUiState.Cargando -> RadarLoadingScreen()

            is ChatUiState.Error -> RadarErrorScreen(mensaje = actual.mensaje)

            is ChatUiState.Listo -> {
            androidx.compose.runtime.DisposableEffect(viewModel, chatId, esVistaHub) {
                if (!esVistaHub && chatId.isNotBlank()) {
                    viewModel.reanudar()
                } else {
                    viewModel.pausar()
                }
                onDispose {
                    viewModel.pausar()
                }
            }

            // Sintonizar canal de audio con este chat cuando no estemos en hub
            LaunchedEffect(chatId, esVistaHub) {
                if (!esVistaHub && chatId.isNotBlank()) {
                    gestorLlamadas?.sintonizarCanalAudio(chatId, "Chat Activo")
                }
            }

            // Baja la lista al último mensaje cada vez que llega uno nuevo
            LaunchedEffect(actual.mensajes.size) {
                if (!esVistaHub && actual.mensajes.isNotEmpty()) {
                    estadoLista.animateScrollToItem(actual.mensajes.size - 1)
                }
            }

            var mostrarDialogoReportar by remember { mutableStateOf(false) }
            var mostrarDialogoBloquear by remember { mutableStateOf(false) }
            var mostrarMenuModeración by remember { mutableStateOf(false) }
            var mostrarDialogoEliminarConversacion by remember { mutableStateOf(false) }
            var conversacionAEliminarPorItem by remember { mutableStateOf<MensajeDirectoItem?>(null) }
            var mensajeAEliminar by remember { mutableStateOf<MensajeUi?>(null) }

            // Toast auto-dismiss de 4 s para aviso de moderación
            LaunchedEffect(actual.avisoModeracion) {
                if (actual.avisoModeracion != null) {
                    delay(4_000)
                    viewModel.consumirAvisoModeracion()
                }
            }

            if (esVistaHub) {
                // ====================================================
                // VISTA HUB: 3 PESTAÑAS SEGÚN MOCKUP Chats.jpeg
                // ====================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // 1. Barra Superior con icono 💬 Chat y botón 👥
                    BarraSuperiorChat(
                        titulo = if (vistaSoloSalas) "Salas" else "Chat",
                        mostrarBotonSalas = false,
                        onAtras = { onVolverAlMapa?.invoke() },
                        onAccionDerecha = null
                    )

                    // 2. Selector Segmentado de Pestañas: Activos / Cercanos / Salas
                    if (!vistaSoloSalas) {
                        BarraPestanasChats(
                            pestanaActiva = pestanaActiva,
                            onPestanaCambia = { pestanaActiva = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 3. Lista de chats según la pestaña activa
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (pestanaActiva) {
                            0 -> {
                                // PESTAÑA 0: ACTIVOS (Chats activos con cualquier persona)
                                if (mensajesRecibidos.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("💬", fontSize = 36.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Sin mensajes activos",
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Toca 'Amigos' o 'Cercanos' para iniciar un chat.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(mensajesRecibidos, key = { it.chatId }) { item ->
                                            val estaCercano = remember(personasCercanas, item.remitenteId) {
                                                personasCercanas.any { p ->
                                                    (p.usuarioId != null && p.usuarioId == item.remitenteId) ||
                                                    p.id == item.remitenteId ||
                                                    (item.remitenteId.isNotBlank() && (p.id.contains(item.remitenteId) || item.remitenteId.contains(p.id)))
                                                }
                                            }
                                            ItemChatActivo(
                                                item = item,
                                                estaCercano = estaCercano,
                                                onClick = { onSeleccionarChat?.invoke(item.chatId, item.remitenteNombre) },
                                                onClickAvatar = if (item.remitenteId.isNotBlank() && onVerPerfil != null) {
                                                    { onVerPerfil(item.remitenteId) }
                                                } else null,
                                                onEliminar = { conversacionAEliminarPorItem = item }
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // PESTAÑA 1: AMIGOS (Lista de amigos para iniciar chat)
                                if (listaAmigos.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("👥", fontSize = 36.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Sin amigos agregados todavía",
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Agrega contactos desde la sección Comunidad o el Radar para enviarles mensajes.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(listaAmigos, key = { it.id }) { amigo ->
                                            ItemAmigoChat(
                                                usuario = amigo,
                                                onClickChat = {
                                                    onSeleccionarUsuario?.invoke(amigo.id, amigo.nombre)
                                                },
                                                onClickPerfil = {
                                                    onVerPerfil?.invoke(amigo.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // PESTAÑA 2: CERCANOS (Personas detectadas en el radar)
                                if (personasCercanas.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("📍", fontSize = 36.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "No hay personas cercanas detectadas",
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Cuando otros dispositivos Aréte entren en tu alcance de radar, aparecerán aquí.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(personasCercanas, key = { it.id }) { persona ->
                                            ItemPersonaCercana(
                                                dispositivo = persona,
                                                onChatClick = {
                                                    // Solo permitir chat si el dispositivo cercano
                                                    // ya compartió su usuarioId o perfil.
                                                    // Si usuarioId == null, el dispositivo es BLE puro
                                                    // sin perfil resuelto aún.
                                                    val uid = persona.usuarioId
                                                    if (uid != null) {
                                                        onSeleccionarUsuario?.invoke(uid, persona.nombre)
                                                    }
                                                    // Si uid == null: el ItemPersonaCercana mostrará
                                                    // el estado "Sincronizando..." y deshabilitará el botón.
                                                },
                                                onLlamadaClick = {
                                                    onIniciarVideollamada?.invoke()
                                                },
                                                onPerfilClick = persona.usuarioId?.let { uid ->
                                                    onVerPerfil?.let { abrir -> { abrir(uid) } }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            3 -> {
                                // PESTAÑA 3: SALAS (§5B.2 — Un solo Chat General + Comunidades + Radar)
                                val salaGeneral = remember(salas) {
                                    salas.firstOrNull {
                                        it.id == com.renea.arete_social.domain.repository.ID_SALA_GENERAL ||
                                        it.tipo == com.renea.arete_social.domain.model.TipoSala.GENERAL ||
                                        it.chatId == com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
                                    }
                                }
                                val salasRegionales = remember(salas) {
                                    salas.filter { it.tipo == com.renea.arete_social.domain.model.TipoSala.REGIONAL }
                                }
                                // Lugares y eventos que se ven ahora (cerca, recientes, por pase o por el Súper):
                                // son los que tienen etiqueta de visibilidad.
                                val salasZona = remember(salas, visibilidadPorChat) {
                                    salas.filter { it.chatId in visibilidadPorChat }
                                }
                                val salasComunidades = remember(salas) {
                                    salas.filter { it.tipo == com.renea.arete_social.domain.model.TipoSala.COMUNIDAD }
                                }
                                val salasOtras = remember(salas, visibilidadPorChat) {
                                    salas.filter {
                                        it != salaGeneral &&
                                            it.tipo != com.renea.arete_social.domain.model.TipoSala.COMUNIDAD &&
                                            it.tipo != com.renea.arete_social.domain.model.TipoSala.REGIONAL &&
                                            it.chatId !in visibilidadPorChat
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 1. Canal General Único
                                    item {
                                        ItemSalaChat(
                                            nombre = salaGeneral?.nombre ?: "Chat Aréte",
                                            descripcion = salaGeneral?.descripcion?.ifBlank { null }
                                                ?: "El chat general: abierto a todos",
                                            esProtegida = false,
                                            onClick = {
                                                onSeleccionarChat?.invoke(
                                                    salaGeneral?.chatId ?: com.renea.arete_social.domain.repository.ID_CHAT_GENERAL,
                                                    salaGeneral?.nombre ?: "Chat Aréte"
                                                )
                                            }
                                        )
                                    }

                                    // 2. Chats regionales: todos siempre visibles; el de tu región, destacado.
                                    if (salasRegionales.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "REGIONALES",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                        items(salasRegionales, key = { it.id }) { sala ->
                                            val destacada = sala.chatId == regionDestacadaChatId
                                            ItemSalaChat(
                                                nombre = sala.nombre,
                                                descripcion = sala.descripcion.ifBlank { "Chat de la región" },
                                                esProtegida = false,
                                                icono = "🌎",
                                                etiqueta = if (destacada) "Tu región" else null,
                                                destacada = destacada,
                                                onClick = { onSeleccionarChat?.invoke(sala.chatId, sala.nombre) }
                                            )
                                        }
                                    }

                                    // 3. Lugares y eventos que se ven en el mapa ahora mismo.
                                    if (salasZona.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "LUGARES Y EVENTOS A TU ALREDEDOR",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                        items(salasZona, key = { it.id }) { sala ->
                                            val ahora = System.currentTimeMillis()
                                            val etiqueta = visibilidadPorChat[sala.chatId]?.let {
                                                com.renea.arete_social.domain.util.etiquetaVisibilidad(it, ahora)
                                            }
                                            ItemSalaChat(
                                                nombre = sala.nombre,
                                                descripcion = sala.descripcion.ifBlank { "Chat del mapa" },
                                                esProtegida = false,
                                                icono = if (sala.tipo == com.renea.arete_social.domain.model.TipoSala.EVENTO) "📅" else "📍",
                                                etiqueta = etiqueta,
                                                destacada = visibilidadPorChat[sala.chatId]?.origen ==
                                                    com.renea.arete_social.domain.model.OrigenVisibilidad.SUPER,
                                                onClick = { onSeleccionarChat?.invoke(sala.chatId, sala.nombre) }
                                            )
                                        }
                                    }

                                    // 4. Salas de mis comunidades
                                    if (salasComunidades.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "MIS COMUNIDADES",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                        items(salasComunidades, key = { it.id }) { sala ->
                                            ItemSalaChat(
                                                nombre = sala.nombre,
                                                descripcion = sala.descripcion.ifBlank { "Canal de la comunidad" },
                                                esProtegida = false,
                                                onClick = { onSeleccionarChat?.invoke(sala.chatId, sala.nombre) }
                                            )
                                        }
                                    }

                                    // 5. Otras salas del radar
                                    if (salasOtras.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "SALAS DEL RADAR",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                        items(salasOtras, key = { it.id }) { sala ->
                                            ItemSalaChat(
                                                nombre = sala.nombre,
                                                descripcion = sala.descripcion.ifBlank {
                                                    if (sala.esPublica) "Sala pública del radar" else "Sala privada protegida con clave"
                                                },
                                                esProtegida = !sala.esPublica,
                                                onClick = { onSeleccionarChat?.invoke(sala.chatId, sala.nombre) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ====================================================
                // VISTA DE CONVERSACIÓN (CHAT ACTIVO)
                // ====================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Header de conversación
                    if (onVolverAlMapa != null && !ocultarBarraSuperior) {
                        val esChatIndividual = chatId != com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
                        BarraSuperiorChat(
                            titulo = tituloChat.ifBlank { "Chat" },
                            mostrarBotonSalas = esChatIndividual,
                            onAtras = { onVolverAlMapa() },
                            onAccionDerecha = if (esChatIndividual) {
                                { mostrarMenuModeración = true }
                            } else null
                        )
                    }

                    // Menú de opciones de conversación (DropdownMenu inline)
                    if (mostrarMenuModeración) {
                        androidx.compose.material3.DropdownMenu(
                            expanded = mostrarMenuModeración,
                            onDismissRequest = { mostrarMenuModeración = false }
                        ) {
                            if (actual.interlocutor != null) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("🚩 Reportar conversación") },
                                    onClick = { mostrarMenuModeración = false; mostrarDialogoReportar = true }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("🚫 Bloquear contacto") },
                                    onClick = { mostrarMenuModeración = false; mostrarDialogoBloquear = true }
                                )
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("🗑️ Eliminar conversación", color = MaterialTheme.colorScheme.error) },
                                onClick = { mostrarMenuModeración = false; mostrarDialogoEliminarConversacion = true }
                            )
                        }
                    }

                    // Toast de aviso de moderación
                    actual.avisoModeracion?.let { aviso ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = aviso,
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Cabecera de la persona (solo chats 1-a-1): foto + nombre, tocable → su perfil.
                    actual.interlocutor?.let { otro ->
                        CabeceraInterlocutor(
                            interlocutor = otro,
                            esE2ee = actual.esE2ee,
                            onClick = onVerPerfil?.let { abrir -> { abrir(otro.id) } }
                        )
                    }

                    // Barra de Canal de Voz / Estado PTT y Videollamada 1-a-1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Estado de Voz
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (audioEstado.transmitiendoVoz) Color(0xFFD92332)
                                        else if (audioEstado.chatAudioActivo) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (audioEstado.transmitiendoVoz) "🎙️ TRANSMITIENDO VOZ..."
                                else if (audioEstado.hablantes.isNotEmpty()) "🔊 ${audioEstado.hablantes.first().nombre} HABLANDO"
                                else if (audioEstado.chatAudioActivo) "🔊 CANAL DE VOZ ACTIVO (PTT)"
                                else "🔇 AUDIO SILENCIADO",
                                color = if (audioEstado.transmitiendoVoz) Color(0xFFD92332) else MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Botón de Videollamada 1-a-1 (si aplica)
                    }

                    actual.errorEnvio?.let { mensajeError ->
                        Text(
                            text = mensajeError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    alertaTofuPeerId?.let { peerId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF331400))
                                .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚠️ ALERTA DE SEGURIDAD (TOFU)",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "La clave de identidad de este contacto cambió respecto a la sesión previa. Si reinstaló la app o cambió de celular, puedes pulsar Confiar.",
                                    color = Color(0xFFFFE0B2),
                                    fontSize = 10.sp
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "Confiar",
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1B5E20))
                                        .clickable { viewModel.confiarEnNuevaIdentidad(peerId) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ignorar",
                                    color = Color(0xFFFF9800),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.descartarAlertaTofu() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (actual.esE2ee) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🔒 CANAL CIFRADO DE EXTREMO A EXTREMO (E2EE)",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    LazyColumn(
                        state = estadoLista,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val mensajesVisibles = actual.mensajes.distinctBy { it.id }
                        itemsIndexed(mensajesVisibles, key = { _, m -> m.id }) { indice, mensaje ->
                            // Como en cualquier red social: foto + nombre solo en el primer mensaje
                            // de una racha de la misma persona; los siguientes quedan alineados debajo.
                            val primeroDeRacha = indice == 0 ||
                                mensajesVisibles[indice - 1].remitenteId != mensaje.remitenteId
                            BurbujaMensaje(
                                mensaje = mensaje,
                                mostrarRemitente = primeroDeRacha,
                                onVerPerfil = onVerPerfil,
                                onVerComunidad = onVerComunidad,
                                onVerEvento = onVerEvento,
                                onEliminarMensaje = { mensajeAEliminar = it }
                            )
                        }
                    }

                    // Barra de entrada con diseño táctico moderno
                    BarraEntradaMensaje(
                        texto = textoNuevo,
                        onTextoCambia = { textoNuevo = it },
                        placeholder = if (actual.esE2ee) "Mensaje cifrado..." else "Escribe un mensaje...",
                        onAdjuntarClick = { mostrarDialogoAdjuntar = true },
                        onEnviarClick = {
                            if (textoNuevo.isNotBlank()) {
                                val txt = textoNuevo
                                textoNuevo = ""
                                viewModel.enviar(txt)
                            }
                        }
                    )
                }
            }

            // Diálogo para adjuntar comunidades o eventos unidos
            if (mostrarDialogoAdjuntar) {
                DialogoAdjuntarContenido(
                    listaComunidades = listaComunidades,
                    listaEventos = listaEventos,
                    onDescartar = { mostrarDialogoAdjuntar = false },
                    onAdjuntarComunidad = { com ->
                        mostrarDialogoAdjuntar = false
                        val tarjeta = "[CARD_COMUNIDAD:${com.id}:${com.nombre}:${com.tipo}:${com.descripcion}]"
                        viewModel.enviar(tarjeta)
                    },
                    onAdjuntarEvento = { ev ->
                        mostrarDialogoAdjuntar = false
                        val tarjeta = "[CARD_EVENTO:${ev.id}:${ev.nombre}:${ev.fecha}:${ev.descripcion}]"
                        viewModel.enviar(tarjeta)
                    }
                )
            }

            // Diálogo: Reportar conversación
            if (mostrarDialogoReportar && actual.interlocutor != null) {
                DialogoReportar(
                    titulo = "Reportar conversación con ${actual.interlocutor.nombre}",
                    nombreObjetivo = actual.interlocutor.nombre,
                    mostrarOpcionBloquear = true,
                    onDismiss = { mostrarDialogoReportar = false },
                    onConfirmar = { motivo, descripcion, bloquearTambien ->
                        mostrarDialogoReportar = false
                        viewModel.enviarReporteConversacion(motivo, descripcion)
                        if (bloquearTambien) viewModel.bloquearInterlocutor()
                    }
                )
            }

            // Diálogo: Confirmar bloqueo
            if (mostrarDialogoBloquear && actual.interlocutor != null) {
                DialogoConfirmarBloqueo(
                    nombre = actual.interlocutor.nombre,
                    onDismiss = { mostrarDialogoBloquear = false },
                    onConfirmar = {
                        mostrarDialogoBloquear = false
                        viewModel.bloquearInterlocutor()
                    }
                )
            }

            // Diálogo: Confirmar eliminación de la conversación entera
            if (mostrarDialogoEliminarConversacion) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogoEliminarConversacion = false },
                    title = { Text("¿Eliminar conversación entera?") },
                    text = {
                        Text(
                            text = "Se eliminarán todos los mensajes de esta conversación en este dispositivo.",
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                mostrarDialogoEliminarConversacion = false
                                viewModel.eliminarConversacion {
                                    onVolverAlMapa?.invoke()
                                }
                            }
                        ) {
                            Text(
                                text = "Eliminar todo",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarDialogoEliminarConversacion = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Diálogo: Confirmar eliminación de conversación desde la lista de Activos
            conversacionAEliminarPorItem?.let { chatItem ->
                AlertDialog(
                    onDismissRequest = { conversacionAEliminarPorItem = null },
                    title = { Text("¿Eliminar conversación con ${chatItem.remitenteNombre}?") },
                    text = {
                        Text(
                            text = "Se eliminarán todos los mensajes de este chat en este dispositivo.",
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val cId = chatItem.chatId
                                conversacionAEliminarPorItem = null
                                viewModel.eliminarConversacionDirecta(cId)
                            }
                        ) {
                            Text(
                                text = "Eliminar",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { conversacionAEliminarPorItem = null }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
    }
}

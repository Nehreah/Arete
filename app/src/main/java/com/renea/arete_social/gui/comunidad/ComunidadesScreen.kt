package com.renea.arete_social.gui.comunidad

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.renea.arete_social.domain.model.Evento
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.renea.arete_social.ContenedorApp
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.repository.ID_SALA_GENERAL
import com.renea.arete_social.feature.mensajeria.ChatViewModel
import com.renea.arete_social.gui.mensajeria.ChatScreen
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.feature.comunidades.ComunidadesUiState
import com.renea.arete_social.feature.comunidades.ComunidadesViewModel

/**
 * Pantalla de Comunidades rediseñada fiel al mockup Comunidad.jpeg:
 * - Cabecera táctica "A R É T E □ / C O M U N I D A D" con acceso a mascota.
 * - Indicador "● N cerca" con punto esmeralda brillante.
 * - Tarjeta "ACCESO A RED COMUNITARIA" y botón prominente "CREAR NUEVA COMUNIDAD".
 * - Sección "01 ──── BASE DE DATOS / COMUNIDADES CREADAS [01] / > NODOS DISPONIBLES".
 * - Tarjetas de nodo con indicador de estado (○ ABIERTO), descripción y botón "[ ACCESO ]".
 * - Pie de página táctico "■ ARÉTE NETWORK // COMUNIDAD 1984".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComunidadesScreen(
    viewModel: ComunidadesViewModel,
    onComunidadSeleccionada: (String) -> Unit,
    onCrearComunidad: () -> Unit,
    modifier: Modifier = Modifier,
    onMascotaClick: () -> Unit = {},
    salas: List<Sala> = emptyList(),
    /** chatId del chat regional destacado (región más cercana). */
    regionDestacadaChatId: String? = null,
    /** chatId de lugares/eventos visibles → origen y vencimiento (etiquetas). */
    visibilidadPorChat: Map<String, com.renea.arete_social.domain.model.VisibilidadMapa> = emptyMap(),
    onSeleccionarChat: (String, String) -> Unit = { _, _ -> },
    repositorioMurales: com.renea.arete_social.data.local.mural.RepositorioMuralesLocal? = null,
    repositorioModeracion: com.renea.arete_social.domain.repository.RepositorioModeracion? = null,
    usuarioId: String = "",
    autorNombre: String = "",
    contenedor: ContenedorApp? = null,
    onIniciarVideollamada: ((contactoId: String, nombre: String) -> Unit)? = null,
    onVerPerfil: ((String) -> Unit)? = null,
    onVerEvento: ((String) -> Unit)? = null,
    chatActivoExternoId: String? = null,
    chatActivoExternoTitulo: String? = null,
    onLimpiarChatExterno: () -> Unit = {}
) {
    val estado by viewModel.estado.collectAsState()
    var refrescando by remember { mutableStateOf(false) }
    if (estado !is ComunidadesUiState.Cargando) refrescando = false

    LaunchedEffect(Unit) {
        viewModel.recargar()
    }

    PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = { refrescando = true; viewModel.recargar() },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val actual = estado) {
            is ComunidadesUiState.Cargando -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CARGANDO COMUNIDADES...",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is ComunidadesUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "ERROR DE CONEXIÓN",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = actual.mensaje,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.recargar() },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "REINTENTAR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            is ComunidadesUiState.Listo -> {
                ComunidadesContenido(
                    comunidades = actual.comunidades,
                    onComunidadSeleccionada = onComunidadSeleccionada,
                    onCrearComunidad = onCrearComunidad,
                    onMascotaClick = onMascotaClick,
                    salas = salas,
                    regionDestacadaChatId = regionDestacadaChatId,
                    visibilidadPorChat = visibilidadPorChat,
                    onSeleccionarChat = onSeleccionarChat,
                    repositorioMurales = repositorioMurales,
                    repositorioModeracion = repositorioModeracion,
                    usuarioId = usuarioId,
                    autorNombre = autorNombre,
                    contenedor = contenedor,
                    onIniciarVideollamada = onIniciarVideollamada,
                    onVerPerfil = onVerPerfil,
                    onVerEvento = onVerEvento,
                    chatActivoExternoId = chatActivoExternoId,
                    chatActivoExternoTitulo = chatActivoExternoTitulo,
                    onLimpiarChatExterno = onLimpiarChatExterno
                )
            }
        }
    }
}

@Composable
private fun ComunidadesContenido(
    comunidades: List<Comunidad>,
    onComunidadSeleccionada: (String) -> Unit,
    onCrearComunidad: () -> Unit,
    onMascotaClick: () -> Unit,
    salas: List<Sala>,
    regionDestacadaChatId: String?,
    visibilidadPorChat: Map<String, com.renea.arete_social.domain.model.VisibilidadMapa>,
    onSeleccionarChat: (String, String) -> Unit,
    repositorioMurales: com.renea.arete_social.data.local.mural.RepositorioMuralesLocal?,
    repositorioModeracion: com.renea.arete_social.domain.repository.RepositorioModeracion? = null,
    usuarioId: String,
    autorNombre: String,
    contenedor: ContenedorApp? = null,
    onIniciarVideollamada: ((contactoId: String, nombre: String) -> Unit)? = null,
    onVerPerfil: ((String) -> Unit)? = null,
    onVerEvento: ((String) -> Unit)? = null,
    chatActivoExternoId: String? = null,
    chatActivoExternoTitulo: String? = null,
    onLimpiarChatExterno: () -> Unit = {}
) {
    var pestanaActiva by rememberSaveable { mutableIntStateOf(0) }
    var chatActivoId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatActivoTitulo by rememberSaveable { mutableStateOf<String?>(null) }
    var muralAbierto by rememberSaveable { mutableStateOf(false) }
    var muralTitulo by rememberSaveable { mutableStateOf<String?>(null) }

    // Sincronizar chat externo si fue solicitado (por ej. desde un evento o mapa)
    LaunchedEffect(chatActivoExternoId) {
        if (chatActivoExternoId != null) {
            chatActivoId = chatActivoExternoId
            chatActivoTitulo = chatActivoExternoTitulo
            pestanaActiva = 2
        }
    }

    // Manejador del botón Atrás del sistema (cierra el chat o mural in-situ)
    androidx.activity.compose.BackHandler(enabled = chatActivoId != null || (pestanaActiva == 3 && muralAbierto)) {
        if (chatActivoId != null) {
            chatActivoId = null
            chatActivoTitulo = null
            onLimpiarChatExterno()
        } else if (pestanaActiva == 3 && muralAbierto) {
            muralAbierto = false
            muralTitulo = null
        }
    }

    // Clasificación de Salas
    val chatGeneral = remember(salas) {
        salas.firstOrNull {
            it.tipo == TipoSala.GENERAL ||
            it.chatId == ID_CHAT_GENERAL ||
            it.id == ID_SALA_GENERAL
        }
    }
    val salasRegionales = remember(salas) {
        salas.filter { it.tipo == TipoSala.REGIONAL }
    }
    val salasComunidades = remember(salas) {
        salas.filter { it.tipo == TipoSala.COMUNIDAD }
    }
    val salasLugaresZona = remember(salas, visibilidadPorChat) {
        salas.filter { it.tipo != TipoSala.EVENTO && it.chatId in visibilidadPorChat }
    }

    // Cargar eventos del usuario para la pestaña 1 (Eventos)
    var misEventos by remember { mutableStateOf<List<Evento>>(emptyList()) }
    var cargandoEventos by remember { mutableStateOf(false) }
    LaunchedEffect(pestanaActiva, usuarioId) {
        if (pestanaActiva == 1 && usuarioId.isNotBlank() && contenedor != null) {
            cargandoEventos = true
            runCatching {
                contenedor.repositorioEventos.listarMisEventos(usuarioId)
            }.onSuccess { misEventos = it }
            cargandoEventos = false
        }
    }

    // Filtrar comunidades a las que pertenece el usuario (para el mural)
    val misComunidades = remember(comunidades, salasComunidades, usuarioId) {
        val idsConSala = salasComunidades.mapNotNull { it.entidadId }.toSet()
        comunidades.filter { com ->
            com.liderId == usuarioId || com.id in idsConSala
        }
    }

    var regionSeleccionadaChatId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(regionDestacadaChatId, salasRegionales) {
        if (regionSeleccionadaChatId == null) {
            regionSeleccionadaChatId = regionDestacadaChatId ?: salasRegionales.firstOrNull()?.chatId
        }
    }
    val salaRegionalActual = remember(salasRegionales, regionSeleccionadaChatId) {
        salasRegionales.firstOrNull { it.chatId == regionSeleccionadaChatId } ?: salasRegionales.firstOrNull()
    }
    var mostrarDialogoRegiones by remember { mutableStateOf(false) }

    // Título y subtítulo dinámico del Hero
    val heroTitulo = when {
        chatActivoId != null -> chatActivoTitulo ?: "Chat"
        pestanaActiva == 3 && muralAbierto -> muralTitulo ?: "Mural"
        else -> "Parchate"
    }
    val heroSubtitulo = when {
        chatActivoId != null -> "Sala de chat en vivo"
        pestanaActiva == 3 && muralAbierto -> "Mural de publicaciones"
        else -> "Encuentra tu gente, crea tu parche"
    }
    val heroOnAtras: (() -> Unit)? = when {
        chatActivoId != null -> {
            {
                chatActivoId = null
                chatActivoTitulo = null
                onLimpiarChatExterno()
            }
        }
        pestanaActiva == 3 && muralAbierto -> {
            {
                muralAbierto = false
                muralTitulo = null
            }
        }
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hero integrado con las 4 pestañas compactas (Comunidades, Eventos, Salas, Murales)
        HeroParcheIntegradoCard(
            pestanaActiva = pestanaActiva,
            onPestanaSeleccionada = { index ->
                pestanaActiva = index
                if (chatActivoId != null) {
                    chatActivoId = null
                    chatActivoTitulo = null
                    onLimpiarChatExterno()
                }
                if (index != 3) {
                    muralAbierto = false
                    muralTitulo = null
                }
            },
            titulo = heroTitulo,
            subtitulo = heroSubtitulo,
            onAtras = heroOnAtras
        )

        // Contenido según la pestaña activa
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (pestanaActiva) {
                0 -> {
                    // PESTAÑA 0: COMUNIDADES (Solo bolitas circulares con avatar)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            CommunityChipRow(
                                comunidades = comunidades,
                                onComunidadSeleccionada = onComunidadSeleccionada,
                                onCrearComunidad = onCrearComunidad,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (comunidades.isEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Todavía no hay comunidades creadas. ¡Toca el botón + para crear la primera!",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // PESTAÑA 1: EVENTOS (Chats de los eventos a los que el usuario asiste/participa)
                    if (chatActivoId != null && contenedor != null) {
                        val chatVm: ChatViewModel = viewModel(
                            key = "chat_parche_$chatActivoId",
                            factory = viewModelFactory {
                                initializer {
                                    ChatViewModel(
                                        contenedor.mensajes,
                                        contenedor.repositorioUsuarios,
                                        contenedor.cifradorE2ee,
                                        chatActivoId!!,
                                        usuarioId,
                                        contenedor.gestorLlamadas,
                                        contenedor.messageRouter,
                                        resolverPerfiles = contenedor.resolverPerfiles
                                    )
                                }
                            }
                        )
                        ChatScreen(
                            viewModel = chatVm,
                            chatId = chatActivoId!!,
                            tituloChat = chatActivoTitulo ?: "Chat",
                            gestorLlamadas = contenedor.gestorLlamadas,
                            onIniciarVideollamada = onIniciarVideollamada?.let { fn ->
                                {
                                    val cid = chatActivoId.takeIf { it != ID_CHAT_GENERAL } ?: "peer"
                                    val nom = (chatActivoTitulo ?: "Chat").removePrefix("Chat: ").removePrefix("Chat ")
                                    fn(cid, nom)
                                }
                            },
                            onVerPerfil = onVerPerfil,
                            onVerComunidad = onComunidadSeleccionada,
                            onVerEvento = onVerEvento,
                            listaComunidades = comunidades,
                            salas = salas,
                            regionDestacadaChatId = regionDestacadaChatId,
                            visibilidadPorChat = visibilidadPorChat,
                            onVolverAlMapa = {
                                chatActivoId = null
                                chatActivoTitulo = null
                                onLimpiarChatExterno()
                            },
                            ocultarBarraSuperior = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (cargandoEventos) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else if (misEventos.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "NO TIENES EVENTOS ACTIVOS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Explora los eventos en el radar o únete a uno para participar en su chat grupal y coordinar con los asistentes.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    text = "CHATS DE TUS EVENTOS",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            items(misEventos, key = { it.id }) { evento ->
                                ItemEventoParche(
                                    evento = evento,
                                    onEntrarChat = {
                                        if (contenedor != null) {
                                            chatActivoId = evento.chatId
                                            chatActivoTitulo = "Chat: ${evento.nombre}"
                                        } else {
                                            onSeleccionarChat(evento.chatId, "Chat: ${evento.nombre}")
                                        }
                                    },
                                    onVerDetalle = {
                                        onVerEvento?.invoke(evento.id)
                                    }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // PESTAÑA 2: SALAS (2 botones contiguos arriba + bolitas de comunidades abajo)
                    if (chatActivoId != null && contenedor != null) {
                        // CHAT IN-SITU
                        val chatVm: ChatViewModel = viewModel(
                            key = "chat_parche_$chatActivoId",
                            factory = viewModelFactory {
                                initializer {
                                    ChatViewModel(
                                        contenedor.mensajes,
                                        contenedor.repositorioUsuarios,
                                        contenedor.cifradorE2ee,
                                        chatActivoId!!,
                                        usuarioId,
                                        contenedor.gestorLlamadas,
                                        contenedor.messageRouter,
                                        resolverPerfiles = contenedor.resolverPerfiles
                                    )
                                }
                            }
                        )
                        ChatScreen(
                            viewModel = chatVm,
                            chatId = chatActivoId!!,
                            tituloChat = chatActivoTitulo ?: "Chat",
                            gestorLlamadas = contenedor.gestorLlamadas,
                            onIniciarVideollamada = onIniciarVideollamada?.let { fn ->
                                {
                                    val cid = chatActivoId.takeIf { it != ID_CHAT_GENERAL } ?: "peer"
                                    val nom = (chatActivoTitulo ?: "Chat").removePrefix("Chat: ").removePrefix("Chat ")
                                    fn(cid, nom)
                                }
                            },
                            onVerPerfil = onVerPerfil,
                            onVerComunidad = onComunidadSeleccionada,
                            onVerEvento = onVerEvento,
                            listaComunidades = comunidades,
                            salas = salas,
                            regionDestacadaChatId = regionDestacadaChatId,
                            visibilidadPorChat = visibilidadPorChat,
                            onVolverAlMapa = {
                                chatActivoId = null
                                chatActivoTitulo = null
                                onLimpiarChatExterno()
                            },
                            ocultarBarraSuperior = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. FILA DE 2 BOTONES RECTANGULARES CONTIGUOS (Aréte y Regional)
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Botón 1: Chat Aréte
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                val cid = chatGeneral?.chatId ?: ID_CHAT_GENERAL
                                                val nom = chatGeneral?.nombre ?: "Chat Aréte"
                                                if (contenedor != null) {
                                                    chatActivoId = cid
                                                    chatActivoTitulo = nom
                                                } else {
                                                    onSeleccionarChat(cid, nom)
                                                }
                                            },
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("📻", fontSize = 20.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Aréte",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "General",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Botón 2: Chat Regional
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                salaRegionalActual?.let { sala ->
                                                    if (contenedor != null) {
                                                        chatActivoId = sala.chatId
                                                        chatActivoTitulo = sala.nombre
                                                    } else {
                                                        onSeleccionarChat(sala.chatId, sala.nombre)
                                                    }
                                                }
                                            },
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🌎", fontSize = 20.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = salaRegionalActual?.nombre ?: "Regional",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable { mostrarDialogoRegiones = true }
                                                ) {
                                                    Text(
                                                        text = "Cambiar",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(" ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. CHATS DE TUS COMUNIDADES (Bolitas idénticas a las de comunidades)
                            item {
                                Text(
                                    text = "CHATS DE TUS COMUNIDADES",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            if (salasComunidades.isEmpty()) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Aún no perteneces a comunidades con chat. ¡Únete a una para participar!",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                            } else {
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        items(salasComunidades, key = { it.id }) { sala ->
                                            val com = comunidades.firstOrNull { it.id == sala.entidadId }
                                            SalaComunidadChipItem(
                                                sala = sala,
                                                comunidad = com,
                                                onClick = {
                                                    if (contenedor != null) {
                                                        chatActivoId = sala.chatId
                                                        chatActivoTitulo = sala.nombre
                                                    } else {
                                                        onSeleccionarChat(sala.chatId, sala.nombre)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. LUGARES EN TU RADAR (Los eventos se gestionan exclusivamente en la pestaña Eventos)
                            if (salasLugaresZona.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "LUGARES EN TU RADAR",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                items(salasLugaresZona, key = { it.id }) { sala ->
                                    val etiqueta = visibilidadPorChat[sala.chatId]?.let {
                                        com.renea.arete_social.domain.util.etiquetaVisibilidad(it, System.currentTimeMillis())
                                    }
                                    ItemSalaParche(
                                        icono = "📍",
                                        nombre = sala.nombre,
                                        descripcion = sala.descripcion.ifBlank { "Lugar visible en el mapa" },
                                        etiqueta = etiqueta,
                                        onClick = {
                                            if (contenedor != null) {
                                                chatActivoId = sala.chatId
                                                chatActivoTitulo = sala.nombre
                                            } else {
                                                onSeleccionarChat(sala.chatId, sala.nombre)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // PESTAÑA 3: MURALES (Filtrado estricto a las comunidades a las que pertenece el usuario)
                    if (repositorioMurales != null) {
                        MuralesScreen(
                            repositorio = repositorioMurales,
                            usuarioId = usuarioId,
                            autorNombre = autorNombre,
                            comunidades = misComunidades,
                            muralAbiertoExterno = muralAbierto,
                            ocultarEncabezado = true,
                            onCambioMural = { abierto, id, titulo ->
                                muralAbierto = abierto
                                muralTitulo = titulo
                            },
                            repositorioModeracion = repositorioModeracion,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Murales no disponibles en este momento.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Diálogo selector de regiones
    if (mostrarDialogoRegiones && salasRegionales.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoRegiones = false },
            title = {
                Text(
                    text = "Elegir Chat Regional",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(salasRegionales, key = { it.id }) { sala ->
                        val esActual = sala.chatId == salaRegionalActual?.chatId
                        val esCercana = sala.chatId == regionDestacadaChatId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (esActual) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    regionSeleccionadaChatId = sala.chatId
                                    mostrarDialogoRegiones = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🌎", fontSize = 18.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = sala.nombre,
                                        fontWeight = if (esActual) FontWeight.Bold else FontWeight.Medium,
                                        color = if (esActual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                    if (esCercana) {
                                        Text(
                                            text = "(Más cercana)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (sala.descripcion.isNotBlank()) {
                                    Text(
                                        text = sala.descripcion,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (esActual) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarDialogoRegiones = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun ItemSalaParche(
    icono: String,
    nombre: String,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icono, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nombre,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = descripcion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (etiqueta != null) {
                Text(
                    text = etiqueta,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComunidadesHeader(
    onMascotaClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val esNuevo = com.renea.arete_social.gui.theme.DisenoConfig.NUEVO_DISENO
        Column {
            if (esNuevo) {
                Text(
                    text = "Arete",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Comunidades",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "a r e t e",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "C O M U N I D A D",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pill Mascota (Mascota.jpeg / Comunidad.jpeg: [🐱 NANI >])
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    .clickable(onClick = onMascotaClick)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🐱",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NANI",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Botón compartir / enlace
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⌲",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ComunidadCardItem(
    comunidad: Comunidad,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            // Fila superior: Nombre + Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .shadow(3.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = comunidad.nombre.uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "○",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ABIERTO",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Línea divisoria sutil
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(MaterialTheme.colorScheme.surface)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Descripción
            Text(
                text = comunidad.descripcion.ifBlank { "Comunidad activa en arete." },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Fila inferior: TIPO + [ ACCESO ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TIPO ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = comunidad.tipo.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "[ ACCESO ]",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SalaComunidadChipItem(
    sala: Sala,
    comunidad: Comunidad?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val foto = comunidad?.fotoUrl
            if (!foto.isNullOrBlank()) {
                AsyncImage(
                    model = foto,
                    contentDescription = sala.nombre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                val inicial = sala.nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "👥"
                Text(
                    text = inicial,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = sala.nombre,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ItemEventoParche(
    evento: Evento,
    onEntrarChat: () -> Unit,
    onVerDetalle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fechaFormateada = remember(evento.fecha) {
        runCatching {
            SimpleDateFormat("EEE d MMM • HH:mm", Locale.getDefault()).format(Date(evento.fecha))
        }.getOrNull() ?: ""
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (fechaFormateada.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "📅 $fechaFormateada",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable(onClick = onVerDetalle)
                ) {
                    Text(
                        text = "VER EVENTO",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column {
                Text(
                    text = evento.nombre,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (evento.descripcion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = evento.descripcion,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = onEntrarChat,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💬 ENTRAR AL CHAT DEL EVENTO",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

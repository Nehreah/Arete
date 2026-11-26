package com.renea.arete_social.gui.mapa

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.renea.arete_social.ContenedorApp
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.model.ContactoLlamada
import com.renea.arete_social.feature.auth.PerfilViewModel
import com.renea.arete_social.feature.comunidades.ComunidadesViewModel
import com.renea.arete_social.feature.mapa.MapaUiState
import com.renea.arete_social.feature.encuentros.GestorEncuentros
import com.renea.arete_social.feature.mapa.MapaViewModel
import com.renea.arete_social.feature.mensajeria.ChatViewModel
import com.renea.arete_social.feature.mensajeria.SalasUiState
import com.renea.arete_social.feature.mensajeria.SalasViewModel
import com.renea.arete_social.feature.cartas.CartasViewModel
import com.renea.arete_social.feature.libreria.LibreriaViewModel
import com.renea.arete_social.gui.auth.perfil.PerfilScreen
import com.renea.arete_social.gui.comunidad.ComunidadesScreen
import com.renea.arete_social.gui.mensajeria.ChatScreen
import com.renea.arete_social.gui.navigation.AreteBottomBar
import com.renea.arete_social.gui.navigation.HistorialNavegable
import com.renea.arete_social.gui.navigation.HistorialPantallas
import com.renea.arete_social.gui.navigation.TabNavegacion
import com.renea.arete_social.gui.tamagotchi.PetScreen
import com.renea.arete_social.gui.theme.LocalRadarColors
import kotlinx.coroutines.launch

/**
 * Contenedor principal de nivel superior de la aplicación.
 * Gestiona el [HorizontalPager] de 5 pestañas principales, el [HistorialNavegable] LIFO,
 * el [BackHandler] corregido y el host de pantallas secundarias en superposición.
 */
@Composable
fun MapaScreen(
    viewModel: MapaViewModel,
    comunidadesViewModel: ComunidadesViewModel,
    perfilViewModel: PerfilViewModel,
    cartasViewModel: CartasViewModel,
    libreriaViewModel: LibreriaViewModel,
    salasViewModel: SalasViewModel,
    contenedor: ContenedorApp,
    usuarioIdActual: String,
    modifier: Modifier = Modifier,
    onCerrarSesion: () -> Unit = {},
    onEliminarCuenta: () -> Unit = {}
) {
    val estado by viewModel.estado.collectAsState()

    // Aviso breve cuando alguien cercano comparte lugares o eventos por Bluetooth.
    val contextoAvisos = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.avisos.collect { aviso ->
            android.widget.Toast.makeText(contextoAvisos, aviso, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── Encuentros con ubicación compartida ─────────────────────────────────
    // Se gestionan aquí (y no en la pestaña del mapa) para que las invitaciones lleguen
    // aunque la persona esté en otra pestaña. Las cuentas de invitado no tienen servidor.
    val ubicacionPropiaEncuentro = (estado as? MapaUiState.Listo)?.ubicacionPropia
    DisposableEffect(usuarioIdActual) {
        GestorEncuentros.iniciar(
            repositorio = contenedor.modulo.encuentros,
            calculador = contenedor.calculadorRuta,
            miId = usuarioIdActual,
            nombreDe = { id ->
                contenedor.repositorioUsuarios.obtenerPorId(id)?.nombre
                    ?: perfilViewModel.amigos.value.firstOrNull { it.id == id }?.nombre
                    ?: id.take(8)
            },

            router = contenedor.messageRouter,
            miNombreProveedor = {
                (perfilViewModel.estado.value as? com.renea.arete_social.feature.auth.PerfilUiState.Listo)?.usuario?.nombre?.takeIf { it.isNotBlank() }
                    ?: com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre()
                    ?: "Usuario"
            }
        )
        onDispose { GestorEncuentros.detener() }
    }
    LaunchedEffect(ubicacionPropiaEncuentro) {
        ubicacionPropiaEncuentro?.let { GestorEncuentros.actualizarMiUbicacion(it) }
    }
    val avisoEncuentro by GestorEncuentros.aviso.collectAsState()
    LaunchedEffect(avisoEncuentro) {
        avisoEncuentro?.let {
            Toast.makeText(contexto, it, Toast.LENGTH_LONG).show()
            GestorEncuentros.consumirAviso()
        }
    }
    val invitacionesEncuentro by GestorEncuentros.solicitudesRecibidas.collectAsState()
    invitacionesEncuentro.firstOrNull()?.let { invitacion ->
        DialogoInvitacionEncuentro(
            solicitud = invitacion,
            onResponder = { acepta -> GestorEncuentros.responderSolicitud(invitacion, acepta) }
        )
    }

    val historial: HistorialNavegable = remember { HistorialPantallas(TabNavegacion.MAPA) }
    val pagerState = rememberPagerState(initialPage = TabNavegacion.MAPA.ordinal) { 5 }
    val pilaSecundaria = remember { mutableStateListOf<PantallaSecundaria>() }
    // Mientras no es null, el mapa está oscurecido con una mira para elegir el lugar del evento.
    var seleccionEvento by remember { mutableStateOf<PantallaSecundaria.SeleccionarLugarEvento?>(null) }

    fun cancelarSeleccionEvento() {
        val sel = seleccionEvento ?: return
        seleccionEvento = null
        // Si venía de una comunidad, se vuelve a ella.
        sel.comunidadId?.let { pilaSecundaria.add(PantallaSecundaria.VerComunidad(it)) }
    }

    var chatActivoId by remember { mutableStateOf(ID_CHAT_GENERAL) }
    var chatActivoTitulo by remember { mutableStateOf("Chat Aréte") }
    var chatEnFoco by remember { mutableStateOf(false) }
    var chatEnParche by remember { mutableStateOf(false) }
    var mostrarDialogoCrearSala by remember { mutableStateOf(false) }
    var focoCoordenadasMapa by remember { mutableStateOf<com.renea.arete_social.gui.mapa.osm.PuntoMapa?>(null) }

    val permisosBle = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyArray()
        }
    }

    val permisosRequeridos = remember {
        val lista = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista += Manifest.permission.POST_NOTIFICATIONS
            // NEARBY_WIFI_DEVICES se requiere en runtime en Android 13+ para el
            // descubrimiento NSD/mDNS en la red WiFi local (WifiLanMeshBridge).
            lista += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        lista += permisosBle
        lista.toTypedArray()
    }

    fun tienePermisosBle(): Boolean =
        permisosBle.isEmpty() || permisosBle.all {
            ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
        }

    var permisoConcedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                contexto,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permisoBleConcedido by remember { mutableStateOf(tienePermisosBle()) }

    var bluetoothActivo by remember {
        mutableStateOf(
            (contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.isEnabled == true
        )
    }

    DisposableEffect(Unit) {
        val bm = contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        fun recalcularBt() {
            bluetoothActivo = bm?.adapter?.isEnabled == true
            permisoBleConcedido = tienePermisosBle()
        }
        recalcularBt()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { recalcularBt() }
        }
        ContextCompat.registerReceiver(
            contexto, receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { contexto.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(bluetoothActivo) {
        if (bluetoothActivo && tienePermisosBle()) {
            contenedor.iniciarMesh()
        }
    }

    val lanzadorActivarBluetooth = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothActivo = (contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.isEnabled == true
    }

    val lanzadorPermisos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        permisoConcedido = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        permisoBleConcedido = tienePermisosBle()
        if (permisoBleConcedido && !bluetoothActivo) {
            runCatching {
                lanzadorActivarBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
        // Relanzar mesh si ya tenemos los permisos mínimos: esto garantiza que
        // NEARBY_WIFI_DEVICES y el FGS se inicializan con los permisos ya concedidos
        // (crítico en Android 13+ donde NEARBY_WIFI_DEVICES se acaba de pedir).
        if (permisoConcedido || permisoBleConcedido) {
            scope.launch { contenedor.iniciarMesh() }
        }
    }

    val debeIniciar = permisoConcedido || (permisoBleConcedido && bluetoothActivo)
    LaunchedEffect(Unit) {
        if (!permisoConcedido || !permisoBleConcedido) {
            lanzadorPermisos.launch(permisosRequeridos)
        }
    }
    LaunchedEffect(debeIniciar) {
        if (debeIniciar) {
            viewModel.iniciar()
            contenedor.iniciarMesh()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.detener()
        }
    }

    // Sincronizar el Pager con el historial navegable
    LaunchedEffect(pagerState.currentPage) {
        val tab = TabNavegacion.values()[pagerState.currentPage]
        historial.visitar(tab)
        if (tab == TabNavegacion.MAPA) {
            viewModel.refrescarObras()
        }
    }

    val tabActual = TabNavegacion.values()[pagerState.currentPage]
    val backEnabled = seleccionEvento != null || pilaSecundaria.isNotEmpty() || chatEnParche || (tabActual == TabNavegacion.CHAT && chatEnFoco) || historial.actual != TabNavegacion.MAPA

    // BackHandler jerárquico profesional por pestaña
    BackHandler(enabled = backEnabled) {
        when {
            seleccionEvento != null -> cancelarSeleccionEvento()
            chatEnParche -> { chatEnParche = false; chatEnFoco = false; chatActivoId = ID_CHAT_GENERAL; chatActivoTitulo = "Chat Aréte" }
            pilaSecundaria.isNotEmpty() -> {
                pilaSecundaria.removeAt(pilaSecundaria.lastIndex)
            }
            tabActual == TabNavegacion.CHAT && chatEnFoco -> {
                chatEnFoco = false
                chatEnParche = false
                chatActivoId = ID_CHAT_GENERAL
                chatActivoTitulo = "Chat Aréte"
            }
            else -> {
                if (historial.volver()) {
                    scope.launch { pagerState.animateScrollToPage(historial.actual.ordinal) }
                }
            }
        }
    }

    fun abrirPantalla(pantalla: PantallaSecundaria) {
        when (pantalla) {
            is PantallaSecundaria.AbrirChat -> {
                pilaSecundaria.clear()
                chatActivoId = pantalla.chatId
                chatActivoTitulo = pantalla.titulo
                chatEnFoco = true
                perfilViewModel.cargarMensajesRecibidos()

                val listaSalasActual = (salasViewModel.estado.value as? SalasUiState.Listo)?.salas ?: emptyList()
                val esSala = pantalla.esSala ||
                    pantalla.chatId == ID_CHAT_GENERAL ||
                    listaSalasActual.any { it.chatId == pantalla.chatId }

                if (esSala) {
                    chatEnParche = true
                    historial.visitar(TabNavegacion.COMUNIDAD)
                    scope.launch { pagerState.animateScrollToPage(TabNavegacion.COMUNIDAD.ordinal) }
                } else {
                    chatEnParche = false
                    historial.visitar(TabNavegacion.CHAT)
                    scope.launch { pagerState.animateScrollToPage(TabNavegacion.CHAT.ordinal) }
                }
            }

            is PantallaSecundaria.AbrirChatConUsuario -> {
                if (pantalla.usuarioId == usuarioIdActual) return
                scope.launch {
                    val chatId = runCatching {
                        contenedor.mensajes.obtenerOCrearChatDirecto(usuarioIdActual, pantalla.usuarioId)
                    }.getOrNull() ?: return@launch
                    pilaSecundaria.clear()
                    chatActivoId = chatId
                    chatActivoTitulo = pantalla.titulo
                    chatEnFoco = true
                    chatEnParche = false
                    perfilViewModel.cargarMensajesRecibidos()
                    historial.visitar(TabNavegacion.CHAT)
                    if (pagerState.currentPage != TabNavegacion.CHAT.ordinal) {
                        pagerState.animateScrollToPage(TabNavegacion.CHAT.ordinal)
                    }
                }
            }

            is PantallaSecundaria.SeleccionarLugarEvento -> {
                // Desde el mapa o desde Comunidades: siempre se elige el lugar sobre el mapa.
                pilaSecundaria.clear()
                chatEnFoco = false
                seleccionEvento = pantalla
                historial.visitar(TabNavegacion.MAPA)
                scope.launch { pagerState.animateScrollToPage(TabNavegacion.MAPA.ordinal) }
            }

            else -> pilaSecundaria.add(pantalla)
        }
    }

    val chatVisibleActual = remember(pilaSecundaria.lastOrNull(), chatEnFoco, chatActivoId, pagerState.currentPage) {
        val sec = pilaSecundaria.lastOrNull()
        if (sec is PantallaSecundaria.AbrirChat) sec.chatId
        else if (sec is PantallaSecundaria.AbrirChatConUsuario) sec.usuarioId
        else if ((pagerState.currentPage == TabNavegacion.CHAT.ordinal && chatEnFoco) || chatEnParche) chatActivoId
        else null
    }
    LaunchedEffect(chatVisibleActual) {
        contenedor.gestorLlamadas.chatVisibleId = chatVisibleActual
    }

    LaunchedEffect(Unit) {
        contenedor.solicitudNavegacionSecundaria.collect { pantalla ->
            abrirPantalla(pantalla)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!permisoConcedido && !debeIniciar) {
            PantallaPermiso(onConcederPermiso = { lanzadorPermisos.launch(permisosRequeridos) })
            return@Box
        }

        val locationManager = remember { contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
        // §3.1 — GPS reactivo: se actualiza en tiempo real cuando el usuario cambia la Ubicación
        // en los Ajustes Rápidos, sin necesidad de reiniciar la pantalla.
        var gpsActivo by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            fun recalcular() {
                gpsActivo = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                        locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }
            recalcular()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) { recalcular() }
            }
            ContextCompat.registerReceiver(
                contexto, receiver,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose { runCatching { contexto.unregisterReceiver(receiver) } }
        }

        if (!permisoBleConcedido || !bluetoothActivo) {
            AvisoBleInactivo(
                faltaPermiso = !permisoBleConcedido,
                onConcederPermiso = { lanzadorPermisos.launch(permisosBle) },
                onActivarBluetooth = {
                    runCatching {
                        lanzadorActivarBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            )
        } else if (!gpsActivo) {
            AvisoGpsInactivo(
                onActivarGps = {
                    runCatching {
                        contexto.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            )
        }

        when (val actual = estado) {
            is MapaUiState.Cargando -> PantallaCargando()
            is MapaUiState.Error -> PantallaError(actual.mensaje)
            is MapaUiState.Listo -> {
                val mensajesRecibidos by perfilViewModel.mensajesRecibidos.collectAsState()
                val listaAmigos by perfilViewModel.amigos.collectAsState()
                val estadoSalas by salasViewModel.estado.collectAsState()
                val listaSalas = (estadoSalas as? SalasUiState.Listo)?.salas ?: emptyList()
                val regionDestacadaChatId = (estadoSalas as? SalasUiState.Listo)?.regionDestacadaChatId
                val visibilidadPorChat = (estadoSalas as? SalasUiState.Listo)?.visibilidadPorChat ?: emptyMap()

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            modifier = Modifier.fillMaxSize()
                        ) { pagina ->
                            when (TabNavegacion.values()[pagina]) {
                                TabNavegacion.PERFIL -> PerfilScreen(
                                    viewModel = perfilViewModel,
                                    cartasViewModel = cartasViewModel,
                                    libreriaViewModel = libreriaViewModel,
                                    onAbrirChat = { id, t -> abrirPantalla(PantallaSecundaria.AbrirChat(id, t)) },
                                    onAbrirChatConUsuario = { usuarioId, nombre ->
                                        abrirPantalla(PantallaSecundaria.AbrirChatConUsuario(usuarioId, "Chat: $nombre"))
                                    },
                                    onIniciarVideollamada = { contactoId, nombre, foto ->
                                        contenedor.gestorLlamadas.iniciarLlamadaVideo(ContactoLlamada(contactoId, nombre, foto))
                                        abrirPantalla(PantallaSecundaria.LlamadaVideo(contactoId, nombre, foto))
                                    },
                                    onVerPerfilAmigo = { id -> abrirPantalla(PantallaSecundaria.VerPerfil(id)) },
                                    onGestionarIdentidad = { abrirPantalla(PantallaSecundaria.GestionarIdentidad) },
                                    onCerrarSesion = onCerrarSesion,
                                    onEliminarCuenta = onEliminarCuenta,
                                    modifier = Modifier.fillMaxSize()
                                )

                                TabNavegacion.MASCOTA -> PetScreen(
                                    repository = contenedor.petRepository,
                                    ownerId = usuarioIdActual,
                                    ownerNombre = (perfilViewModel.estado.collectAsState().value as? com.renea.arete_social.feature.auth.PerfilUiState.Listo)?.usuario?.nombre ?: "Mi Mascota",
                                    viewerId = usuarioIdActual,
                                    detectorProximidad = contenedor.detectorProximidad,
                                    repositorioEventos = contenedor.repositorioEventos,
                                    repositorioUsuarios = contenedor.repositorioUsuarios,
                                    repositorioLibreria = contenedor.libreria,
                                    onEstadoCambiado = { texto, vence ->
                                        contenedor.gestorLlamadas.transmitirEstadoMascota(texto, vence)
                                        contenedor.detectorProximidad.actualizarEstadoDispositivo(usuarioIdActual, texto, vence)
                                    },
                                    onAtras = { scope.launch { pagerState.animateScrollToPage(TabNavegacion.MAPA.ordinal) } },
                                    modifier = Modifier.fillMaxSize()
                                )

                                TabNavegacion.MAPA -> ContenidoRadar(
                                    estado = actual,
                                    comunidadesViewModel = comunidadesViewModel,
                                    perfilViewModel = perfilViewModel,
                                    salasViewModel = salasViewModel,
                                    contenedor = contenedor,
                                    usuarioIdActual = usuarioIdActual,
                                    onIrMiPerfil = { scope.launch { pagerState.animateScrollToPage(TabNavegacion.PERFIL.ordinal) } },
                                    onAbrirPantallaSecundaria = { p -> abrirPantalla(p) },
                                    seleccionEvento = seleccionEvento,
                                    onConfirmarLugarEvento = { ubicacion ->
                                        val sel = seleccionEvento
                                        seleccionEvento = null
                                        pilaSecundaria.add(
                                            PantallaSecundaria.CrearEvento(
                                                comunidadId = sel?.comunidadId,
                                                ubicacionInicial = ubicacion
                                            )
                                        )
                                    },
                                    onCancelarSeleccionEvento = { cancelarSeleccionEvento() },
                                    focoCoordenadas = focoCoordenadasMapa,
                                    onConsumirFocoCoordenadas = { focoCoordenadasMapa = null },
                                    modifier = Modifier.fillMaxSize()
                                )

                                TabNavegacion.COMUNIDAD -> {
                                    ComunidadesScreen(
                                        viewModel = comunidadesViewModel,
                                        repositorioMurales = contenedor.repositorioMurales,
                                        repositorioModeracion = contenedor.modulo.moderacion,
                                        usuarioId = usuarioIdActual,
                                        autorNombre = (perfilViewModel.estado.collectAsState().value as? com.renea.arete_social.feature.auth.PerfilUiState.Listo)?.usuario?.nombre ?: "Usuario",
                                        onComunidadSeleccionada = { id -> abrirPantalla(PantallaSecundaria.VerComunidad(id)) },
                                        onCrearComunidad = { abrirPantalla(PantallaSecundaria.CrearComunidadNueva) },
                                        onMascotaClick = { scope.launch { pagerState.animateScrollToPage(TabNavegacion.MASCOTA.ordinal) } },
                                        salas = listaSalas,
                                        regionDestacadaChatId = regionDestacadaChatId,
                                        visibilidadPorChat = visibilidadPorChat,
                                        onSeleccionarChat = { id, titulo ->
                                            chatActivoId = id
                                            chatActivoTitulo = titulo
                                        },
                                        contenedor = contenedor,
                                        onIniciarVideollamada = { contactoId, nombre ->
                                            contenedor.gestorLlamadas.iniciarLlamadaVideo(ContactoLlamada(contactoId, nombre, null))
                                            abrirPantalla(PantallaSecundaria.LlamadaVideo(contactoId, nombre, null))
                                        },
                                        onVerPerfil = { id -> abrirPantalla(PantallaSecundaria.VerPerfil(id)) },
                                        onVerEvento = { id -> abrirPantalla(PantallaSecundaria.VerEvento(id)) },
                                        chatActivoExternoId = if (chatEnParche) chatActivoId else null,
                                        chatActivoExternoTitulo = if (chatEnParche) chatActivoTitulo else null,
                                        onLimpiarChatExterno = {
                                            chatEnParche = false
                                            chatActivoId = ID_CHAT_GENERAL
                                            chatActivoTitulo = "Chat Aréte"
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                TabNavegacion.CHAT -> {
                                    val chatVm: ChatViewModel = viewModel(
                                        key = "chat_$chatActivoId",
                                        factory = viewModelFactory {
                                            initializer {
                                                ChatViewModel(
                                                    contenedor.mensajes,
                                                    contenedor.repositorioUsuarios,
                                                    contenedor.cifradorE2ee,
                                                    chatActivoId,
                                                    usuarioIdActual,
                                                    contenedor.gestorLlamadas,
                                                    contenedor.messageRouter,
                                                    resolverPerfiles = contenedor.resolverPerfiles
                                                )
                                            }
                                        }
                                    )
                                    val estadoComunidades by comunidadesViewModel.estado.collectAsState()
                                    val listaComunidades = (estadoComunidades as? com.renea.arete_social.feature.comunidades.ComunidadesUiState.Listo)?.comunidades ?: emptyList()

                                    ChatScreen(
                                        viewModel = chatVm,
                                        chatId = chatActivoId,
                                        tituloChat = chatActivoTitulo,
                                        gestorLlamadas = contenedor.gestorLlamadas,
                                        onIniciarVideollamada = {
                                            val contactoId = chatActivoId.takeIf { it != ID_CHAT_GENERAL } ?: "peer"
                                            val nombre = chatActivoTitulo.removePrefix("Chat: ").removePrefix("Chat ")
                                            contenedor.gestorLlamadas.iniciarLlamadaVideo(
                                                ContactoLlamada(contactoId, nombre, null)
                                            )
                                            abrirPantalla(PantallaSecundaria.LlamadaVideo(contactoId, nombre, null))
                                        },
                                        onVerPerfil = { id -> abrirPantalla(PantallaSecundaria.VerPerfil(id)) },
                                        onVerComunidad = { id -> abrirPantalla(PantallaSecundaria.VerComunidad(id)) },
                                        onVerEvento = { id -> abrirPantalla(PantallaSecundaria.VerEvento(id)) },
                                        listaComunidades = listaComunidades,
                                        listaEventos = actual.eventosCercanos,
                                        mensajesRecibidos = mensajesRecibidos,
                                        personasCercanas = actual.personasCercanas,
                                        listaAmigos = listaAmigos,
                                        salas = listaSalas,
                                        regionDestacadaChatId = regionDestacadaChatId,
                                        visibilidadPorChat = visibilidadPorChat,
                                        onSeleccionarChat = { id, titulo ->
                                            pilaSecundaria.clear()
                                            chatActivoId = id
                                            chatActivoTitulo = titulo
                                            chatEnFoco = true
                                            chatEnParche = false
                                            perfilViewModel.cargarMensajesRecibidos()
                                        },
                                        onSeleccionarUsuario = { usuarioId, titulo ->
                                            abrirPantalla(PantallaSecundaria.AbrirChatConUsuario(usuarioId, "Chat: $titulo"))
                                        },
                                        onVolverAlMapa = {
                                            if (chatEnFoco) {
                                                chatEnFoco = false
                                                chatEnParche = false
                                                chatActivoId = ID_CHAT_GENERAL
                                                chatActivoTitulo = "Chat Aréte"
                                            } else {
                                                if (historial.volver()) {
                                                    scope.launch { pagerState.animateScrollToPage(historial.actual.ordinal) }
                                                } else {
                                                    scope.launch { pagerState.animateScrollToPage(TabNavegacion.MAPA.ordinal) }
                                                }
                                            }
                                        },
                                        onCrearSala = { mostrarDialogoCrearSala = true },
                                        esVistaHub = !chatEnFoco,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }



                        // Host de pantallas secundarias si hay alguna abierta
                        if (pilaSecundaria.isNotEmpty()) {
                            PantallaSecundariaHost(
                                pantalla = pilaSecundaria.last(),
                                contenedor = contenedor,
                                usuarioIdActual = usuarioIdActual,
                                onAtras = {
                                    if (pilaSecundaria.isNotEmpty()) pilaSecundaria.removeAt(pilaSecundaria.lastIndex)
                                },
                                onIrAlMapa = {
                                    pilaSecundaria.clear()
                                    chatEnParche = false
                                    chatEnFoco = false
                                    historial.visitar(TabNavegacion.MAPA)
                                    scope.launch { pagerState.animateScrollToPage(TabNavegacion.MAPA.ordinal) }
                                },
                                onAbrir = { p -> abrirPantalla(p) },
                                onReemplazar = { p ->
                                    if (pilaSecundaria.isNotEmpty()) pilaSecundaria[pilaSecundaria.lastIndex] = p
                                    else pilaSecundaria.add(p)
                                },
                                personasCercanas = actual.personasCercanas,
                                listaAmigos = listaAmigos,
                                salas = listaSalas,
                                regionDestacadaChatId = regionDestacadaChatId,
                                visibilidadPorChat = visibilidadPorChat,
                                esInvitado = perfilViewModel.sesionEsInvitado,
                                onCentrarEnMapa = { lat, lng ->
                                    pilaSecundaria.clear()
                                    chatEnParche = false
                                    chatEnFoco = false
                                    focoCoordenadasMapa = com.renea.arete_social.gui.mapa.osm.PuntoMapa(lat, lng)
                                    historial.visitar(TabNavegacion.MAPA)
                                    scope.launch { pagerState.animateScrollToPage(TabNavegacion.MAPA.ordinal) }
                                }
                            )
                        }
                    }

                    AreteBottomBar(
                        tabActual = TabNavegacion.values()[pagerState.currentPage],
                        onTabSeleccionado = { tab ->
                            pilaSecundaria.clear()
                            chatEnParche = false
                            seleccionEvento = null
                            if (tab != TabNavegacion.CHAT) {
                                chatEnFoco = false
                            }
                            historial.visitar(tab)
                            scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PantallaPermiso(onConcederPermiso: () -> Unit) {
    val radarColors = LocalRadarColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RadarPermissionIcon()
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "arete",
            color = radarColors.verdeBrillante,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
        Text(
            text = "RADAR",
            color = radarColors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Necesitamos tu ubicación para detectar personas, lugares y eventos cercanos.",
            color = radarColors.texto,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onConcederPermiso,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = "ACTIVAR RADAR", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AvisoBleInactivo(
    faltaPermiso: Boolean,
    onConcederPermiso: () -> Unit,
    onActivarBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6101410))
            .border(1.dp, radarColors.verde.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = if (faltaPermiso) "📡 Falta permiso de Bluetooth" else "📡 Bluetooth apagado",
            color = radarColors.verdeBrillante,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (faltaPermiso)
                "Sin este permiso no podés ver ni ser visto por nadie cerca cuando no hay wifi."
            else
                "Prendé el Bluetooth para detectar y compartir tu perfil con gente cercana sin wifi.",
            color = radarColors.texto,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = if (faltaPermiso) onConcederPermiso else onActivarBluetooth,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (faltaPermiso) "CONCEDER PERMISO" else "ACTIVAR BLUETOOTH",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AvisoGpsInactivo(
    onActivarGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6101410))
            .border(1.dp, radarColors.verde.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "📍 Ubicación (GPS) desactivada",
            color = radarColors.verdeBrillante,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "El descubrimiento de personas cercanas por Bluetooth funciona sin GPS. Activar la Ubicación mejora la precisión de la distancia mostrada y permite encontrar personas también por coordenadas GPS.",
            color = radarColors.texto,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onActivarGps,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "ACTIVAR UBICACIÓN",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RadarPermissionIcon() {
    val radarColors = LocalRadarColors.current
    Box(
        modifier = Modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = radarColors.verde.copy(alpha = 0.12f),
                radius = size.minDimension * 0.43f,
                center = centro,
                style = Stroke(2f)
            )
            drawCircle(
                color = radarColors.verde.copy(alpha = 0.20f),
                radius = size.minDimension * 0.29f,
                center = centro,
                style = Stroke(2f)
            )
            drawCircle(
                color = radarColors.verdeBrillante,
                radius = 5f,
                center = centro
            )
        }
    }
}

@Composable
private fun RadarLoadingAnimation() {
    val radarColors = LocalRadarColors.current
    val transition = rememberInfiniteTransition(label = "radarLoading")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = radarColors.verde.copy(alpha = pulseAlpha),
                radius = size.minDimension * 0.4f,
                center = centro,
                style = Stroke(2f)
            )
            drawCircle(
                color = radarColors.verdeBrillante,
                radius = 6f,
                center = centro
            )
        }
    }
}

@Composable
private fun PantallaCargando() {
    val radarColors = LocalRadarColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RadarLoadingAnimation()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "ESCANEANDO ENTORNO...",
            color = radarColors.verde,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun PantallaError(mensaje: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = 0.10f))
                .border(1.dp, Color.Red.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.Red,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "ERROR DEL RADAR",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = mensaje,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
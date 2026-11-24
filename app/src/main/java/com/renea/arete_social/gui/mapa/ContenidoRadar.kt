package com.renea.arete_social.gui.mapa

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Spacer
import com.renea.arete_social.gui.mapa.osm.AtribucionMapa
import com.renea.arete_social.gui.mapa.osm.CapaMapa
import com.renea.arete_social.gui.mapa.osm.ColorPin
import com.renea.arete_social.gui.mapa.osm.EstadoCamaraOsm
import com.renea.arete_social.gui.mapa.osm.LineaMapa
import com.renea.arete_social.gui.mapa.osm.MarcadorMapa
import com.renea.arete_social.gui.mapa.osm.OsmMapa
import com.renea.arete_social.gui.mapa.osm.PuntoMapa
import com.renea.arete_social.gui.mapa.osm.TemaMapa
import com.renea.arete_social.gui.mapa.osm.resolverProveedorYTema
import com.renea.arete_social.ContenedorApp
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.feature.auth.PerfilUiState
import com.renea.arete_social.feature.auth.PerfilViewModel
import com.renea.arete_social.feature.comunidades.ComunidadesViewModel
import com.renea.arete_social.feature.mapa.MapaUiState
import com.renea.arete_social.feature.mensajeria.SalasViewModel
import com.renea.arete_social.feature.mensajeria.ChatViewModel
import com.renea.arete_social.feature.tamagotchi.regalos.TipoRegalo
import com.renea.arete_social.gui.mensajeria.ChatScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.renea.arete_social.gui.theme.LocalCicloDia
import com.renea.arete_social.gui.theme.LocalRadarColors

/**
 * Contenido puro del Radar y del mapa (OSMDroid, vía [OsmMapa]) extraído de [MapaScreen.kt]
 * (Principio de Responsabilidad Única - Fase 7 del Plan v3).
 */
@SuppressLint("MissingPermission")
@Composable
fun ContenidoRadar(
    estado: MapaUiState.Listo,
    comunidadesViewModel: ComunidadesViewModel,
    perfilViewModel: PerfilViewModel,
    salasViewModel: SalasViewModel,
    contenedor: ContenedorApp,
    usuarioIdActual: String,
    onAbrirPantallaSecundaria: (PantallaSecundaria) -> Unit,
    onIrMiPerfil: () -> Unit,
    seleccionEvento: PantallaSecundaria.SeleccionarLugarEvento? = null,
    onConfirmarLugarEvento: (Ubicacion) -> Unit = {},
    onCancelarSeleccionEvento: () -> Unit = {},
    focoCoordenadas: PuntoMapa? = null,
    onConsumirFocoCoordenadas: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    val estadoPerfil by perfilViewModel.estado.collectAsState()

    // Chispas y medicina para la mascota de otra persona (gato del mapa o lista de Cercanos).
    val contextoRegalos = LocalContext.current
    val scopeRegalos = rememberCoroutineScope()
    val enviarRegalo: (usuarioId: String, nombre: String, tipo: TipoRegalo) -> Unit = { usuarioId, nombre, tipo ->
        scopeRegalos.launch {
            val resultado = contenedor.gestorRegalosMascota.enviar(usuarioId, tipo)
            Toast.makeText(contextoRegalos, resultado.texto(tipo, nombre), Toast.LENGTH_LONG).show()
        }
    }

    // Responder al estado de un Michi: se manda como mensaje directo (cifrado) a su dueño, sin salir del mapa.
    val responderEstado: suspend (usuarioId: String, estadoTexto: String, respuesta: String) -> Boolean =
        { usuarioId, estadoTexto, respuesta ->
            val chatId = runCatching {
                contenedor.mensajes.obtenerOCrearChatDirecto(usuarioIdActual, usuarioId)
            }.getOrNull()
            if (chatId == null) {
                false
            } else {
                // ViewModel de un solo uso: su ámbito se cancela al vaciar el almacén.
                val almacen = androidx.lifecycle.ViewModelStore()
                try {
                    val vm = androidx.lifecycle.ViewModelProvider(
                        almacen,
                        viewModelFactory {
                            initializer {
                                ChatViewModel(
                                    contenedor.mensajes,
                                    contenedor.repositorioUsuarios,
                                    contenedor.cifradorE2ee,
                                    chatId,
                                    usuarioIdActual,
                                    contenedor.gestorLlamadas,
                                    contenedor.messageRouter,
                                    resolverPerfiles = contenedor.resolverPerfiles,
                                    resolverIdentidad = contenedor.resolverIdentidad
                                )
                            }
                        }
                    )[ChatViewModel::class.java]
                    vm.enviarSinAbrir("↩ Respuesta a tu estado «$estadoTexto»:\n${respuesta.trim()}")
                } finally {
                    almacen.clear()
                }
            }
        }

    val fotoPerfilActual = (estadoPerfil as? PerfilUiState.Listo)?.usuario?.foto
    val nombrePerfilActual = (estadoPerfil as? PerfilUiState.Listo)?.usuario?.nombre ?: ""

    val posicionInicial = estado.ubicacionPropia?.let {
        PuntoMapa(it.latitud, it.longitud)
    } ?: PuntoMapa(0.0, 0.0)

    val cicloActual = LocalCicloDia.current
    val esOscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Capa de mapa seleccionada por el usuario (Estándar, Oscuro, Terreno, Saturado).
    var capaSeleccionada by remember { mutableStateOf(CapaMapa.ESTANDAR) }

    // Proveedor de teselas y tema visual se derivan juntos de la capa y el ciclo día/noche.
    val (proveedorMapa, temaMapa) = remember(capaSeleccionada, esOscuro, cicloActual) {
        resolverProveedorYTema(capaSeleccionada, esOscuro, cicloActual)
    }

    var modoAgregarLugar by remember { mutableStateOf(false) }
    var panelMapaActivo by remember { mutableStateOf<String?>(null) }
    // Chat de Sala general ampliado: la pantalla se divide (mitad superior chat, mitad inferior mapa).
    var chatAmpliado by remember { mutableStateOf(false) }
    var obraPergaminoSeleccionada by remember { mutableStateOf<com.renea.arete_social.domain.model.ObraLibreria?>(null) }
    var camaraCentradaEnUsuario by remember { mutableStateOf(true) }
    var posicionInicialFijada by remember { mutableStateOf(false) }

    val contexto = LocalContext.current
    var mostrarDialogoDescarga by remember { mutableStateOf(false) }
    var gpsHabilitado by remember {
        mutableStateOf(com.renea.arete_social.util.GestorCompatibilidadFabricantes.estaUbicacionHabilitada(contexto))
    }

    // OSMDroid es un mapa 2D: ya no hay inclinación (tilt) como en Google Maps.
    val estadoCamara = remember { EstadoCamaraOsm(posicionInicial, EstadoCamaraOsm.ZOOM_POR_DEFECTO) }

    // Al empezar a elegir el lugar del evento la cámara deja de seguirte: el punto lo marca la mira.
    // Animar la cámara cuando se pide enfocar un punto específico (por ej. un evento o lugar)
    LaunchedEffect(focoCoordenadas) {
        if (focoCoordenadas != null) {
            camaraCentradaEnUsuario = false
            panelMapaActivo = null
            estadoCamara.animar(focoCoordenadas, EstadoCamaraOsm.ZOOM_POR_DEFECTO, 0f, 900L)
            onConsumirFocoCoordenadas()
        }
    }

    LaunchedEffect(seleccionEvento != null) {
        if (seleccionEvento != null) {
            camaraCentradaEnUsuario = false
            panelMapaActivo = null
        }
    }

    LaunchedEffect(estadoCamara.enMovimiento) {
        if (estadoCamara.enMovimiento && estadoCamara.inicioPorGesto) {
            camaraCentradaEnUsuario = false
        }
    }

    LaunchedEffect(estado.ubicacionPropia) {
        estado.ubicacionPropia?.let {
            val nuevaPosicion = PuntoMapa(it.latitud, it.longitud)
            if (!posicionInicialFijada) {
                posicionInicialFijada = true
                estadoCamara.mover(nuevaPosicion, EstadoCamaraOsm.ZOOM_POR_DEFECTO)
            } else if (camaraCentradaEnUsuario) {
                // Conserva el zoom y el giro actuales del usuario.
                estadoCamara.animar(nuevaPosicion, duracionMs = 800L)
            }
        }
    }

    // Marcadores, ruta y punto propio se describen con tipos del mapa (ver gui/mapa/osm/ModeloMapa.kt).
    // Los marcadores de lugares y eventos abren su pantalla al tocarlos; el del amigo muestra su globo.
    val sesionEncuentro by com.renea.arete_social.feature.encuentros.GestorEncuentros.sesionActiva.collectAsState()
    val colorRuta = MaterialTheme.colorScheme.primary.toArgb()
    val encuentroActivo = sesionEncuentro?.estado == com.renea.arete_social.domain.model.EstadoEncuentro.ACTIVO
    val miUbicacionEncuentro = estado.ubicacionPropia
    val amigoUbicacion = sesionEncuentro?.amigoUbicacion
    val hayRuta = encuentroActivo && miUbicacionEncuentro != null && amigoUbicacion != null

    val marcadoresMapa = remember(
        estado.lugaresCercanos, estado.eventosCercanos, estado.visibilidad,
        hayRuta, amigoUbicacion, sesionEncuentro?.amigoNombre
    ) {
        buildList {
            // Las personas ya NO se dibujan como marcadores: un marcador exacto revelaría su
            // ubicación real. Se muestran como Mimis ficticios en CapaMimisMapa (más abajo).
            // Lugares (verde; dorado si solo los ves por el Modo Súper de Michi).\
            estado.lugaresCercanos.forEach { lugar ->
                val vis = estado.visibilidad[com.renea.arete_social.domain.model.claveEntidad(
                    com.renea.arete_social.domain.model.TipoEntidadMapa.LUGAR, lugar.id
                )]
                add(
                    MarcadorMapa(
                        id = "lugar:${lugar.id}",
                        posicion = PuntoMapa(lugar.ubicacion.latitud, lugar.ubicacion.longitud),
                        color = if (vis?.origen == com.renea.arete_social.domain.model.OrigenVisibilidad.SUPER)
                            ColorPin.SUPER else ColorPin.LUGAR,
                        titulo = lugar.nombre
                    )
                )
            }
            // Eventos (violeta; dorado si solo los ves por el Modo Súper).
            estado.eventosCercanos.forEach { evento ->
                val vis = estado.visibilidad[com.renea.arete_social.domain.model.claveEntidad(
                    com.renea.arete_social.domain.model.TipoEntidadMapa.EVENTO, evento.id
                )]
                add(
                    MarcadorMapa(
                        id = "evento:${evento.id}",
                        posicion = PuntoMapa(evento.ubicacion.latitud, evento.ubicacion.longitud),
                        color = if (vis?.origen == com.renea.arete_social.domain.model.OrigenVisibilidad.SUPER)
                            ColorPin.SUPER else ColorPin.EVENTO,
                        titulo = evento.nombre
                    )
                )
            }
            // Amigo del encuentro: si el encuentro está ACTIVO con ubicación conocida, el gatito
            // Compose (MimiAmigoGps en CapaMimisMapa) lo dibuja en su posición GPS real, así que
            // NO se añade un pin OSMDroid para evitar doble marcador. Solo se usa el pin cuando
            // el encuentro está PENDIENTE y el amigo todavía no ha aceptado (sin ubicación aún).
            if (amigoUbicacion != null && !encuentroActivo) {
                add(
                    MarcadorMapa(
                        id = "encuentro:amigo",
                        posicion = PuntoMapa(amigoUbicacion.latitud, amigoUbicacion.longitud),
                        color = ColorPin.AMIGO,
                        titulo = "📍 Encuentro con ${sesionEncuentro?.amigoNombre}",
                        abreInfo = true
                    )
                )
            }
        }
    }

    val lineasMapa = remember(hayRuta, sesionEncuentro?.ruta, miUbicacionEncuentro, amigoUbicacion, colorRuta) {
        if (!hayRuta || miUbicacionEncuentro == null || amigoUbicacion == null) {
            emptyList()
        } else {
            val ruta = sesionEncuentro?.ruta
            val puntos = ruta?.puntos?.takeIf { it.size >= 2 }
                ?: com.renea.arete_social.feature.encuentros.GestorEncuentros.interpolarPuntos(miUbicacionEncuentro, amigoUbicacion)
            listOf(
                LineaMapa(
                    id = "encuentro:ruta",
                    puntos = puntos.map { PuntoMapa(it.latitud, it.longitud) },
                    colorArgb = colorRuta,
                    anchoDp = 4f,
                    // Punteada = línea recta de respaldo (sin ruta por calles); continua = ruta real.
                    punteada = ruta?.porCalles != true
                )
            )
        }
    }

    val ubicacionPropiaMapa = remember(estado.ubicacionPropia) {
        estado.ubicacionPropia?.let { PuntoMapa(it.latitud, it.longitud, it.precisionMetros) }
    }

    // ─── Sala general en el mapa ──────────────────────────────────────────────
    // El ViewModel se crea aquí (no dentro del panel) para que el chat flotante y el ampliado
    // compartan exactamente el mismo estado.
    val chatGeneralVm: ChatViewModel? = if (panelMapaActivo == "salas") {
        viewModel<ChatViewModel>(
            key = "mapa_chat_general_$usuarioIdActual",
            factory = viewModelFactory {
                initializer {
                    ChatViewModel(
                        contenedor.mensajes,
                        contenedor.repositorioUsuarios,
                        contenedor.cifradorE2ee,
                        ID_CHAT_GENERAL,
                        usuarioIdActual,
                        contenedor.gestorLlamadas,
                        contenedor.messageRouter,
                        resolverPerfiles = contenedor.resolverPerfiles,
                        resolverIdentidad = contenedor.resolverIdentidad
                    )
                }
            }
        )
    } else null

    // Al cerrar Sala general el chat vuelve a su tamaño normal.
    LaunchedEffect(panelMapaActivo) {
        if (panelMapaActivo != "salas") chatAmpliado = false
    }
    val chatAmpliadoVisible = chatAmpliado && panelMapaActivo == "salas" && seleccionEvento == null
    // Atrás primero encoge el chat ampliado; después actúa la navegación normal.
    BackHandler(enabled = chatAmpliadoVisible) { chatAmpliado = false }
    // Reparto de la altura: 0 = solo mapa; 1 = mitad chat / mitad mapa (con transición suave).
    val pesoChat by animateFloatAsState(
        targetValue = if (chatAmpliadoVisible) 1f else 0f,
        animationSpec = tween(280),
        label = "peso_chat_ampliado"
    )
    // En Sala general el teclado NO debe empujar ni redimensionar nada de esta pantalla.
    if (panelMapaActivo == "salas") SinReajusteDeTeclado()

    Column(modifier = modifier.fillMaxSize().consumeWindowInsets(WindowInsets.ime)) {
        // Mitad superior: chat ampliado de Sala general.
        if (chatGeneralVm != null && (chatAmpliadoVisible || pesoChat > 0.02f)) {
            PanelChatGeneral(
                viewModel = chatGeneralVm,
                contenedor = contenedor,
                ampliado = true,
                onAlternarAmpliado = { chatAmpliado = false },
                onCerrar = { panelMapaActivo = null },
                modifier = Modifier
                    .weight(pesoChat.coerceAtLeast(0.0001f))
                    .fillMaxWidth()
                    .clipToBounds()
            )
        }

        // Mapa y todos sus controles: ocupan todo el alto, o la mitad inferior con el chat ampliado.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OsmMapa(
                estadoCamara = estadoCamara,
                tema = temaMapa,
                proveedor = proveedorMapa,
                modifier = Modifier.fillMaxSize(),
                marcadores = marcadoresMapa,
                lineas = lineasMapa,
                ubicacionPropia = ubicacionPropiaMapa,
                onClickMapa = { posicion ->
                    if (modoAgregarLugar) {
                        onAbrirPantallaSecundaria(
                            PantallaSecundaria.CrearLugarEn(
                                Ubicacion(posicion.latitud, posicion.longitud, System.currentTimeMillis())
                            )
                        )
                        modoAgregarLugar = false
                    }
                },
                onClickMarcador = { id ->
                    when {
                        id.startsWith("lugar:") ->
                            onAbrirPantallaSecundaria(PantallaSecundaria.VerLugar(id.removePrefix("lugar:")))
                        id.startsWith("evento:") ->
                            onAbrirPantallaSecundaria(PantallaSecundaria.VerEvento(id.removePrefix("evento:")))
                    }
                },
                mostrarDialogoDescarga = mostrarDialogoDescarga,
                onCerrarDialogoDescarga = { mostrarDialogoDescarga = false }
            )

            // Mimis: hasta 14 gatitos que deambulan por el mapa (posiciones ficticias, nunca el GPS).
            // El listado completo sigue en el panel "Cercanos".
            if (!modoAgregarLugar && seleccionEvento == null) {
                CapaMimisMapa(
                    estadoCamara = estadoCamara,
                    personasCercanas = estado.personasCercanas,
                    usuarioIdPropio = usuarioIdActual,
                    onVerPerfil = { id -> onAbrirPantallaSecundaria(PantallaSecundaria.VerPerfil(id)) },
                    onEscribir = { id, nombre ->
                        onAbrirPantallaSecundaria(PantallaSecundaria.AbrirChatConUsuario(id, "Chat: $nombre"))
                    },
                    onEnviarChispa = { id, nombre -> enviarRegalo(id, nombre, TipoRegalo.CHISPA) },
                    onEnviarMedicina = { id, nombre -> enviarRegalo(id, nombre, TipoRegalo.MEDICINA) },
                    onSolicitarSincronizacion = { id ->
                        scopeRegalos.launch {
                            val enviado = contenedor.gestorPasesBle.solicitarSincronizacion(id)
                            Toast.makeText(
                                contextoRegalos,
                                if (enviado) "Solicitud de sincronización enviada" else "No se pudo enviar la solicitud. Acércate e inténtalo de nuevo.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onResponderEstado = responderEstado,
                    obrasPorAutor = remember(estado.obrasCercanasMapa) {
                        estado.obrasCercanasMapa.groupBy { it.autorId }.mapValues { (_, lista) ->
                            lista.maxByOrNull { it.compartidaMapaEn ?: 0L }!!
                        }
                    },
                    onAbrirObra = { obra ->
                        scopeRegalos.launch {
                            runCatching {
                                contenedor.libreria.registrarLecturaObra(obra.id, obra.autorId, usuarioIdActual)
                            }
                        }
                    },
                    amigoEncuentro = sesionEncuentro?.takeIf { encuentroActivo },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (modoAgregarLugar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.60f))
                )
            }

            if (!modoAgregarLugar && seleccionEvento == null) {
                RadarHud(modifier = Modifier.fillMaxSize())
            }

            // Atribución obligatoria de OpenStreetMap (Fase 5): siempre visible, también en los modos de
            // selección. Queda por debajo de los paneles y botones inferiores (que empiezan a 24 dp).
            AtribucionMapa(
                tema = temaMapa,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 4.dp)
            )

            if (seleccionEvento != null) {
                CapaSeleccionLugarEvento(
                    onSeleccionar = {
                        val centro = estadoCamara.centro
                        onConfirmarLugarEvento(Ubicacion(centro.latitud, centro.longitud, System.currentTimeMillis()))
                    },
                    onCancelar = onCancelarSeleccionEvento,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            // Alerta de GPS / Ubicación del sistema desactivada (Xiaomi / Poco / Samsung / Motorola)
            if (!gpsHabilitado) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 70.dp, end = 16.dp)
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                contexto.startActivity(com.renea.arete_social.util.GestorCompatibilidadFabricantes.crearIntentAjustesUbicacion())
                            }
                        },
                    color = Color(0xFF2A1B00),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ubicación (GPS) apagada en el móvil",
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "En Xiaomi y Samsung el radar BLE no encuentra celulares si el GPS está apagado. Toca para activar.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Cabecera compacta a la izquierda: perfil, marca y accesos rápidos.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(radarColors.panel)
                        .border(1.dp, radarColors.verde.copy(alpha = 0.55f), CircleShape)
                        .clickable { onIrMiPerfil() },
                    contentAlignment = Alignment.Center
                ) {
                    if (fotoPerfilActual != null) {
                        AsyncImage(
                            model = fotoPerfilActual,
                            contentDescription = "Mi perfil",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                .clickable { onIrMiPerfil() }
                        )
                    } else {
                        Text(
                            text = nombrePerfilActual.take(1).uppercase().ifBlank { "U" },
                            color = radarColors.verde,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Text(
                    text = "Arete",
                    color = radarColors.verdeBrillante,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )

                // Los tres accesos: círculos pequeños apilados que se despliegan al pulsar.
                AccesosMapaApilados(
                    accesos = listOf(
                        AccesoMapa("personas", "◎", "Cercanos"),
                        AccesoMapa("salas", "▤", "Sala general"),
                        AccesoMapa("libreria", "📚", "Librería")
                    ),
                    idActivo = panelMapaActivo,
                    onSeleccionar = { id -> panelMapaActivo = if (panelMapaActivo == id) null else id }
                )
            }

            // Panel compacto de personas cercanas.
            panelMapaActivo?.takeIf { it == "personas" }?.let { panel ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 136.dp, end = 20.dp)
                        .width(278.dp)
                        .heightIn(max = 350.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PERSONAS CERCANAS",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Escríbele o envíale ✦ chispa y 💊 medicina",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { panelMapaActivo = null }.padding(6.dp))
                        }

                        if (panel == "personas") {
                            if (estado.personasCercanas.isEmpty()) {
                                Text("Todavía no se detectan personas cercanas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 285.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    items(estado.personasCercanas, key = { it.id }) { persona ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    panelMapaActivo = null
                                                    persona.usuarioId?.let { onAbrirPantallaSecundaria(PantallaSecundaria.AbrirChatConUsuario(it, "Chat: ${persona.nombre}")) }
                                                }.padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(radarColors.verde.copy(alpha = 0.18f)).border(1.dp, radarColors.verde.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                                                Text(persona.nombre.take(1).uppercase().ifBlank { "?" }, color = radarColors.verdeBrillante, fontWeight = FontWeight.Bold)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(persona.nombre, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(persona.distanciaMetros?.let { "A ${it.toInt()} m" } ?: "Cerca de ti", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            }
                                            if (persona.usuarioId != null) {
                                                Text(
                                                    "✦",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 17.sp,
                                                    modifier = Modifier
                                                        .clickable { enviarRegalo(persona.usuarioId, persona.nombre, TipoRegalo.CHISPA) }
                                                        .padding(horizontal = 5.dp, vertical = 4.dp)
                                                )
                                                Text(
                                                    "💊",
                                                    fontSize = 15.sp,
                                                    modifier = Modifier
                                                        .clickable { enviarRegalo(persona.usuarioId, persona.nombre, TipoRegalo.MEDICINA) }
                                                        .padding(horizontal = 5.dp, vertical = 4.dp)
                                                )
                                            }
                                            Text("✉", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Chat General integrado en el mapa (versión flotante). Su versión ampliada va arriba, en la
            // mitad superior de la pantalla dividida.
            if (panelMapaActivo == "salas" && !chatAmpliadoVisible && chatGeneralVm != null) {
                PanelChatGeneral(
                    viewModel = chatGeneralVm,
                    contenedor = contenedor,
                    ampliado = false,
                    onAlternarAmpliado = { chatAmpliado = true },
                    onCerrar = { panelMapaActivo = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 136.dp, end = 12.dp)
                        .width(340.dp)
                        .height(420.dp)
                )
            }

            if (panelMapaActivo == "libreria") {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 136.dp, end = 20.dp)
                        .width(286.dp)
                        .heightIn(max = 380.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LIBRERÍA CERCANA",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Obras de cercanos en las últimas 24 h",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                "✕",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { panelMapaActivo = null }.padding(6.dp)
                            )
                        }

                        if (estado.obrasCercanasMapa.isEmpty()) {
                            Text(
                                text = "Aún no hay cuentos ni poemas compartidos cerca en las últimas 24 h.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 310.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(estado.obrasCercanasMapa, key = { it.id }) { obra ->
                                    val esPropia = obra.autorId == usuarioIdActual
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (!esPropia) {
                                                    scopeRegalos.launch {
                                                        runCatching {
                                                            contenedor.libreria.registrarLecturaObra(obra.id, obra.autorId, usuarioIdActual)
                                                        }
                                                    }
                                                }
                                                obraPergaminoSeleccionada = obra
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("📖", fontSize = 18.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = obra.titulo,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val autor = if (esPropia) "ti (Tu obra)" else (obra.autorNombre?.ifBlank { null } ?: "Alguien cercano")
                                            Text(
                                                text = "${obra.tipo.etiqueta} · por $autor",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (!esPropia) {
                                            Text(
                                                text = "✉",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 16.sp,
                                                modifier = Modifier
                                                    .clickable {
                                                        panelMapaActivo = null
                                                        val nombreChat = obra.autorNombre?.ifBlank { null } ?: "Autor"
                                                        onAbrirPantallaSecundaria(PantallaSecundaria.AbrirChatConUsuario(obra.autorId, "Chat: $nombreChat"))
                                                    }
                                                    .padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            obraPergaminoSeleccionada?.let { obra ->
                DialogoPergaminoObra(
                    obra = obra,
                    usuarioIdActual = usuarioIdActual,
                    onCerrar = { obraPergaminoSeleccionada = null },
                    onResponder = { autorId, autorNombre ->
                        obraPergaminoSeleccionada = null
                        onAbrirPantallaSecundaria(PantallaSecundaria.AbrirChatConUsuario(autorId, "Chat: $autorNombre"))
                    },
                    onAbrirPdf = { o, alObtener ->
                        scopeRegalos.launch {
                            runCatching { contenedor.libreria.urlTemporalPdf(o) }.onSuccess(alObtener)
                        }
                    }
                )
            }

            // Controles flotantes: arriba a la derecha el botón único de Mapa (capas y descarga) con la
            // brújula debajo; abajo al centro Alejar · Centrar · Acercar; abajo a la derecha, Agregar evento.
            val coroutineScope = rememberCoroutineScope()

            // Brújula táctica interactiva (orientación al Norte 100% Free Software)
            BrujulaTactico(
                bearing = estadoCamara.bearing,
                onClick = {
                    coroutineScope.launch {
                        estadoCamara.orientarAlNorte()
                    }
                },
                siempreVisible = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = if (!gpsHabilitado) 128.dp else 72.dp)
            )

            // Botón único de Mapa: al pulsarlo se elige la capa o se descarga el mapa offline.
            SelectorCapaMapa(
                capaActual = capaSeleccionada,
                onCapaSeleccionada = { capaSeleccionada = it },
                onDescargarMapa = { mostrarDialogoDescarga = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = if (!gpsHabilitado) 72.dp else 16.dp)
            )

            // Alejar (izquierda) · Centrar (medio) · Acercar (derecha)
            BarraControlesCamara(
                siguiendoUsuario = camaraCentradaEnUsuario,
                onZoomIn = {
                    coroutineScope.launch {
                        estadoCamara.zoomIn()
                    }
                },
                onZoomOut = {
                    coroutineScope.launch {
                        estadoCamara.zoomOut()
                    }
                },
                onCentrar = {
                    estado.ubicacionPropia?.let { loc ->
                        camaraCentradaEnUsuario = true
                        coroutineScope.launch {
                            // Conserva el zoom y el giro actuales.
                            estadoCamara.animar(PuntoMapa(loc.latitud, loc.longitud), duracionMs = 600L)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )

            // Agregar evento (+): el lugar se elige primero sobre el mapa; el formulario viene después.
            BotonCrearEvento(
                onClick = { onAbrirPantallaSecundaria(PantallaSecundaria.SeleccionarLugarEvento()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 28.dp)
            )

            // ====================================================
            // TARJETA FLOTANTE DE ENCUENTRO ACTIVO (§5B.5)
            // ====================================================
            val sesionEncuentroControl by com.renea.arete_social.feature.encuentros.GestorEncuentros.sesionActiva.collectAsState()
            sesionEncuentroControl?.let { sesion ->
                val amigoNombre = sesion.amigoNombre
                val modo = sesion.modo
                val ubicacionPropia = estado.ubicacionPropia
                val ubicacionAmigo = sesion.amigoUbicacion
                val activo = sesion.estado == com.renea.arete_social.domain.model.EstadoEncuentro.ACTIVO
                // Con ruta por calles se usa su largo real; si no, la distancia en línea recta.
                val distancia = sesion.ruta?.distanciaMetros ?: if (ubicacionPropia != null && ubicacionAmigo != null) {
                    com.renea.arete_social.feature.encuentros.GestorEncuentros.calcularDistanciaMetros(
                        ubicacionPropia,
                        ubicacionAmigo
                    )
                } else null
                val enLineaRecta = sesion.ruta?.porCalles == false
                val minutosSinSenal = ubicacionAmigo?.let { (System.currentTimeMillis() - it.timestamp) / 60_000L } ?: 0L
                val minutos = distancia?.let {
                    com.renea.arete_social.feature.encuentros.GestorEncuentros.calcularTiempoEstimadoMinutos(it, modo)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 96.dp, end = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activo) "📍 ENCUENTRO: $amigoNombre" else "⏳ INVITACIÓN ENVIADA: $amigoNombre",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (activo) "✕ Terminar" else "✕ Cancelar",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    com.renea.arete_social.feature.encuentros.GestorEncuentros.terminarEncuentro()
                                }
                            )
                        }

                        if (!activo) {
                            Text(
                                text = "Esperando que $amigoNombre acepte. Hasta entonces no se comparte tu ubicación.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        } else if (distancia != null) {
                            Text(
                                text = "A ${distancia.toInt()} m · ~${minutos} min (${modo.etiqueta})" +
                                    if (enLineaRecta) " · en línea recta" else "",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                            if (minutosSinSenal >= 2) {
                                Text(
                                    text = "Última posición de $amigoNombre: hace $minutosSinSenal min",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Esperando ubicación de $amigoNombre...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activo) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            com.renea.arete_social.feature.encuentros.GestorEncuentros.alternarModoRuta()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Modo: ${modo.etiqueta}",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        onAbrirPantallaSecundaria(
                                            PantallaSecundaria.AbrirChatConUsuario(sesion.amigoId, "Chat: $amigoNombre")
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "💬 Chat",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            } // fin del else (controles normales del mapa)
        } // fin del Box del mapa
    }
}

/**
 * Capa para elegir el lugar de un evento: oscurece el mapa, deja un círculo claro con una mira
 * en el centro y dos botones. No intercepta toques, así el mapa se puede seguir moviendo debajo;
 * el punto elegido es el centro de la pantalla.
 */
@Composable
private fun CapaSeleccionLugarEvento(
    onSeleccionar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            val radio = 78.dp.toPx()
            drawRect(Color.Black.copy(alpha = 0.58f))
            drawCircle(color = Color.Transparent, radius = radio, center = centro, blendMode = BlendMode.Clear)
            drawCircle(color = radarColors.verdeBrillante, radius = radio, center = centro, style = Stroke(2.dp.toPx()))
            val trazo = 2.dp.toPx()
            val hueco = 10.dp.toPx()
            val largo = 22.dp.toPx()
            drawLine(radarColors.verdeBrillante, Offset(centro.x, centro.y - hueco - largo), Offset(centro.x, centro.y - hueco), trazo)
            drawLine(radarColors.verdeBrillante, Offset(centro.x, centro.y + hueco), Offset(centro.x, centro.y + hueco + largo), trazo)
            drawLine(radarColors.verdeBrillante, Offset(centro.x - hueco - largo, centro.y), Offset(centro.x - hueco, centro.y), trazo)
            drawLine(radarColors.verdeBrillante, Offset(centro.x + hueco, centro.y), Offset(centro.x + hueco + largo, centro.y), trazo)
            drawCircle(color = radarColors.verdeBrillante, radius = 3.5.dp.toPx(), center = centro)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(radarColors.panel.copy(alpha = 0.96f))
                .border(1.dp, radarColors.verde.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Elige el lugar del evento", color = radarColors.verdeBrillante, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Mueve el mapa hasta que la mira quede sobre el punto exacto.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(radarColors.panel)
                    .border(1.dp, radarColors.verde.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onCancelar)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancelar", color = radarColors.verdeBrillante, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(radarColors.verde)
                    .clickable(onClick = onSeleccionar)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("📍  Seleccionar lugar", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RadarHud(modifier: Modifier = Modifier) {
    val radarColors = LocalRadarColors.current
    Canvas(modifier = modifier) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radioMax = size.minDimension * 0.45f

        drawCircle(
            color = radarColors.verde.copy(alpha = 0.15f),
            radius = radioMax,
            center = centro,
            style = Stroke(1.5f)
        )
        drawCircle(
            color = radarColors.verde.copy(alpha = 0.10f),
            radius = radioMax * 0.65f,
            center = centro,
            style = Stroke(1.2f)
        )
        drawCircle(
            color = radarColors.verde.copy(alpha = 0.07f),
            radius = radioMax * 0.35f,
            center = centro,
            style = Stroke(1.0f)
        )

        drawLine(
            color = radarColors.verde.copy(alpha = 0.2f),
            start = Offset(centro.x - radioMax, centro.y),
            end = Offset(centro.x + radioMax, centro.y),
            strokeWidth = 1f
        )
        drawLine(
            color = radarColors.verde.copy(alpha = 0.2f),
            start = Offset(centro.x, centro.y - radioMax),
            end = Offset(centro.x, centro.y + radioMax),
            strokeWidth = 1f
        )
    }
}

/**
 * Panel de Sala general. Se usa en dos formas:
 *  - flotante sobre el mapa ([ampliado] = false);
 *  - ampliado, ocupando la mitad superior de la pantalla dividida ([ampliado] = true).
 * El botón discreto ⤢ / ⤡ de la cabecera alterna entre ambas.
 */
@Composable
private fun PanelChatGeneral(
    viewModel: ChatViewModel,
    contenedor: ContenedorApp,
    ampliado: Boolean,
    onAlternarAmpliado: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = LocalRadarColors.current
    Surface(
        modifier = modifier,
        shape = if (ampliado) RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp) else RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ampliado) Modifier.statusBarsPadding() else Modifier)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(radarColors.panel).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SALA GENERAL", color = radarColors.verdeBrillante, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (ampliado) "Chat público · pantalla dividida" else "Chat público · en el mapa",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                // Botón discreto para ampliar / encoger el chat.
                Text(
                    text = if (ampliado) "⤡" else "⤢",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { onAlternarAmpliado() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Text(
                    "✕",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { onCerrar() }.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                )
            }
            androidx.compose.material3.HorizontalDivider(color = radarColors.verde.copy(alpha = 0.25f))
            ChatScreen(
                viewModel = viewModel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                chatId = ID_CHAT_GENERAL,
                tituloChat = "Sala General",
                gestorLlamadas = contenedor.gestorLlamadas,
                onVolverAlMapa = onCerrar,
                esVistaHub = false
            )
        }
    }
}

/**
 * Mientras está en pantalla, el teclado no redimensiona ni sube el contenido de la ventana:
 * solo se superpone. (En esta pantalla, por su forma, el comportamiento habitual de otros chats
 * resulta engorroso.) Al salir se restaura el modo anterior de la ventana.
 */
@Composable
private fun SinReajusteDeTeclado() {
    val contexto = LocalContext.current
    DisposableEffect(contexto) {
        val ventana = contexto.buscarActividad()?.window
        val anterior = ventana?.attributes?.softInputMode
        ventana?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            if (ventana != null && anterior != null) ventana.setSoftInputMode(anterior)
        }
    }
}

private fun Context.buscarActividad(): Activity? {
    var actual: Context? = this
    while (actual is ContextWrapper) {
        if (actual is Activity) return actual
        actual = actual.baseContext
    }
    return null
}

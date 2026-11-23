package com.renea.arete_social

import android.content.Context
import android.bluetooth.BluetoothManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.renea.arete_social.data.crypto.CifradorE2eeAndroid
import com.renea.arete_social.data.mesh.MessageRouterMesh
import com.renea.arete_social.data.mesh.RepositorioMensajesHibrido
import com.renea.arete_social.data.mesh.sync.CommunityMeshSyncManager
import com.renea.arete_social.data.network.wifi.WifiLanMeshBridge
import com.renea.arete_social.data.proximity.ble.ProximityManagerBle
import com.renea.arete_social.data.proximity.gps.ProximityManagerGps
import com.renea.arete_social.data.proximity.hibrido.ProximityManagerHibrido
import com.renea.arete_social.data.community.CommunityFileManager
import com.renea.arete_social.data.community.CommunityJoinRequests
import com.renea.arete_social.data.community.CommunityRepositoryLocal
import com.renea.arete_social.data.community.EpochKeyManager
import com.renea.arete_social.data.community.RepositorioComunidadesLocalFirst
import com.renea.arete_social.domain.repository.RepositorioComunidades
import com.renea.arete_social.data.event.EventRepositoryLocal
import com.renea.arete_social.data.identity.DeviceKeyManager
import com.renea.arete_social.data.identity.IdentityManager
import com.renea.arete_social.data.identity.RecoveryManager
import com.renea.arete_social.data.identity.SignatureManager
import com.renea.arete_social.data.local.room.AppLocalDatabase
import com.renea.arete_social.data.local.ModuloLocal
import com.renea.arete_social.data.local.lugares.RepositorioLugaresLocal
import com.renea.arete_social.data.local.salas.RepositorioSalasLocal
import com.renea.arete_social.data.local.regiones.RepositorioRegionesLocal
import com.renea.arete_social.data.local.amistades.RepositorioAmistadesLocal
import com.renea.arete_social.data.local.encuentros.RepositorioEncuentrosLocal
import com.renea.arete_social.data.local.perfil.RepositorioPublicacionesPerfilLocal
import com.renea.arete_social.data.local.moderacion.RepositorioModeracionLocal
import com.renea.arete_social.domain.crypto.CifradorE2ee
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.proximity.DetectorProximidad
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.data.identity.GestorResolverIdentidad
import com.renea.arete_social.domain.util.hexToByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.renea.arete_social.gui.mapa.PantallaSecundaria

/**
 * Composición manual de dependencias — no hay Hilt ni Koin en este proyecto. Se crea UNA sola
 * vez por proceso, desde [AreteApp] (v2). Antes se creaba en MainActivity.onCreate: cada
 * recreación de la Activity (rotación, modo oscuro, cambio de idioma…) construía otro router,
 * otro servidor GATT y otro SQLiteOpenHelper sin cerrar los anteriores. Se pasa hacia abajo por
 * el árbol de composables (AreteNavHost).
 */
class ContenedorApp(private val context: Context) {
    val identityManager = IdentityManager()

    init {
        identityManager.ensureLoaded(context.applicationContext)
        com.renea.arete_social.data.local.media.AlmacenMediaPerfil.inicializar(context.applicationContext)
        com.renea.arete_social.feature.auth.GestorPerfilDatosTemporales.inicializar(context.applicationContext)
        com.renea.arete_social.feature.auth.GestorAmistades.inicializar(context.applicationContext)
        com.renea.arete_social.feature.auth.GestorPerfilEfemero.inicializar(context.applicationContext)
        com.renea.arete_social.feature.auth.GestorSesionLocal.inicializar(context.applicationContext)
        com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.inicializar(context.applicationContext)
    }

    /** True si el dispositivo tiene conectividad real a internet en este momento. */
    fun hayInternet(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    // Proveedor del usuario activo con fallback al ID criptográfico local.
    private val usuarioIdConFallback: () -> String? = {
        com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoUsuario()
            ?: identityManager.getOrLoadDeviceId(context).toString()
    }

    val cacheUsuarios = com.renea.arete_social.data.local.perfil.CacheUsuariosLocal(
        context.applicationContext.getSharedPreferences("antired_usuarios_cache_prefs", Context.MODE_PRIVATE)
    )

    val repositorioUsuarios: com.renea.arete_social.domain.repository.RepositorioUsuarios by lazy {
        com.renea.arete_social.data.local.perfil.RepositorioUsuariosLocal(
            cacheUsuariosLocal = cacheUsuarios,
            peerProfileDao = localDatabase.peerProfileDao(),
            miIdProveedor = usuarioIdConFallback
        )
    }

    val modulo by lazy {
        ModuloLocal(
            lugares = RepositorioLugaresLocal(context.applicationContext),
            mensajes = mensajes,
            salas = RepositorioSalasLocal(context.applicationContext),
            libreria = libreria,
            amistades = RepositorioAmistadesLocal(),
            encuentros = RepositorioEncuentrosLocal(usuarioIdConFallback),
            publicacionesPerfil = RepositorioPublicacionesPerfilLocal(),
            regiones = RepositorioRegionesLocal(),
            moderacion = RepositorioModeracionLocal(context.applicationContext)
        )
    }


    /** Ruta por calles entre dos personas en un encuentro; cae a línea recta si no hay red. */
    val calculadorRuta: com.renea.arete_social.domain.ruta.CalculadorRuta =
        com.renea.arete_social.data.network.ruta.CalculadorRutaOsrm()

    val cifradorE2ee: CifradorE2ee = CifradorE2eeAndroid(context.applicationContext)

    val petRepository: com.renea.arete_social.feature.tamagotchi.data.PetRepository =
        com.renea.arete_social.feature.tamagotchi.data.PetRepositoryLocal(context.applicationContext)

    val gestorOutboxLocal = com.renea.arete_social.data.local.outbox.GestorOutboxLocal(context.applicationContext)
    val gestorIdentidad = com.renea.arete_social.data.crypto.GestorIdentidadEcdsa(context.applicationContext)
    val pinesIdentidad = com.renea.arete_social.data.crypto.PinesIdentidad(context.applicationContext)

    val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad by lazy {
        GestorResolverIdentidad(identityManager, usuarioIdConFallback)
    }

    // ─── Subsistema Local-First y P2P (Auditoría Arquitectónica v3) ───
    val featureFlagComunidadesP2p: Boolean = true
    val localDatabase: AppLocalDatabase by lazy { AppLocalDatabase.getInstance(context.applicationContext) }
    val deviceKeyManager = DeviceKeyManager()
    val signatureManager = SignatureManager()
    val epochKeyManager = EpochKeyManager()
    val communityLedgers = java.util.concurrent.ConcurrentHashMap<String, com.renea.arete_social.data.community.CommunityLedger>()
    val communityRepositoryLocal by lazy {
        CommunityRepositoryLocal(
            database = localDatabase,
            signatureManager = signatureManager,
            epochKeyManager = epochKeyManager
        )
    }
    val repositorioComunidades: RepositorioComunidades by lazy {
        RepositorioComunidadesLocalFirst(
            localRepo = communityRepositoryLocal,
            remoteRepo = null,
            identityManager = identityManager,
            deviceKeyManager = deviceKeyManager,
            featureFlagP2p = featureFlagComunidadesP2p,
            syncManagerProveedor = { communityMeshSyncManager },
            deviceIdProvider = { identityManager.getOrLoadDeviceId(context) },
            almacenMedia = com.renea.arete_social.data.local.media.AlmacenMediaPerfil,
            context = context.applicationContext,
            gestorArchivosLanProveedor = { gestorArchivosLan },
            resolverIdentidad = resolverIdentidad
        )
    }

    val eventRepositoryLocal by lazy { EventRepositoryLocal(localDatabase, identityManager) }
    val repositorioEventos: com.renea.arete_social.domain.repository.RepositorioEventos by lazy {
        com.renea.arete_social.data.event.RepositorioEventosLocalFirst(
            localRepo = eventRepositoryLocal,
            database = localDatabase,
            remoteRepo = null,
            gestorEventosBleProveedor = { gestorEventosBle },
            resolverIdentidad = resolverIdentidad,
            almacenMedia = com.renea.arete_social.data.local.media.AlmacenMediaPerfil,
            repositorioUsuarios = repositorioUsuarios
        )
    }
    val communityFileManager = CommunityFileManager(epochKeyManager)
    val recoveryManager = RecoveryManager(identityManager)
    val communityJoinRequests = CommunityJoinRequests(epochKeyManager)
    val repositorioMurales by lazy { com.renea.arete_social.data.local.mural.RepositorioMuralesLocal(context.applicationContext) }


    val messageRouter: MessageRouter = MessageRouterMesh(
        usuarioIdProveedor = usuarioIdConFallback,
        gestorIdentidad    = gestorIdentidad,
        pinesIdentidad     = pinesIdentidad,
        resolverClaveRemota = null,
        e2eePubProveedor   = { usuarioIdConFallback()?.let { cifradorE2ee.obtenerClavePublicaPropia(it) } },
        gestorOutboxLocal  = gestorOutboxLocal,
        resolverIdentidad  = resolverIdentidad
    )


    /** Gestor de sincronización P2P de comunidades sobre red mallada. */
    val communityMeshSyncManager by lazy {
        CommunityMeshSyncManager(
            database                 = localDatabase,
            messageRouter            = messageRouter,
            miUsuarioIdProveedor     = usuarioIdConFallback,
            communityRepositoryLocal = communityRepositoryLocal
        )
    }

    // ─── Perfil en texto por Bluetooth: público a cualquiera, privado solo a amistades (sin internet; sin fotos ni PDF) ───
    val cachePerfil = com.renea.arete_social.data.local.perfil.CachePerfilLocal(
        context.applicationContext.getSharedPreferences("antired_perfil_ble_cache", Context.MODE_PRIVATE),
        // Proveedor de intereses/gustos para la sección GUSTOS del manifiesto v2
        interesesProveedor = {
            com.renea.arete_social.data.local.perfil.GestorInteresesUsuario.intereses.value
        },
        // Proveedor de cartas para la sección CARTAS (vacío por defecto; opt-in futuro)
        cartasEnviadasProveedor = null,
        peerSectionDao = localDatabase.peerSectionDao()
    ).also { c ->
        usuarioIdConFallback()?.let { miId ->
            val u = cacheUsuarios.obtener(miId)
            val nombre = u?.nombre ?: com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre()
            val desc = u?.descripcion ?: ""
            val foto = u?.foto ?: com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimaFoto()
            val portada = u?.portada ?: com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimaPortada()
            c.asegurarMiSnapshotInicializado(
                miId = miId,
                nombre = nombre,
                descripcion = desc,
                foto = foto,
                portada = portada,
                privado = u?.perfilPrivado ?: false
            )
        }
    }

    val gestorSnapshotLocal = com.renea.arete_social.data.local.perfil.GestorSnapshotLocal(
        cachePerfilLocal = cachePerfil,
        miIdProveedor = usuarioIdConFallback
    )

    val resolverPerfiles: com.renea.arete_social.domain.repository.ResolverPerfiles by lazy {
        com.renea.arete_social.data.local.perfil.ResolverPerfilesLocal(
            peerProfileDao = localDatabase.peerProfileDao(),
            cacheUsuariosLocal = cacheUsuarios,
            cachePerfilLocal = cachePerfil
        )
    }

    val gestorPerfilMediaBle = com.renea.arete_social.data.mesh.GestorPerfilMediaBle(
        router = messageRouter,
        resolverPerfiles = resolverPerfiles,
        miIdProveedor = usuarioIdConFallback,
        esAmigo = { id -> com.renea.arete_social.feature.auth.GestorAmistades.esAmigo(id) }
    ).also { it.iniciar() }

    val gestorPerfilBle = com.renea.arete_social.data.mesh.GestorPerfilBle(
        router = messageRouter,
        cache = cachePerfil,
        miIdProveedor = usuarioIdConFallback,
        esAmigo = { id -> com.renea.arete_social.feature.auth.GestorAmistades.esAmigo(id) },
        resolverIdentidad = resolverIdentidad,
        onPerfilRecibido = { snapshot ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                resolverPerfiles.registrarPerfilRecibido(snapshot)
                snapshot.fotoHash?.let { hash ->
                    gestorPerfilMediaBle.solicitarMedia(snapshot.usuarioId, hash)
                }
                snapshot.portadaHash?.let { hash ->
                    gestorPerfilMediaBle.solicitarMedia(snapshot.usuarioId, hash)
                }
                snapshot.estados.forEach { estado ->
                    estado.fotoHash?.let { hash ->
                        gestorPerfilMediaBle.solicitarMedia(snapshot.usuarioId, hash)
                    }
                }
            }
        }
    ).also { it.iniciar() }

    val sincronizadorCercanos = com.renea.arete_social.data.mesh.SincronizadorCercanos(
        gestorPerfilBle = gestorPerfilBle,
        resolverPerfiles = resolverPerfiles,
        miIdProveedor = usuarioIdConFallback,
        esAmigo = { id -> com.renea.arete_social.feature.auth.GestorAmistades.esAmigo(id) }
    ).also { it.iniciar() }

    val gestorEventosBle = com.renea.arete_social.data.mesh.GestorEventosBle(
        database = localDatabase,
        router = messageRouter,
        miIdProveedor = usuarioIdConFallback,
        esAmigo = { id -> com.renea.arete_social.feature.auth.GestorAmistades.esAmigo(id) },
        resolverIdentidad = resolverIdentidad
    ).also { gestor ->
        gestor.iniciar()
        // Conectar la señal mesh → señal reactiva del repositorio → refresco de UI.
        // Sin esto, los eventos recibidos vía P2P se guardaban en Room pero MapaViewModel
        // y ComunidadDetalleViewModel no se enteraban hasta que el GPS emitía un nuevo punto.
        gestor.onEventoActualizado = { repositorioEventos.notificarActualizado() }
    }

    val gestorArchivosLan = com.renea.arete_social.data.network.wifi.GestorArchivosLan(
        context = context.applicationContext,
        router = messageRouter,
        miIdProveedor = usuarioIdConFallback
    ).also { it.iniciar() }


    val cartas: com.renea.arete_social.domain.repository.RepositorioCartas by lazy {
        com.renea.arete_social.data.local.cartas.RepositorioCartasLocalFirst(
            context = context,
            messageRouter = messageRouter,
            remoteRepo = null
        )
    }

    val libreria: com.renea.arete_social.domain.repository.RepositorioLibreria by lazy {
        com.renea.arete_social.data.local.libreria.RepositorioLibreriaLocalFirst(
            context = context,
            cachePerfil = cachePerfil,
            remoteRepo = null
        )
    }

    private val esSoloLocalProveedor: () -> Boolean = {
        usuarioIdConFallback()?.startsWith("invitado-local-") == true
    }

    private val mensajesHibrido = RepositorioMensajesHibrido(
        messageRouter          = messageRouter,
        hayInternet            = ::hayInternet,
        miUsuarioIdProveedor   = usuarioIdConFallback,
        gestorOutboxLocal      = gestorOutboxLocal,
        cifradorE2ee           = cifradorE2ee,
        esSoloLocal            = esSoloLocalProveedor,
        pinesIdentidad         = pinesIdentidad,
        resolverIdentidad      = resolverIdentidad,
        gestorIdentidad        = gestorIdentidad
    )

    val mensajes: RepositorioMensajes = mensajesHibrido

    private val scopeApp = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Al recuperar conectividad (o al arrancar con ella) se republica la Outbox pendiente por la red.
     */
    init {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        scopeApp.launch { mensajesHibrido.drenarOutbox() }
                    }
                }
            })
        }

        // Suscripción al flujo P2P de sincronización de comunidades sobre mesh
        scopeApp.launch {
            messageRouter.todosLosPaquetesEntrantes()
                .filter { it.chatId.startsWith(CommunityMeshSyncManager.CHAT_PREFIX) }
                .catch { /* ignorar errores individuales, no detener el flujo */ }
                .collect { paquete -> communityMeshSyncManager.procesarPaqueteMesh(paquete) }
        }

        // Anuncio reactivo de comunidades locales cuando se conectan pares por Bluetooth/LAN
        scopeApp.launch {
            messageRouter.peersConectados
                .filter { it > 0 }
                .catch { /* ignorar errores de emisión */ }
                .collect {
                    runCatching {
                        val comunidades = localDatabase.communityDao().getAllCommunities()
                        for (c in comunidades) {
                            val bytes = c.communityIdHex.hexToByteArray()
                            communityMeshSyncManager.emitirResumenInventario(bytes)
                            communityMeshSyncManager.emitirTodosLosMiembros(c.communityIdHex)
                        }
                    }
                }
        }
    }

    // ─── Wi-Fi LAN: NSD/mDNS + TCP integrado en el mismo MessageRouter ───
    // Se mantiene independiente de BLE: un fallo de un transporte no debe tumbar el otro.
    val wifiLanMesh = WifiLanMeshBridge(
        context = context.applicationContext,
        peerIdProvider = usuarioIdConFallback,
        router = messageRouter,
        onState = { android.util.Log.i("AntiRedNetwork", it) },
        firmarHandshake = { datos -> gestorIdentidad.firmar(datos.toString(Charsets.UTF_8)) ?: "" },
        miClavePublica = { gestorIdentidad.obtenerClavePublicaBase64() ?: "" },
        verificarFirma = { datos, firma, clave -> gestorIdentidad.verificarFirma(datos.toString(Charsets.UTF_8), firma, clave) },
        // S-02: TOFU se delega al MessageRouterMesh centralizado para que ambos transportes
        // (BLE y Wi-Fi LAN) muestren la alerta TOFU y permitan confiar/actualizar la identidad en caso de reinstalación.
        validarClavePeer = null
    )

    private val detectorGps = ProximityManagerGps(context.applicationContext, repositorioUsuarios, usuarioIdProveedor = usuarioIdConFallback)
    private val detectorBle = ProximityManagerBle(
        context = context.applicationContext,
        usuarioIdProveedor = usuarioIdConFallback,
        nombreProveedor = { com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre() },
        visibilidadProveedor = { com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimaVisibilidad() },
        onDispositivoDetectado = { id, nombre ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                resolverPerfiles.registrarParDescubierto(id, nombre)
                sincronizadorCercanos.onParDetectado(id)
                gestorEventosBle.emitirResumenEventos(destinatarioDirecto = id)
            }
        },
        resolverNombreProveedor = { id ->
            cacheUsuarios.obtener(id)?.nombre ?: cachePerfil.recibido(id)?.nombre
        }
    )

    val detectorProximidad: DetectorProximidad =
        ProximityManagerHibrido(detectorGps, detectorBle, wifiLanMesh.dispositivos)

    // ─── Mensajería BLE GATT (sin WiFi, sin pairing) ─────────────────────────
    // RFCOMM (Bluetooth Clásico) requería que los dispositivos fueran descubiertos
    // primero por el stack Clásico (SDP lookup), pero nosotros los descubrimos por BLE,
    val solicitudNavegacionSecundaria = MutableSharedFlow<PantallaSecundaria>(extraBufferCapacity = 5)

    // que es una pila distinta. GATT usa directamente las MACs del escaneo BLE y no
    // necesita pairing previo. Soluciona definitivamente los mensajes que no llegaban.
    private val bluetoothAdapter = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }.getOrNull()

    val gattMensajeria: com.renea.arete_social.data.mesh.GattMensajeriaManager? =
        bluetoothAdapter?.let { adapter ->
            com.renea.arete_social.data.mesh.GattMensajeriaManager(
                context = context.applicationContext,
                bluetoothAdapter = adapter,
                detectorProximidad = detectorBle,
                messageRouter = messageRouter,
                ubicacionPropiaProveedor = {
                    val visibilidad = com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimaVisibilidad()
                    if (visibilidad == com.renea.arete_social.domain.model.Visibilidad.INVISIBLE) {
                        null
                    } else {
                        val loc = detectorGps.ultimaUbicacionConocida
                        if (loc != null && visibilidad == com.renea.arete_social.domain.model.Visibilidad.APROXIMADA) {
                            val latAprox = Math.round(loc.latitud * 1000.0) / 1000.0
                            val lonAprox = Math.round(loc.longitud * 1000.0) / 1000.0
                            loc.copy(latitud = latAprox, longitud = lonAprox)
                        } else {
                            loc
                        }
                    }
                },
                onUbicacionPeerRecibida = { mac, usuarioId, ubi ->
                    detectorBle.actualizarUbicacionDispositivo(usuarioId ?: mac, ubi)
                },
                esPeerAutorizadoParaUbicacion = { mac ->
                    val visibilidad = com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimaVisibilidad()
                    val uid = messageRouter.usuarioAutenticadoDe(mac)
                    visibilidad != com.renea.arete_social.domain.model.Visibilidad.INVISIBLE &&
                        uid != null && com.renea.arete_social.feature.auth.GestorAmistades.esAmigo(uid)
                },
                onEstadoPeerRecibido = { mac, usuarioId, estado, vence ->
                    detectorBle.actualizarEstadoDispositivo(usuarioId ?: mac, estado, vence)
                },
                usuarioIdProveedor = usuarioIdConFallback,
                dispositivoBtProveedor = { mac -> detectorBle.obtenerBluetoothDevice(mac) }
            ).also { it.iniciar() }
        }
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Job de nivel-app que mantiene el BLE scan+advertise activo mientras el mesh esté
     * iniciado, independientemente de si el MapaViewModel está en el foreground.
     * Sin esto el SharedFlow WhileSubscribed detiene el BLE cuando la pantalla del mapa
     * no está visible, y los peers dejan de verse entre sí.
     */
    private var jobBleApp: kotlinx.coroutines.Job? = null

    /** Arranca/reanuda los transportes disponibles. Cada transporte falla de forma aislada. */
    fun iniciarMesh() {
        // S-01: Cargar la identidad criptográfica del usuario actual antes de anunciar nada en mesh.
        val actualUid = usuarioIdConFallback()
        gestorIdentidad.cambiarUsuario(actualUid)
        com.renea.arete_social.feature.auth.GestorAmistades.inicializar(context.applicationContext, actualUid)
        com.renea.arete_social.data.mesh.MeshForegroundService.iniciar(context.applicationContext)
        gattMensajeria?.iniciar()
        runCatching { wifiLanMesh.start() }
            .onFailure { android.util.Log.w("AntiRedNetwork", "No se pudo iniciar Wi-Fi LAN", it) }
        // Mantener el BLE activo a nivel de app: una suscripción persistente al flujo compartido
        // garantiza que el scan+advertise no se detenga cuando el MapaViewModel se destruye.
        if (jobBleApp?.isActive != true) {
            jobBleApp = scopeApp.launch {
                detectorProximidad.descubrir()
                    .catch { android.util.Log.w("AntiRedBle", "Error en suscripción BLE de app: ${it.message}") }
                    .collect { /* solo mantener la suscripción viva */ }
            }
        }
    }

    /**
     * Cierre de sesión: deja de anunciarse y de escuchar como el usuario anterior y olvida el
     * estado en memoria. Sin esto el dispositivo seguía siendo un nodo Mesh con la identidad del
     * usuario que ya cerró sesión.
     */
    fun detenerMesh() {
        // Cancelar la suscripción BLE de nivel-app al cerrar sesión.
        jobBleApp?.cancel()
        jobBleApp = null
        // Lo descubierto y los súper poderes son de la persona que cierra sesión.
        servicioVisionRegional.detener()
        servicioModoSuper.detener()
        gestorPasesBle.reiniciar()
        scopeApp.launch { runCatching { servicioDescubrimiento.limpiar() } }
        gattMensajeria?.detener()
        wifiLanMesh.close()
        messageRouter.reiniciar()
        mensajesHibrido.reiniciarSesion()
        // S-01: Descargar claves de identidad mesh de memoria para que no persistan entre cuentas.
        gestorIdentidad.limpiarIdentidad()
        // S-05: Colgar llamada activa y limpiar estado de audio/sesión de llamadas.
        gestorLlamadas.limpiarSesion()
        // S-06: Limpiar ajustes de parche efímeros (compartidos entre usuarios si no se borran).
        com.renea.arete_social.data.local.ajustes.AjustesParche.limpiar(context.applicationContext)
        com.renea.arete_social.data.mesh.MeshForegroundService.detener(context.applicationContext)
    }

    val gestorLlamadas: com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio =
        com.renea.arete_social.feature.llamadas.GestorLlamadasYAudio(
            context = context.applicationContext,
            repositorioMensajes = mensajes,
            repositorioUsuarios = repositorioUsuarios,
            usuarioIdActualProveedor = { usuarioIdConFallback() ?: "" }
        ).also { gestor ->

            gestor.conectarConMesh(
                messageRouter = messageRouter,
                cifradorE2ee = cifradorE2ee,
                nombreProveedor = { id -> cachePerfil.recibido(id)?.nombre },
                gestorIdentidad = gestorIdentidad,
                pinesIdentidad = pinesIdentidad,
                resolverIdentidad = resolverIdentidad
            )
            gestor.onEstadoMascotaRecibido = { id, est, ven ->
                detectorBle.actualizarEstadoDispositivo(id, est, ven)
            }
            mensajesHibrido.onMensajeNuevoRecibido = { remitenteId, chatId, contenido ->
                gestorPerfilMediaBle.notificarActividadChat()
                sincronizadorCercanos.notificarActividadChat()
                val esCifrado = runCatching { cifradorE2ee.esCifrado(contenido) }.getOrDefault(false)
                val preview = if (esCifrado) {
                    val claveRemitente = cifradorE2ee.obtenerClavePublicaConocida(remitenteId)
                    if (claveRemitente != null) {
                        runCatching {
                            cifradorE2ee.descifrar(
                                textoCifrado = contenido,
                                clavePublicaContraparteBase64 = claveRemitente,
                                miUsuarioId = usuarioIdConFallback() ?: "",
                                remitenteOriginalId = remitenteId,
                                destinatarioOriginalId = usuarioIdConFallback() ?: "",
                                chatId = chatId
                            )
                        }.getOrDefault("Nuevo mensaje")
                    } else {
                        "Nuevo mensaje"
                    }
                } else if (com.renea.arete_social.feature.mensajeria.TextoMensaje.esProtocolo(contenido)) {
                    "Nuevo mensaje"
                } else {
                    contenido.take(60)
                }

                gestor.mostrarNotificacion(
                    com.renea.arete_social.domain.model.NotificacionTactica(
                        tipo = com.renea.arete_social.domain.model.TipoNotificacionTactica.MENSAJE,
                        titulo = "MENSAJE",
                        subtitulo = preview,
                        remitenteId = remitenteId,
                        remitenteNombre = cachePerfil.recibido(remitenteId)?.nombre ?: remitenteId.take(8),
                        chatId = chatId
                    )
                )
            }
        }

    /**
     * Chispas y medicina entre mascotas. Se envían desde el mapa y llegan por el canal de señales
     * (internet, Wi-Fi local o Bluetooth); aquí se conecta la entrega con la mascota de este teléfono.
     */
    val gestorRegalosMascota = com.renea.arete_social.feature.tamagotchi.regalos.GestorRegalosMascota(
        repositorio = petRepository,
        prefs = context.applicationContext.getSharedPreferences("antired_regalos_mascota", Context.MODE_PRIVATE),
        miIdProveedor = usuarioIdConFallback,
        miNombreProveedor = { com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoNombre() },
        transporte = { destinatarioId, regaloId, tipo, cantidad, nombre ->
            gestorLlamadas.enviarRegaloMascota(destinatarioId, regaloId, tipo, cantidad, nombre)
        },
        alNotificar = { regalo, texto ->
            gestorLlamadas.mostrarNotificacion(
                com.renea.arete_social.domain.model.NotificacionTactica(
                    tipo = com.renea.arete_social.domain.model.TipoNotificacionTactica.REGALO_MASCOTA,
                    titulo = "REGALO PARA TU MASCOTA",
                    subtitulo = texto,
                    remitenteId = regalo.remitenteId,
                    remitenteNombre = regalo.remitenteNombre
                )
            )
        }
    ).also { gestor -> gestorLlamadas.alRecibirRegaloMascota = gestor::recibir }

    // ── Descubrimiento de lugares y eventos, regiones y Modo Súper de Michi ──────────────────────
    // Ver PLAN_CHATS_ZONALES_Y_LUGARES.md. Todo es local al teléfono salvo las consultas de lugares,
    // eventos y regiones; los lugares y eventos descubiertos se recuerdan 24 h y se ocultan al vencer.

    /** Tu región más cercana (con histéresis) y la lista de regiones. Una sola fuente para chats y Súper. */
    val servicioRegionActual = com.renea.arete_social.feature.descubrimiento.ServicioRegionActual()

    /** Memoria de los lugares y eventos descubiertos (SQLite propio, sin tocar la base del Outbox). */
    val gestorDescubrimientosLocal =
        com.renea.arete_social.data.local.descubrimientos.GestorDescubrimientosLocal(context.applicationContext)

    val servicioDescubrimiento = com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento(
        almacen = gestorDescubrimientosLocal,
        usuarioIdProveedor = usuarioIdConFallback
    )

    /** Dice si el Michi de la persona está en Modo Súper. */
    val servicioModoSuper = com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper(
        repositorio = petRepository,
        scope = scopeApp,
        // Con la app a la vista ya lo celebra la pantalla de la mascota; si no, se avisa con una notificación.
        alActivarse = { pet ->
            val aLaVista = runCatching {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            }.getOrDefault(false)
            if (!aLaVista) {
                com.renea.arete_social.feature.tamagotchi.notification.PetNotificationHelper
                    .notificarSuperPoderes(context.applicationContext, pet.name)
            }
        }
    )

    /** Con el Súper activo, trae los lugares y eventos públicos de la zona regional más cercana. */
    val servicioVisionRegional = com.renea.arete_social.feature.descubrimiento.ServicioVisionRegional(
        modoSuper = servicioModoSuper.estado,
        region = servicioRegionActual,
        repositorioLugares = modulo.lugares,
        repositorioEventos = repositorioEventos,
        scope = scopeApp
    )

    /**
     * Pases por cercanía: lo que descubriste puede pasar a quien se cruza contigo por Bluetooth (24 h).
     * La firma es la identidad ECDSA de la app; con internet se comprueba que lo recibido existe de verdad.
     */
    val servicioPases = com.renea.arete_social.feature.descubrimiento.ServicioPases(
        descubrimiento = servicioDescubrimiento,
        firmar = { datos -> gestorIdentidad.firmar(datos) },
        miClavePublica = { gestorIdentidad.obtenerClavePublicaBase64() },
        verificarFirma = { datos, firma, clave -> gestorIdentidad.verificarFirma(datos, firma, clave) },
        verificador = com.renea.arete_social.feature.descubrimiento.VerificadorEntidadesRemoto(
            repositorioLugares = modulo.lugares,
            repositorioEventos = repositorioEventos,
            hayInternet = ::hayInternet
        )
    )

    /** Ofrece y recibe pases por la malla BLE. El ajuste «Compartir por cercanía» solo controla lo que se OFRECE. */
    val gestorPasesBle = com.renea.arete_social.data.mesh.GestorPasesBle(
        router = messageRouter,
        servicioPases = servicioPases,
        miIdProveedor = usuarioIdConFallback,
        // Parche activo = la persona acepta compartir con todo el que pida sincronizar (automático, sin
        // confirmar cada vez). El ajuste suelto «Compartir por cercanía» del perfil se mantiene como
        // alternativa, pero ya no puede dejar a Parche "encendido" sin compartir.
        compartirActivado = {
            val app = context.applicationContext
            com.renea.arete_social.data.local.ajustes.AjustesParche.activado(app) ||
                com.renea.arete_social.data.local.ajustes.AjustesPases.compartirActivado(app)
        }
    ).also { it.iniciar() }

    init {
        // Las regiones salen de la tabla `regiones`; mientras tanto (o sin internet) valen las embebidas.
        scopeApp.launch {
            runCatching { modulo.regiones.listar() }.onSuccess { servicioRegionActual.definirRegiones(it) }
        }
    }

    /**
     * Solo válido cuando ya hay sesión iniciada. Todas las pantallas que lo llaman
     * están detrás de Login/Registro en la navegación (AreteNavHost), así que en
     * la práctica siempre hay un usuario autenticado en ese punto.
     */
    fun usuarioIdActual(): String =
        usuarioIdConFallback()
            ?: error("usuarioIdActual() llamado sin sesión iniciada")
}
package com.renea.arete_social.data.network.internet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.renea.arete_social.data.local.ajustes.AjustesRed
import com.renea.arete_social.data.remote.BuzonCiegoCripto
import com.renea.arete_social.data.remote.HttpRelayTransport
import com.renea.arete_social.data.remote.NostrRelayTransport
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.data.proximity.gps.RADIO_CERCANOS_METROS
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private const val TAG = "InternetMeshBridge"

/**
 * Puente de enlace sobre Internet (Wi-Fi y Datos Móviles) para la Red Mallada Híbrida de Aréte.
 *
 * ## Responsabilidades
 * 1. **Detección inteligente de red**: Distingue entre conexión Wi-Fi y Datos Móviles (Celular).
 * 2. **Nodo Pasarela (Gateway / Data Mule)**:
 *    - Recibe paquetes generados localmente o por BLE desde celulares desconectados y los
 *      retransmite por Internet hacia relays o buzones remotos.
 *    - Recibe paquetes desde Internet y los inyecta al [MessageRouter] para que se propaguen
 *      por el aire vía BLE hacia nodos cercanos sin internet.
 * 3. **Diferenciación de tres ámbitos**:
 *    - **Amigos (Global / Sin límite de distancia)**: Mensajes directos cifrados de punto a punto (E2EE)
 *      se enrutan sin importar la distancia física de los interlocutores.
 *    - **Comunidades, Murales y Gacetas a Distancia**: Libros mayores y publicaciones temáticas
 *      se sincronizan globalmente entre todos los nodos suscritos.
 *    - **Radar y Cercanos (Límite estricto ≤ 2 km)**: Los anuncios de presencia se acotan
 *      estrictamente al radio local mediante filtrado geográfico.
 * 4. **Protección de datos móviles**: Permite configurar si los datos pesados se transmiten
 *    solo en Wi-Fi para no agotar el saldo del usuario.
 */
class InternetMeshBridge(
    context: Context,
    private val peerIdProvider: () -> String?,
    private val router: MessageRouter,
    private val esAmigo: (String) -> Boolean,
    private val onState: (String) -> Unit = {},
    private val permitirEnDatosMoviles: () -> Boolean = { true },
    private val maxRadioCercanosMetros: Double = RADIO_CERCANOS_METROS,
    private val firmarHandshake: ((ByteArray) -> String)? = null,
    private val verificarFirma: ((datos: ByteArray, firmaBase64: String, clavePublicaBase64: String) -> Boolean)? = null,
    private val aliasesProveedor: (() -> List<String>)? = null
) : AutoCloseable {

    private val appContext = context.applicationContext ?: context
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _conectadoAInternet = MutableStateFlow(false)
    val conectadoAInternet: StateFlow<Boolean> = _conectadoAInternet.asStateFlow()

    private val _esWifi = MutableStateFlow(false)
    val esWifi: StateFlow<Boolean> = _esWifi.asStateFlow()

    private val _esDatosMoviles = MutableStateFlow(false)
    val esDatosMoviles: StateFlow<Boolean> = _esDatosMoviles.asStateFlow()

    private val paquetesEntrantesInternet = MutableSharedFlow<PaqueteMesh>(extraBufferCapacity = 256)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var peerConnectionRegistrado = AtomicBoolean(false)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    // ─── Relay Ciego Federado (Nostr WebSocket Público y Comunitario) ────────
    /** Intervalo de consulta de buzones en Wi-Fi: cada 8 segundos. */
    private val INTERVALO_RELAY_WIFI_S = 8.seconds
    /** Intervalo de consulta de buzones en Datos Móviles: cada 12 segundos. */
    private val INTERVALO_RELAY_DATOS_S = 12.seconds
    private val senalFetchInmediato = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    private val nostrTransport = NostrRelayTransport(
        relaysProvider = { AjustesRed.obtenerRelaysConfigurados(appContext) }
    )
    private val httpTransport = HttpRelayTransport(
        relayUrlsProveedor = { AjustesRed.obtenerRelaysConfigurados(appContext) }
    )

    /** Selección automática de transporte: si el relay inicia con wss:// o ws:// usa Nostr; si no, HTTP. */
    private val relayTransport = object : com.renea.arete_social.data.remote.RelayTransport {
        private fun transportActivo(): com.renea.arete_social.data.remote.RelayTransport {
            val urls = AjustesRed.obtenerRelaysConfigurados(appContext)
            return if (urls.any { it.startsWith("wss://") || it.startsWith("ws://") }) nostrTransport else httpTransport
        }

        override suspend fun deposit(mailboxTagHex: String, envelopeBytes: ByteArray, ttlSeconds: Long): Boolean =
            transportActivo().deposit(mailboxTagHex, envelopeBytes, ttlSeconds)

        override suspend fun fetch(mailboxTagHex: String, afterTimestampMs: Long): List<com.renea.arete_social.data.remote.RelayEnvelope> =
            transportActivo().fetch(mailboxTagHex, afterTimestampMs)

        override suspend fun ack(mailboxTagHex: String, envelopeIds: List<String>): Boolean =
            transportActivo().ack(mailboxTagHex, envelopeIds)
    }

    /** Registro de marca temporal del último sobre por tag (persistido en SharedPreferences). */
    private val ultimoFetchPorTag = ConcurrentHashMap<String, Long>()
    private val prefsRelay by lazy {
        appContext.getSharedPreferences("arete_relay_fetch_state", Context.MODE_PRIVATE)
    }
    /** Job del bucle periódico de consulta de buzones. */
    private var jobBucleRelay: Job? = null

    // Conexión virtual de tipo PeerConnection que representa el enlace con la red Internet
    private val relayPeerConnection = object : PeerConnection {
        override val dispositivo: Dispositivo = Dispositivo(
            id = "internet-relay-gateway",
            nombre = "Pasarela Internet (Wi-Fi / Datos)",
            señal = -30,
            ultimaConexion = System.currentTimeMillis(),
            usuarioId = "gateway-internet",
            distanciaMetros = null
        )

        override suspend fun conectar(): Boolean = _conectadoAInternet.value

        override suspend fun desconectar() {
            // El ciclo de vida se controla mediante el estado de conectividad del dispositivo
        }

        override suspend fun enviar(paquete: PaqueteMesh): Boolean {
            if (!_conectadoAInternet.value) return false

            // Si estamos en datos móviles y el usuario no lo autorizó, posponer
            if (_esDatosMoviles.value && !permitirEnDatosMoviles()) {
                return false
            }

            // Evaluar elegibilidad según el ámbito del paquete:
            val dest = paquete.destinatarioId
            val tieneDestinatarioDirecto = dest != null
            val esParaAmigo = tieneDestinatarioDirecto && (esAmigo(dest) || paquete.chatId.startsWith("dm_") || !paquete.chatId.startsWith("radar_"))
            val esComunidadOGaceta = paquete.chatId.startsWith("comunidad_") ||
                    paquete.chatId.startsWith("mural_") ||
                    paquete.chatId.startsWith("gaceta_") ||
                    paquete.chatId.startsWith("community:sync:")
            val esCanalGeneralOArea = paquete.chatId == "radar_general" || paquete.chatId.startsWith("local-")
            // PRIVACIDAD TÁCTICA: Jamás transmitir ubicación ni balizas de radar a distancia sobre Internet.
            // La ubicación de los usuarios queda estrictamente acotada al espacio aéreo local (BLE / <= 2 km).
            val contieneDatosUbicacion = paquete.contenido.contains("\"latitud\"") ||
                    paquete.contenido.contains("\"longitud\"") ||
                    paquete.contenido.startsWith("LOC:") ||
                    paquete.contenido.startsWith("GPS:") ||
                    paquete.chatId.startsWith("geo_")

            if (contieneDatosUbicacion) {
                Log.d(TAG, "Privacidad Táctica: Paquete ${paquete.id.take(8)} descartado para Internet (contiene datos de ubicación)")
                return false
            }

            return when {
                tieneDestinatarioDirecto -> {
                    // Mensajes directos cifrados de punto a punto (E2EE) o sincronización de pares
                    transmitirHaciaInternet(paquete)
                }
                esComunidadOGaceta -> {
                    // Comunidades/Gacetas a distancia: alcance temático distribuido
                    transmitirHaciaInternet(paquete)
                }
                esCanalGeneralOArea -> {
                    // Radar local: No se retransmite hacia internet
                    Log.d(TAG, "Privacidad: Canal radar hiperlocal descartado para transmisión por Internet")
                    false
                }
                else -> {
                    transmitirHaciaInternet(paquete)
                }
            }
        }

        override fun observarPaquetes(): Flow<PaqueteMesh> = paquetesEntrantesInternet

        override fun estaConectado(): Boolean = _conectadoAInternet.value
    }

    /**
     * Inicia la supervisión del estado de red e integra el puente en el MessageRouter.
     */
    @Synchronized
    fun start() {
        if (networkCallback != null) return

        p2pTransport.iniciar()
        actualizarEstadoRed()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                actualizarEstadoRed()
            }

            override fun onLost(network: Network) {
                actualizarEstadoRed()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                actualizarEstadoRed()
            }
        }

        networkCallback = cb
        runCatching {
            cm?.registerNetworkCallback(request, cb)
        }.onFailure {
            Log.w(TAG, "No se pudo registrar NetworkCallback para Internet: ${it.message}")
        }
    }

    private fun actualizarEstadoRed() {
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }

        // Consideramos internet activo si tiene la capacidad de internet, permitiendo datos móviles
        // incluso si la validación HTTP de Google aún no termina o está en curso.
        val tieneInternet = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                 caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED))

        val esWifiActivo = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val esCelularActivo = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        _conectadoAInternet.value = tieneInternet
        _esWifi.value = esWifiActivo
        _esDatosMoviles.value = esCelularActivo

        if (tieneInternet) {
            val tipoMedio = when {
                esWifiActivo -> "Wi-Fi"
                esCelularActivo -> "Datos Móviles"
                else -> "Internet"
            }
            onState("Nodo conectado como Pasarela vía $tipoMedio · Relay Ciego Federado activo")
            if (peerConnectionRegistrado.compareAndSet(false, true)) {
                router.registrarPeer(relayPeerConnection)
                Log.i(TAG, "Pasarela Internet registrada exitosamente en MessageRouter")
            }
            // Iniciar consulta periódica de buzones relay
            val miId = peerIdProvider()
            if (miId != null) iniciarBucleRelay(miId)
        } else {
            if (peerConnectionRegistrado.compareAndSet(true, false)) {
                router.removerPeer(relayPeerConnection.dispositivo.id)
                Log.i(TAG, "Sin conexión a Internet: Pasarela retirada de MessageRouter")
            }
            detenerBucleRelay()
        }
    }

    val p2pTransport = WebRtcP2pTransport(
        miPeerId = peerIdProvider,
        firmarDatos = firmarHandshake,
        verificarFirma = verificarFirma
    )

    init {
        scope.launch {
            p2pTransport.paquetesEntrantes.collect { paquete ->
                inyectarPaqueteDesdeInternet(paquete)
            }
        }
    }

    /**
     * Transmite un paquete firmado hacia el canal de Internet:
     * 1. Intenta entrega directa P2P (si hay endpoint UDP activo).
     * 2. Si falla o no hay endpoint, deposita el sobre en el Relay Ciego Federado.
     */
    private suspend fun transmitirHaciaInternet(paquete: PaqueteMesh): Boolean {
        // En datos móviles, respetar política de ahorro de datos: posponer paquetes pesados
        if (_esDatosMoviles.value && AjustesRed.soloTextoEnDatosMoviles(appContext)) {
            val tamanoAprox = paquete.contenido.length * 2
            if (tamanoAprox > AjustesRed.LIMITE_PAQUETE_LIGERO_BYTES) {
                Log.d(TAG, "Ahorro de datos: Paquete pesado (${tamanoAprox}B) pospuesto en conexión móvil para Wi-Fi/BLE")
                return false
            }
        }

        val destino = paquete.destinatarioId
        if (destino != null) {
            // Intentar entrega directa P2P (endpoint activo en caché)
            val enviado = p2pTransport.enviarDirecto(destino, paquete)
            if (enviado) {
                Log.d(TAG, "Paquete ${paquete.id.take(8)} entregado directamente por P2P a $destino")
                return true
            }
            // Fallback: depositar en buzón ciego del destinatario
            Log.d(TAG, "P2P no disponible para $destino → depositando en Relay Ciego")
            return depositarEnRelay(paquete, destinatarioId = destino)
        } else {
            // Canal grupal / comunidad: depositar en buzón de comunidad
            return depositarEnRelay(paquete, destinatarioId = null)
        }
    }

    /**
     * Serializa el paquete, le aplica padding anti-análisis y lo deposita en el relay ciego.
     * El servidor solo ve la etiqueta de buzón y un blob cifrado opaco.
     */
    private suspend fun depositarEnRelay(paquete: PaqueteMesh, destinatarioId: String?): Boolean {
        val tagHex = when {
            destinatarioId != null -> BuzonCiegoCripto.derivarTagBuzonUsuario(destinatarioId)
            paquete.chatId.startsWith("comunidad_") ->
                BuzonCiegoCripto.derivarTagComunidad(paquete.chatId.removePrefix("comunidad_"))
            paquete.chatId.startsWith("community:sync:") ->
                BuzonCiegoCripto.derivarTagComunidad(paquete.chatId.removePrefix("community:sync:"))
            else -> return false // Canal no admitido para relay remoto
        }
        // El paquete ya viene cifrado E2EE; serializamos y aplicamos padding de longitud uniforme
        val jsonBytes = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<PaqueteMesh>(), paquete
        ).toByteArray(Charsets.UTF_8)
        val padded = BuzonCiegoCripto.aplicarPadding(jsonBytes)
        val ttl = if (_esDatosMoviles.value) 24 * 3600L else 72 * 3600L // 24h en datos, 72h en Wi-Fi
        val exito = relayTransport.deposit(tagHex, padded, ttl)
        if (exito) {
            Log.d(TAG, "Sobre ${paquete.id.take(8)} depositado en relay (tag=${tagHex.take(12)}...)")
            forzarFetchInmediato()
        }
        return exito
    }

    fun forzarFetchInmediato() {
        senalFetchInmediato.trySend(Unit)
    }

    /**
     * Bucle periódico de consulta de buzones propios en el relay.
     * Se activa cuando hay conexión a Internet y se detiene al perderla.
     */
    private fun iniciarBucleRelay(usuarioId: String) {
        if (jobBucleRelay?.isActive == true) return
        jobBucleRelay = scope.launch {
            while (isActive) {
                try {
                    val todosMisIds = (aliasesProveedor?.invoke() ?: emptyList()).plus(usuarioId).filter { it.isNotBlank() }.distinct()
                    val tagsAConsultar = todosMisIds.flatMap { BuzonCiegoCripto.tagsActivosParaUsuario(it) }.distinct()
                    for (tag in tagsAConsultar) {
                        val ultimoTagMs = ultimoFetchPorTag.getOrPut(tag) {
                            prefsRelay.getLong("fetch_tag_$tag", System.currentTimeMillis() - BuzonCiegoCripto.VENTANA_ROTACION_MS)
                        }
                        // Margen de seguridad contra desincronización de relojes entre dispositivos (30s)
                        val fetchSinceMs = maxOf(0L, ultimoTagMs - 30_000L)
                        val sobres = relayTransport.fetch(tag, fetchSinceMs)
                        if (sobres.isNotEmpty()) {
                            val ids = mutableListOf<String>()
                            var maxTs = ultimoTagMs
                            for (sobre in sobres) {
                                try {
                                    val sinPadding = BuzonCiegoCripto.removerPadding(sobre.payloadBytes)
                                    val jsonStr = String(sinPadding, Charsets.UTF_8)
                                    val paquete = json.decodeFromString<PaqueteMesh>(jsonStr)
                                    inyectarPaqueteDesdeInternet(paquete)
                                    ids.add(sobre.id)
                                    if (sobre.timestampMs > maxTs) {
                                        maxTs = sobre.timestampMs
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error deserializando sobre relay ${sobre.id.take(8)}: ${e.message}")
                                }
                            }
                            ultimoFetchPorTag[tag] = maxTs
                            prefsRelay.edit().putLong("fetch_tag_$tag", maxTs).apply()
                            if (ids.isNotEmpty()) relayTransport.ack(tag, ids)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error en bucle relay: ${e.message}")
                }
                val intervalo = if (_esWifi.value) INTERVALO_RELAY_WIFI_S else INTERVALO_RELAY_DATOS_S
                kotlinx.coroutines.withTimeoutOrNull(intervalo) {
                    senalFetchInmediato.receive()
                }
            }
        }
    }

    private fun detenerBucleRelay() {
        jobBucleRelay?.cancel()
        jobBucleRelay = null
    }

    /**
     * Inyecta un paquete recibido desde Internet en la red mallada local.
     * Esto permite que este nodo lo redistribuya por BLE hacia celulares sin conexión.
     */
    fun inyectarPaqueteDesdeInternet(paquete: PaqueteMesh) {
        paquetesEntrantesInternet.tryEmit(paquete)
        scope.launch {
            router.enrutar(paquete, "internet:relay")
        }
    }

    override fun close() {
        detenerBucleRelay()
        p2pTransport.close()
        networkCallback?.let { cb ->
            runCatching { cm?.unregisterNetworkCallback(cb) }
            networkCallback = null
        }
        if (peerConnectionRegistrado.compareAndSet(true, false)) {
            router.removerPeer(relayPeerConnection.dispositivo.id)
        }
        _conectadoAInternet.value = false
    }
}

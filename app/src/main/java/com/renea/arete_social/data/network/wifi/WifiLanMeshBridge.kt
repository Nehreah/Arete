package com.renea.arete_social.data.network.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.Dispositivo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Puente entre NSD/TCP local y el enrutador mesh existente. */
class WifiLanMeshBridge(
    context: Context,
    private val peerIdProvider: () -> String?,
    private val router: MessageRouter,
    private val onState: (String) -> Unit = {},
    private val firmarHandshake: ((ByteArray) -> String)? = null,
    private val miClavePublica: (() -> String)? = null,
    private val verificarFirma: ((datos: ByteArray, firmaBase64: String, clavePublicaBase64: String) -> Boolean)? = null,
    // S-02: TOFU — compara la clave pública del handshake con el pin almacenado del peer.
    private val validarClavePeer: ((peerId: String, clavePublicaBase64: String) -> Boolean?)? = null
) : AutoCloseable {
    data class PeerLanInfo(
        val peer: WifiLanTransport.WifiLanPeer,
        @Volatile var lastSeen: Long
    )

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val flows = ConcurrentHashMap<String, MutableSharedFlow<PaqueteMesh>>()
    private val peers = ConcurrentHashMap<String, PeerLanInfo>()
    private val _dispositivos = MutableStateFlow<List<Dispositivo>>(emptyList())
    val dispositivos: StateFlow<List<Dispositivo>> = _dispositivos.asStateFlow()
    @Volatile private var transport: WifiLanTransport? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var watchdogJob: Job? = null

    private fun estaEnWifi(): Boolean {
        val activeNet = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun actualizarDispositivos() {
        val ahora = System.currentTimeMillis()
        _dispositivos.value = peers.values
            .filter { ahora - it.lastSeen <= 25_000L }
            .map { p ->
                Dispositivo(
                    id = p.peer.id,
                    nombre = "Aréte (${p.peer.id.take(8)})",
                    señal = -40,
                    ultimaConexion = p.lastSeen,
                    usuarioId = p.peer.id,
                    distanciaMetros = 1.0,
                    macAddress = null
                )
            }
    }

    @Synchronized fun start() {
        if (transport != null) return
        val id = peerIdProvider()?.takeIf { it.isNotBlank() }
        if (id == null) { onState("Wi-Fi local: esperando identidad de usuario"); return }

        // Monitoreo de conectividad Wi-Fi: purgar peers si se sale de la red local
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    Log.d(TAG, "Conexión Wi-Fi perdida: limpiando todos los peers LAN")
                    peers.keys.toList().forEach(::remove)
                }
            }
            networkCallback = cb
            cm?.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar NetworkCallback para Wi-Fi: ${e.message}")
        }

        val t = WifiLanTransport(
            context = appContext,
            peerId = id,
            onPeerFound = { peer ->
                peers[peer.id] = PeerLanInfo(peer, System.currentTimeMillis())
                actualizarDispositivos()
                val flow = flows.getOrPut(peer.id) { MutableSharedFlow(extraBufferCapacity = 128) }
                val connection = LanPeerConnection(peer, flow, this)
                router.registrarPeer(connection)
                onState("Teléfono cercano encontrado por Wi-Fi local")
            },
            onPayload = { peer, payload ->
                val info = peers[peer.id]
                if (info == null) {
                    peers[peer.id] = PeerLanInfo(peer, System.currentTimeMillis())
                    actualizarDispositivos()
                    val flow = flows.getOrPut(peer.id) { MutableSharedFlow(extraBufferCapacity = 128) }
                    val connection = LanPeerConnection(peer, flow, this)
                    router.registrarPeer(connection)
                } else {
                    info.lastSeen = System.currentTimeMillis()
                }
                try {
                    val packet = json.decodeFromString<PaqueteMesh>(payload.toString(Charsets.UTF_8))
                    flows.getOrPut(peer.id) { MutableSharedFlow(extraBufferCapacity = 128) }.tryEmit(packet)
                    scope.launch { router.enrutar(packet, "wifi:${peer.id}") }
                } catch (e: Exception) {
                    Log.w(TAG, "Paquete LAN inválido descartado", e)
                }
            },
            onState = onState,
            firmarHandshake = firmarHandshake,
            miClavePublica = miClavePublica,
            verificarFirma = verificarFirma,
            validarClavePeer = validarClavePeer,
            onPeerLost = { peerId ->
                Log.d(TAG, "onPeerLost recibido para peer LAN $peerId")
                remove(peerId)
            }
        )
        transport = t
        t.start()

        // Watchdog periódico: verifica alcance Wi-Fi y hace ping a peers inactivos
        watchdogJob = scope.launch {
            while (isActive) {
                delay(10_000L)
                if (!estaEnWifi()) {
                    if (peers.isNotEmpty()) {
                        Log.d(TAG, "Dispositivo no conectado a Wi-Fi: purgando ${peers.size} peers LAN")
                        peers.keys.toList().forEach(::remove)
                    }
                    continue
                }
                val ahora = System.currentTimeMillis()
                for ((peerId, peerInfo) in peers) {
                    if (ahora - peerInfo.lastSeen > 20_000L) {
                        val vivo = transport?.ping(peerInfo.peer) == true
                        if (vivo) {
                            peerInfo.lastSeen = System.currentTimeMillis()
                        } else {
                            Log.d(TAG, "Ping falló para peer LAN $peerId: removiendo por inactividad")
                            remove(peerId)
                        }
                    }
                }
                actualizarDispositivos()
            }
        }
    }

    private suspend fun send(peerId: String, packet: PaqueteMesh): Boolean {
        val peerInfo = peers[peerId] ?: return false
        val t = transport ?: return false
        val bytes = json.encodeToString(packet).toByteArray(Charsets.UTF_8)
        val res = t.send(peerInfo.peer, bytes)
        return if (res.isSuccess) {
            peerInfo.lastSeen = System.currentTimeMillis()
            true
        } else {
            Log.w(TAG, "Envío LAN a $peerId falló (${res.exceptionOrNull()?.message}): removiendo")
            remove(peerId)
            false
        }
    }

    private fun remove(peerId: String) {
        router.removerPeer("wifi:$peerId")
        peers.remove(peerId)
        flows.remove(peerId)
        actualizarDispositivos()
    }

    override fun close() {
        watchdogJob?.cancel()
        watchdogJob = null
        runCatching { networkCallback?.let { cm?.unregisterNetworkCallback(it) } }
        networkCallback = null
        transport?.close()
        transport = null
        peers.keys.toList().forEach(::remove)
        flows.clear()
    }

    private inner class LanPeerConnection(
        private val peer: WifiLanTransport.WifiLanPeer,
        private val incoming: MutableSharedFlow<PaqueteMesh>,
        private val owner: WifiLanMeshBridge
    ) : PeerConnection {
        override val dispositivo: Dispositivo
            get() {
                val last = owner.peers[peer.id]?.lastSeen ?: System.currentTimeMillis()
                return Dispositivo(
                    id = "wifi:${peer.id}", nombre = "Aréte (${peer.id.take(8)})", señal = null,
                    ultimaConexion = last, usuarioId = peer.id,
                    distanciaMetros = null, macAddress = null
                )
            }
        @Volatile private var connected = true
        override suspend fun conectar(): Boolean { connected = peers.containsKey(peer.id); return connected }
        override suspend fun desconectar() { connected = false; owner.remove(peer.id) }
        override suspend fun enviar(paquete: PaqueteMesh): Boolean = connected && owner.send(peer.id, paquete)
        override fun observarPaquetes(): Flow<PaqueteMesh> = incoming
        override fun estaConectado(): Boolean = connected && peers.containsKey(peer.id)
    }

    companion object { private const val TAG = "AntiRedWifiMesh" }
}

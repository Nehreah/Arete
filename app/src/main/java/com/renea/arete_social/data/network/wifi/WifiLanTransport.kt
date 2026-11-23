package com.renea.arete_social.data.network.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Descubrimiento y transporte TCP en la LAN usando Android NSD/mDNS.
 * Solo funciona si los dispositivos pueden comunicarse en la misma red local; no atraviesa
 * redes distintas ni sustituye a BLE/Wi-Fi Direct. Los mensajes usan framing UTF-8 con longitud.
 * La aplicación integradora debe validar/autenticar y cifrar el payload antes de enviarlo.
 */
class WifiLanTransport(
    context: Context,
    private val peerId: String,
    private val onPeerFound: (WifiLanPeer) -> Unit,
    private val onPayload: (peer: WifiLanPeer, payload: ByteArray) -> Unit,
    private val onState: (String) -> Unit = {},
    private val firmarHandshake: ((ByteArray) -> String)? = null,
    private val miClavePublica: (() -> String)? = null,
    private val verificarFirma: ((datos: ByteArray, firmaBase64: String, clavePublicaBase64: String) -> Boolean)? = null,
    // S-02: Validar que la clave pública del handshake coincida con el pin TOFU del peer.
    // Si devuelve null = peer desconocido (aceptar provisionalmente); false = conflicto TOFU (rechazar).
    private val validarClavePeer: ((peerId: String, clavePublicaBase64: String) -> Boolean?)? = null,
    private val onPeerLost: ((peerId: String) -> Unit)? = null
) : AutoCloseable {
    data class WifiLanPeer(val id: String, val host: InetAddress, val port: Int)

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null
    private val running = AtomicBoolean(false)
    private val peers = ConcurrentHashMap<String, WifiLanPeer>()
    private val idPorServicio = ConcurrentHashMap<String, String>()
    private val watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "antired-wifi-watchdog").apply { isDaemon = true }
    }

    private fun esDireccionLocal(a: InetAddress): Boolean {
        // H-08: Rechazar loopback para que otras aplicaciones locales del mismo terminal no inyecten frames
        if (a.isLoopbackAddress) return false
        if (a.isSiteLocalAddress || a.isLinkLocalAddress) return true
        val b = a.address ?: return false
        return b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC
    }

    @Volatile private var server: ServerSocket? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val portReadyLatch = java.util.concurrent.CountDownLatch(1)
    private val peerExecutor: java.util.concurrent.ThreadPoolExecutor =
        java.util.concurrent.ThreadPoolExecutor(
            4,
            16,
            60L,
            java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.ArrayBlockingQueue(32),
            { runnable -> Thread(runnable, "antired-wifi-peer").apply { isDaemon = true } },
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
        )

    fun start() {
        if (!running.compareAndSet(false, true)) return
        runCatching {
            multicastLock = wifiManager?.createMulticastLock("antired_multicast_lock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        Thread({ serve() }, "antired-wifi-server").start()
        // Registration starts after server has bound a port.
        Thread({
            val listo = portReadyLatch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            val port = server?.localPort
            if (listo && port != null && running.get()) {
                registerService(port)
                startDiscovery()
            } else if (running.get()) {
                onState("No se pudo iniciar el servidor TCP local")
            }
        }, "antired-nsd-start").start()
    }

    private fun serve() {
        try {
            val socketServer = ServerSocket()
            socketServer.reuseAddress = true
            socketServer.bind(InetSocketAddress(0))
            server = socketServer
            portReadyLatch.countDown()
            onState("Servidor LAN listo en puerto ${socketServer.localPort}")
            while (running.get()) {
                val socket = socketServer.accept()
                socket.soTimeout = IO_TIMEOUT_MS
                try {
                    peerExecutor.execute { handle(socket) }
                } catch (e: java.util.concurrent.RejectedExecutionException) {
                    Log.w(TAG, "Conexión TCP rechazada por saturación del servidor LAN: ${socket.inetAddress}")
                    runCatching { socket.close() }
                }
            }
        } catch (e: Exception) {
            portReadyLatch.countDown()
            if (running.get()) {
                Log.w(TAG, "Servidor LAN detenido: ${e.message}")
                onState("Error en servidor LAN: ${e.localizedMessage ?: "desconocido"}")
            }
        }
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = safeServiceName(peerId)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("peer", peerId.take(120))
            setAttribute("protocol", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                onState("Servicio LAN anunciado")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onState("No se pudo anunciar el servicio LAN (código $errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Error al retirar anuncio NSD: $errorCode")
            }
        }
        registrationListener = listener
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (e: Exception) { onState("NSD no disponible: ${e.localizedMessage}") }
    }

    @SuppressLint("NewApi")
    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = onState("Buscando pares en la Wi-Fi local")
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!running.get() || serviceInfo.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.d(TAG, "NSD resolve falló: $errorCode")
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val id = info.attributes["peer"]?.toString(Charsets.UTF_8) ?: info.serviceName
                            val host = info.host ?: return
                            if (id == peerId || info.port !in 1..65535) return
                            idPorServicio[info.serviceName] = id
                            val peer = WifiLanPeer(id, host, info.port)
                            peers[id] = peer
                            onPeerFound(peer)
                        }
                    })
                } catch (e: Exception) { Log.d(TAG, "No se pudo resolver NSD: ${e.message}") }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val id = serviceInfo.attributes?.get("peer")?.toString(Charsets.UTF_8)
                    ?: idPorServicio.remove(serviceInfo.serviceName)
                    ?: serviceInfo.serviceName
                peers.remove(id)
                onPeerLost?.invoke(id)
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onState("Falló el descubrimiento Wi-Fi (código $errorCode)")
                runCatching { nsd.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "No se pudo detener NSD: $errorCode")
            }
        }
        discoveryListener = listener
        try { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (e: Exception) { onState("No se pudo iniciar NSD: ${e.localizedMessage}") }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            // M-01: Rechazar conexiones desde direcciones no locales (WAN/datos móviles)
            if (!esDireccionLocal(s.inetAddress)) {
                Log.w(TAG, "Rechazada conexión LAN desde dirección no local: ${s.inetAddress}")
                return
            }
            // M-01: Watchdog de plazo total por conexión (15s) para evitar retención de hilos por Slowloris
            val watchdogKill = watchdog.schedule({ runCatching { s.close() } }, 15, java.util.concurrent.TimeUnit.SECONDS)
            try {
                s.soTimeout = IO_TIMEOUT_MS
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val remoteId = input.readUTF()
                if (remoteId == "__PING__") {
                    output.writeBoolean(true)
                    output.flush()
                    return
                }
                if (remoteId.isBlank() || remoteId == peerId) return

                val timestamp = input.readLong()
                val pubKey = input.readUTF()
                val sig = input.readUTF()

                val ahora = System.currentTimeMillis()
                if (Math.abs(ahora - timestamp) > 60_000L) {
                    Log.w(TAG, "Rechazado frame LAN de $remoteId: timestamp expirado o desfasado")
                    return
                }

                // M-01: Handshake fail-closed. Exigir firma y clave pública obligatorias si verificarFirma está configurado
                if (verificarFirma != null) {
                    if (pubKey.isBlank() || sig.isBlank()) {
                        Log.w(TAG, "Rechazado frame LAN de $remoteId: handshake sin firma o clave")
                        return
                    }
                    val datos = "$remoteId|$timestamp".toByteArray(Charsets.UTF_8)
                    if (!verificarFirma.invoke(datos, sig, pubKey)) {
                        Log.w(TAG, "Rechazado frame LAN de $remoteId: firma criptográfica inválida")
                        return
                    }
                }

                // S-02: TOFU — verificar que la clave pública del peer no difiere de la pinada.
                // null = peer no pinado (aceptar provisionalmente), false = conflicto (rechazar).
                if (validarClavePeer != null && pubKey.isNotBlank()) {
                    val valido = validarClavePeer.invoke(remoteId, pubKey)
                    if (valido == false) {
                        Log.w(TAG, "Rechazado frame LAN de $remoteId: clave pública no coincide con pin TOFU guardado")
                        return
                    }
                }

                val length = input.readInt()
                if (length !in 1..MAX_FRAME_BYTES) return
                val payload = ByteArray(length)
                input.readFully(payload)
                val peer = WifiLanPeer(remoteId, s.inetAddress, s.port)
                onPayload(peer, payload)
                output.writeBoolean(true)
                output.flush()
            } catch (e: Exception) { Log.d(TAG, "Frame LAN rechazado/cortado: ${e.message}") }
            finally { watchdogKill.cancel(false) }
        }
    }

    suspend fun send(peer: WifiLanPeer, payload: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        if (payload.isEmpty() || payload.size > MAX_FRAME_BYTES) return@withContext Result.failure(IllegalArgumentException("El mensaje debe tener entre 1 y $MAX_FRAME_BYTES bytes"))
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = IO_TIMEOUT_MS
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                val timestamp = System.currentTimeMillis()
                val pub = miClavePublica?.invoke() ?: ""
                val datos = "$peerId|$timestamp".toByteArray(Charsets.UTF_8)
                val sig = firmarHandshake?.invoke(datos) ?: ""

                out.writeUTF(peerId)
                out.writeLong(timestamp)
                out.writeUTF(pub)
                out.writeUTF(sig)
                out.writeInt(payload.size)
                out.write(payload)
                out.flush()
                check(input.readBoolean()) { "El par no confirmó recepción" }
            }
        }
    }

    suspend fun ping(peer: WifiLanPeer): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.host, peer.port), 1500)
                socket.soTimeout = 1500
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                out.writeUTF("__PING__")
                out.flush()
                input.readBoolean()
            }
        }.isSuccess
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { discoveryListener?.let { nsd.stopServiceDiscovery(it) } }
        runCatching { registrationListener?.let { nsd.unregisterService(it) } }
        runCatching { server?.close() }
        runCatching { peerExecutor.shutdownNow() }
        runCatching { watchdog.shutdownNow() }
        idPorServicio.clear()
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        multicastLock = null
        discoveryListener = null
        registrationListener = null
        server = null
        peers.clear()
    }

    private fun safeServiceName(value: String): String = "AntiRed-${value.filter { it.isLetterOrDigit() || it == '-' }.take(40)}"

    companion object {
        private const val TAG = "AntiRedWifiLAN"
        private const val SERVICE_TYPE = "_antired._tcp."
        private const val MAX_FRAME_BYTES = 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val IO_TIMEOUT_MS = 10_000
    }
}

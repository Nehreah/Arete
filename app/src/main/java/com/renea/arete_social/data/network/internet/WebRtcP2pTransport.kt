package com.renea.arete_social.data.network.internet

import android.util.Log
import com.renea.arete_social.domain.mesh.PaqueteMesh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private const val TAG = "WebRtcP2pTransport"
private val jsonParser = Json { ignoreUnknownKeys = true }

/**
 * Transporte P2P directo sobre Internet para la red Aréte.
 *
 * ## Filosofía y Funcionamiento
 * 1. **P2P Directo Sin Servidores Centrales**: Utiliza canales directos UDP/DataChannel
 *    entre dispositivos pares atravesando NATs.
 * 2. **Señalización Ligera (Rendezvous / STUN)**: Solo para resolución de candidatos de red
 *    y puertos efímeros de encuentro (SDP/ICE). Los mensajes nunca se almacenan en servidores.
 * 3. **Cifrado E2EE Preservado**: Todo [PaqueteMesh] transmitido viaja protegido de extremo a extremo.
 */
class WebRtcP2pTransport(
    private val miPeerId: () -> String?,
    private val firmarDatos: ((ByteArray) -> String)? = null,
    private val verificarFirma: ((datos: ByteArray, firmaBase64: String, clavePublicaBase64: String) -> Boolean)? = null
) : AutoCloseable {

    data class PeerP2pEndpoint(
        val peerId: String,
        val host: InetAddress,
        val puerto: Int,
        var ultimoContactoMs: Long = System.currentTimeMillis()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var socketUdp: DatagramSocket? = null

    // Directorio de endpoints P2P activos resueltos mediante señalización / encuentro previo
    private val endpointsActivos = ConcurrentHashMap<String, PeerP2pEndpoint>()

    private val _paquetesEntrantes = MutableSharedFlow<PaqueteMesh>(extraBufferCapacity = 128)
    val paquetesEntrantes: Flow<PaqueteMesh> = _paquetesEntrantes.asSharedFlow()

    companion object {
        const val PUERTO_BASE_DEFAULT = 4747
        private const val MAX_DATAGRAM_BYTES = 64 * 1024 // 64 KB por datagrama P2P
        private const val HEADER_MAGICO = 0x41525450 // "ARTP" (Aréte Realtime Transport Protocol v1)
        private const val HEADER_MAGICO_V2 = 0x41525432 // "ART2" (Protocolo autenticado v2)
        private const val MAX_ENDPOINTS = 256
        private const val ENDPOINT_TTL_MS = 10 * 60_000L // 10 minutos de inactividad
    }

    /**
     * Inicia el escucha UDP local para recibir paquetes P2P directos.
     */
    fun iniciar(puerto: Int = PUERTO_BASE_DEFAULT) {
        if (running.compareAndSet(false, true)) {
            try {
                val s = DatagramSocket(puerto).apply {
                    soTimeout = 0 // Bloqueante en hilo de escucha
                    receiveBufferSize = 256 * 1024
                    sendBufferSize = 256 * 1024
                }
                socketUdp = s
                scope.launch { bucleRecepcion(s) }
                Log.i(TAG, "Transporte P2P WebRtc/UDP iniciado en puerto $puerto")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enlazar puerto UDP $puerto, intentando puerto aleatorio: ${e.message}")
                runCatching {
                    val s = DatagramSocket()
                    socketUdp = s
                    scope.launch { bucleRecepcion(s) }
                    Log.i(TAG, "Transporte P2P enlazado en puerto efímero ${s.localPort}")
                }
            }
        }
    }

    private fun podarEndpointsInactivos() {
        val ahora = System.currentTimeMillis()
        endpointsActivos.entries.removeIf { ahora - it.value.ultimoContactoMs > ENDPOINT_TTL_MS }
        if (endpointsActivos.size >= MAX_ENDPOINTS) {
            endpointsActivos.entries.minByOrNull { it.value.ultimoContactoMs }?.let {
                endpointsActivos.remove(it.key)
            }
        }
    }

    /**
     * Registra o actualiza el endpoint directo de un amigo resuelto mediante señalización efímera.
     */
    fun registrarEndpointP2p(peerId: String, host: InetAddress, puerto: Int) {
        if (peerId.isBlank() || peerId.length > 132 || puerto !in 1..65535) return
        podarEndpointsInactivos()
        endpointsActivos[peerId] = PeerP2pEndpoint(peerId, host, puerto)
        Log.d(TAG, "Endpoint P2P registrado para peer $peerId -> ${host.hostAddress}:$puerto")
    }

    /**
     * Envía un paquete directamente al nodo destinatario si su endpoint P2P está registrado y alcanzable.
     */
    suspend fun enviarDirecto(destinatarioPeerId: String, paquete: PaqueteMesh): Boolean {
        val endpoint = endpointsActivos[destinatarioPeerId] ?: return false
        val s = socketUdp ?: return false
        val miId = miPeerId() ?: ""
        if (miId.isBlank() || miId.length > 132) return false

        return try {
            val jsonPayload = jsonParser.encodeToString(paquete).toByteArray(Charsets.UTF_8)
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            val ahora = System.currentTimeMillis()

            val firmador = firmarDatos
            if (firmador != null) {
                val datosAValidar = "$miId|$ahora|${jsonPayload.size}".toByteArray(Charsets.UTF_8)
                val sig = firmador.invoke(datosAValidar)
                dos.writeInt(HEADER_MAGICO_V2)
                dos.writeUTF(miId)
                dos.writeLong(ahora)
                dos.writeUTF(sig)
            } else {
                dos.writeInt(HEADER_MAGICO)
                dos.writeUTF(miId)
                dos.writeLong(ahora)
            }

            dos.writeInt(jsonPayload.size)
            dos.write(jsonPayload)
            dos.flush()

            val bytes = baos.toByteArray()
            if (bytes.size > MAX_DATAGRAM_BYTES) {
                Log.w(TAG, "Paquete P2P excede tamaño máximo de datagrama (${bytes.size} bytes)")
                return false
            }

            val dgram = DatagramPacket(bytes, bytes.size, endpoint.host, endpoint.puerto)
            s.send(dgram)
            Log.d(TAG, "Paquete P2P ${paquete.id.take(8)} enviado directamente a ${endpoint.host.hostAddress}:${endpoint.puerto}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al enviar paquete P2P directo a $destinatarioPeerId: ${e.message}")
            false
        }
    }

    private fun bucleRecepcion(socket: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM_BYTES)
        while (running.get() && !socket.isClosed) {
            try {
                val dgram = DatagramPacket(buffer, buffer.size)
                socket.receive(dgram)

                val bais = ByteArrayInputStream(dgram.data, dgram.offset, dgram.length)
                val dis = DataInputStream(bais)

                val magico = dis.readInt()
                if (magico != HEADER_MAGICO && magico != HEADER_MAGICO_V2) continue

                val remitentePeerId = dis.readUTF()
                if (remitentePeerId.isBlank() || remitentePeerId.length > 132) continue

                val timestamp = dis.readLong()
                val ahora = System.currentTimeMillis()
                // Protección estricta anti-replay (ventana de 60s)
                if (Math.abs(ahora - timestamp) > 60_000L) {
                    Log.w(TAG, "Datagrama P2P de $remitentePeerId rechazado por timestamp desfasado o replay")
                    continue
                }

                val sigV2 = if (magico == HEADER_MAGICO_V2) dis.readUTF() else null
                if (sigV2 != null && sigV2.length > 512) continue

                val len = dis.readInt()
                if (len <= 0 || len > MAX_DATAGRAM_BYTES) continue

                val payloadBytes = ByteArray(len)
                dis.readFully(payloadBytes)

                val json = String(payloadBytes, Charsets.UTF_8)
                val paquete = runCatching { jsonParser.decodeFromString<PaqueteMesh>(json) }.getOrNull()
                if (paquete == null) continue

                // Validación estricta de congruencia de identidad
                if (paquete.remitenteId != remitentePeerId) {
                    Log.w(TAG, "Datagrama P2P rechazado: discordancia entre remitente transport ($remitentePeerId) y paquete (${paquete.remitenteId})")
                    continue
                }

                // Si viene firmado con V2 y disponemos de verificador
                if (magico == HEADER_MAGICO_V2 && sigV2 != null && verificarFirma != null) {
                    val pub = paquete.clavePublicaRemitente
                    if (pub.isNullOrBlank()) continue
                    val datosAValidar = "$remitentePeerId|$timestamp|$len".toByteArray(Charsets.UTF_8)
                    if (!verificarFirma.invoke(datosAValidar, sigV2, pub)) {
                        Log.w(TAG, "Firma de datagrama P2P V2 inválida para $remitentePeerId")
                        continue
                    }
                } else if (paquete.firma == null) {
                    // Rechazar paquetes sin firma criptográfica para evitar secuestro de endpoint
                    Log.w(TAG, "Datagrama P2P de $remitentePeerId descartado: paquete no firmado")
                    continue
                }

                // Solo si el paquete es legítimo y auténtico actualizamos el endpoint
                podarEndpointsInactivos()
                endpointsActivos[remitentePeerId] = PeerP2pEndpoint(
                    peerId = remitentePeerId,
                    host = dgram.address,
                    puerto = dgram.port,
                    ultimoContactoMs = ahora
                )

                _paquetesEntrantes.tryEmit(paquete)
                Log.d(TAG, "Paquete P2P verificado recibido de ${dgram.address.hostAddress}:${dgram.port} [ID: ${paquete.id.take(8)}]")
            } catch (e: Exception) {
                if (!running.get() || socket.isClosed) break
                Log.d(TAG, "Error en recepción datagrama P2P: ${e.message}")
            }
        }
    }

    override fun close() {
        if (running.compareAndSet(true, false)) {
            runCatching { socketUdp?.close() }
            socketUdp = null
            endpointsActivos.clear()
        }
    }
}

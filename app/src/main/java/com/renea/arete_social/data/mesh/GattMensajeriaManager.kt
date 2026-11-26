package com.renea.arete_social.data.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.proximity.DetectorProximidad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "GattManager"

val ANTIRED_GATT_SERVICE_UUID: UUID = UUID.fromString("0000FED3-0000-1000-8000-00805F9B34FB")
val ANTIRED_GATT_MSG_UUID: UUID     = UUID.fromString("0000FED4-0000-1000-8000-00805F9B34FB")
val ANTIRED_GATT_LOC_UUID: UUID     = UUID.fromString("0000FED5-0000-1000-8000-00805F9B34FB")
val ANTIRED_GATT_ESTADO_UUID: UUID  = UUID.fromString("0000FED6-0000-1000-8000-00805F9B34FB")
val ANTIRED_CCCD_UUID: UUID         = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

/**
 * Mensajería offline vía BLE GATT (sin Bluetooth clásico, sin pairing, sin internet).
 *
 * **Servidor**: característica escribible; los fragmentos entrantes se ensamblan con [GattFramer]
 * y se entregan al [MessageRouter]. **Cliente**: una conexión por peer descubierto.
 *
 * ## Correcciones v2
 * - `iniciar()` es idempotente (se llamaba desde ContenedorApp y de nuevo desde MapaScreen; cada
 *   llamada abría otro colector de descubrimiento).
 * - Envío SERIALIZADO por peer: un único consumidor por cliente escribe un fragmento y espera su
 *   `onCharacteristicWrite` antes del siguiente. Antes el flag `escribiendo` no se volvía a activar
 *   tras un ACK, `encolar()` podía lanzar un segundo escritor concurrente y Android (una operación
 *   GATT a la vez) rechazaba escrituras; además los fragmentos NO_RESPONSE también disparan
 *   `onCharacteristicWrite` y desbloqueaban la cola a mitad de un paquete.
 * - Reconexión con retroceso exponencial y límite de fallos (antes: cada 3 s para siempre).
 * - Límite de conexiones salientes simultáneas (Android admite ~7 en total).
 * - `detener()` cierra de verdad las conexiones GATT (antes solo vaciaba el mapa).
 * - Reacciona a Bluetooth apagado/encendido: cierra y reabre el servidor GATT.
 * - El servidor rechaza escrituras preparadas/desplazadas y tramas desproporcionadas.
 * - Característica ANTIRED_GATT_LOC_UUID para intercambiar ubicación GPS sin depender de Internet.
 */
@SuppressLint("MissingPermission")
class GattMensajeriaManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val detectorProximidad: DetectorProximidad,
    private val messageRouter: MessageRouter,
    private val ubicacionPropiaProveedor: () -> com.renea.arete_social.domain.model.Ubicacion? = { null },
    private val onUbicacionPeerRecibida: (mac: String, usuarioId: String?, com.renea.arete_social.domain.model.Ubicacion) -> Unit = { _, _, _ -> },
    private val esPeerAutorizadoParaUbicacion: (mac: String) -> Boolean = { false },
    private val onEstadoPeerRecibido: (mac: String, usuarioId: String?, estado: String?, vence: Long?) -> Unit = { _, _, _, _ -> },
    private val usuarioIdProveedor: () -> String? = { null },
    private val dispositivoBtProveedor: ((mac: String) -> BluetoothDevice?)? = null
) {
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var managerJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + managerJob)
    private val json      = Json { ignoreUnknownKeys = true }
    private val iniciado  = AtomicBoolean(false)

    private var gattServer: BluetoothGattServer? = null
    private var caracteristicaMensajesServidor: BluetoothGattCharacteristic? = null
    private val servidoresPeers = ConcurrentHashMap<String, PeerConnection>()
    private val mtuPorCentral = ConcurrentHashMap<String, Int>()
    private val notifAck = ConcurrentHashMap<String, CompletableDeferred<Int>>()
    private val notifMutex = ConcurrentHashMap<String, Mutex>()
    private val reintentosDiferidos = ConcurrentHashMap<String, Job>()

    private fun tienePermisoConnect(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    /** Idempotente. El reintento de apertura del servidor tras conceder permisos va en el colector. */
    fun iniciar() {
        if (!iniciado.compareAndSet(false, true)) {
            iniciarServidor()   // por si los permisos se concedieron después del primer intento
            return
        }
        if (!managerJob.isActive) {
            managerJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.IO + managerJob)
        }
        ContextCompat.registerReceiver(
            context,
            receptorBluetooth,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        iniciarServidor()
        iniciarConector()
    }

    fun detener() {
        if (!iniciado.compareAndSet(true, false)) return
        Log.d(TAG, "Deteniendo GattMensajeriaManager")
        runCatching { context.unregisterReceiver(receptorBluetooth) }
        cerrarClientes()
        cerrarServidor()
        GattFramer.limpiarTodo()
        managerJob.cancelChildren()
    }

    private val receptorBluetooth = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.d(TAG, "Bluetooth apagado: cerrando servidor y clientes GATT")
                    cerrarClientes()
                    cerrarServidor()
                }
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth encendido: reabriendo servidor GATT")
                    iniciarServidor()   // los clientes se recrean con el siguiente descubrimiento
                }
            }
        }
    }

    // ── Servidor GATT ────────────────────────────────────────────────────────

    @Synchronized
    fun iniciarServidor() {
        if (gattServer != null) return
        if (!bluetoothAdapter.isEnabled) return
        if (!tienePermisoConnect()) {
            Log.w(TAG, "No se puede iniciar servidor GATT: falta permiso BLUETOOTH_CONNECT")
            return
        }
        try {
            val server = btManager.openGattServer(context, serverCallback)
            if (server == null) {
                Log.e(TAG, "openGattServer devolvió null")
                return
            }
            val service = BluetoothGattService(ANTIRED_GATT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val carMsg = BluetoothGattCharacteristic(
                ANTIRED_GATT_MSG_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val cccd = BluetoothGattDescriptor(
                ANTIRED_CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            carMsg.addDescriptor(cccd)
            service.addCharacteristic(carMsg)
            caracteristicaMensajesServidor = carMsg

            val carLoc = BluetoothGattCharacteristic(
                ANTIRED_GATT_LOC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(carLoc)

            val carEstado = BluetoothGattCharacteristic(
                ANTIRED_GATT_ESTADO_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(carEstado)

            server.addService(service)
            gattServer = server
            Log.d(TAG, "Servidor GATT iniciado con características de mensajería (write+notify), ubicación y estado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir servidor GATT: ${e.message}")
        }
    }

    @Synchronized
    private fun cerrarServidor() {
        servidoresPeers.keys.forEach { peerId ->
            messageRouter.removerPeer(peerId)
        }
        servidoresPeers.clear()
        mtuPorCentral.clear()
        notifAck.values.forEach { it.complete(-1) }
        notifAck.clear()
        notifMutex.clear()
        caracteristicaMensajesServidor = null
        runCatching { gattServer?.close() }
        gattServer = null
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                runCatching {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
            if (descriptor.uuid == ANTIRED_CCCD_UUID &&
                java.util.Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            ) {
                Log.d(TAG, "GattServer: Cliente ${device.address} habilitó notificaciones CCCD. Registrando como peer de retorno.")
                registrarClienteComoPeer(device, device.address)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int,
            offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == ANTIRED_GATT_LOC_UUID) {
                val mac = device.address
                if (!esPeerAutorizadoParaUbicacion(mac)) {
                    Log.w(TAG, "Lectura de ubicación GATT rechazada para dispositivo no autorizado: $mac")
                    runCatching {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                    return
                }
                val ubi = ubicacionPropiaProveedor()
                if (ubi == null) {
                    // Si el usuario configuró visibilidad INVISIBLE o no hay ubicación disponible, denegar
                    runCatching {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    }
                    return
                }
                val bytes = java.nio.ByteBuffer.allocate(16)
                .putDouble(ubi.latitud)
                .putDouble(ubi.longitud)
                .array()
                val respuesta = if (offset < bytes.size) bytes.copyOfRange(offset, bytes.size) else ByteArray(0)
                runCatching {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, respuesta)
                }
            } else if (characteristic.uuid == ANTIRED_GATT_ESTADO_UUID) {
                val estadoActual = com.renea.arete_social.data.local.ajustes.AjustesParche.estado(context)
                val vence = com.renea.arete_social.data.local.ajustes.AjustesParche.venceEstado(context)
                val bytes = if (estadoActual != null && vence > System.currentTimeMillis()) {
                    val strBytes = estadoActual.toByteArray(Charsets.UTF_8)
                    java.nio.ByteBuffer.allocate(8 + strBytes.size)
                        .putLong(vence)
                        .put(strBytes)
                        .array()
                } else {
                    ByteArray(0)
                }
                val respuesta = if (offset < bytes.size) bytes.copyOfRange(offset, bytes.size) else ByteArray(0)
                runCatching {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, respuesta)
                }
            } else {
                runCatching {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            Log.d(TAG, "GattServer onCharacteristicWriteRequest: dev=${device.address} prep=$preparedWrite resp=$responseNeeded off=$offset len=${value.size}")
            // Escrituras "preparadas"/con desplazamiento y tramas enormes no forman parte del
            // protocolo: se rechazan (cualquier dispositivo BLE puede escribir en esta característica).
            val aceptable = !preparedWrite && offset == 0 && value.size in 1..MAX_TRAMA_BYTES
            if (aceptable) {
                val payloadCompleto = GattFramer.ensamblar(device.address, value)
                if (payloadCompleto != null) {
                    val paquete = runCatching {
                        json.decodeFromString<PaqueteMesh>(String(payloadCompleto, Charsets.UTF_8))
                    }.getOrNull()
                    if (paquete != null) {
                        scope.launch { messageRouter.enrutar(paquete, origenMac = device.address) }
                        // Registrar este cliente conectado en el Servidor como peer de retorno inmediato vía notificaciones
                        registrarClienteComoPeer(device, paquete.remitenteId)
                        if (clientes[device.address] == null && clientes.size < MAX_CLIENTES) {
                            val disp = Dispositivo(
                                id = paquete.remitenteId,
                                nombre = "Par conectado",
                                señal = null,
                                ultimaConexion = System.currentTimeMillis(),
                                usuarioId = paquete.remitenteId,
                                distanciaMetros = null,
                                macAddress = device.address
                            )
                            val cli = ClienteGatt(device.address, disp)
                            if (clientes.putIfAbsent(device.address, cli) == null) {
                                cli.conectar()
                            }
                        }
                    } else {
                        Log.w(TAG, "[${device.address}] Paquete ensamblado no deserializable")
                    }
                }
            } else {
                Log.w(TAG, "[${device.address}] Escritura rechazada (prep=$preparedWrite off=$offset len=${value.size})")
            }
            if (responseNeeded) {
                runCatching {
                    gattServer?.sendResponse(
                        device, requestId,
                        if (aceptable) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        0, null
                    )
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d(TAG, "GattServer onMtuChanged: dev=${device.address} mtu=$mtu")
            mtuPorCentral[device.address] = mtu
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.d(TAG, "GattServer onNotificationSent: dev=${device.address} status=$status")
            notifAck.remove(device.address)?.complete(status)
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "GattServer onConnectionStateChange: dev=${device.address} status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val peerId = "srv-${device.address}"
                GattFramer.limpiarSesion(device.address)
                mtuPorCentral.remove(device.address)
                notifAck.remove(device.address)?.complete(-1)
                notifMutex.remove(device.address)
                servidoresPeers.remove(peerId)?.let {
                    messageRouter.removerPeer(peerId)
                }
            }
        }
    }

    private fun registrarClienteComoPeer(device: BluetoothDevice, remitenteId: String) {
        val claveServidor = "srv-${device.address}"
        val existente = servidoresPeers[claveServidor]
        if (existente != null) {
            if (existente.dispositivo.usuarioId == device.address && remitenteId != device.address) {
                val actualizado = Dispositivo(
                    id = claveServidor,
                    nombre = "Par conectado (Servidor)",
                    señal = null,
                    ultimaConexion = System.currentTimeMillis(),
                    usuarioId = remitenteId,
                    distanciaMetros = null,
                    macAddress = device.address
                )
                val peerActualizado = object : PeerConnection {
                    override val dispositivo = actualizado
                    override fun estaConectado() = true
                    override suspend fun conectar(): Boolean = true
                    override suspend fun desconectar() {}
                    override fun observarPaquetes() = emptyFlow<PaqueteMesh>()
                    override suspend fun enviar(paquete: PaqueteMesh): Boolean {
                        return enviarNotificacionAlCliente(device, paquete)
                    }
                }
                servidoresPeers[claveServidor] = peerActualizado
                messageRouter.registrarPeer(peerActualizado)
                Log.d(TAG, "GattServer: Peer actualizado con usuarioId real verificado: $claveServidor -> $remitenteId")
            }
            return
        }
        val disp = Dispositivo(
            id = claveServidor,
            nombre = "Par conectado (Servidor)",
            señal = null,
            ultimaConexion = System.currentTimeMillis(),
            usuarioId = remitenteId,
            distanciaMetros = null,
            macAddress = device.address
        )
        val peerServidor = object : PeerConnection {
            override val dispositivo = disp
            override fun estaConectado() = true
            override suspend fun conectar(): Boolean = true
            override suspend fun desconectar() {}
            override fun observarPaquetes() = emptyFlow<PaqueteMesh>()
            override suspend fun enviar(paquete: PaqueteMesh): Boolean {
                return enviarNotificacionAlCliente(device, paquete)
            }
        }
        if (servidoresPeers.putIfAbsent(claveServidor, peerServidor) == null) {
            messageRouter.registrarPeer(peerServidor)
            Log.d(TAG, "GattServer: Peer registrado como receptor de notificaciones: $claveServidor ($remitenteId)")
        }
    }

    private suspend fun enviarNotificacionAlCliente(device: BluetoothDevice, paquete: PaqueteMesh): Boolean {
        val server = gattServer ?: return false
        val charMsg = caracteristicaMensajesServidor ?: return false
        val jsonBytes = runCatching {
            json.encodeToString(paquete).toByteArray(Charsets.UTF_8)
        }.getOrNull() ?: return false

        val mutex = notifMutex.computeIfAbsent(device.address) { Mutex() }
        return mutex.withLock {
            val mtu = mtuPorCentral[device.address] ?: 180
            val tamanoFragmento = (mtu - 3).coerceIn(20, 512)
            val trozos = GattFramer.fragmentar(jsonBytes, mtu = tamanoFragmento)

            for (trozo in trozos) {
                val ack = CompletableDeferred<Int>()
                notifAck[device.address] = ack
                val enviado = try {
                    val exitoDisparo = withContext(Dispatchers.Main) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            server.notifyCharacteristicChanged(device, charMsg, false, trozo) == BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            charMsg.value = trozo
                            @Suppress("DEPRECATION")
                            server.notifyCharacteristicChanged(device, charMsg, false)
                        }
                    }
                    if (!exitoDisparo) {
                        Log.w(TAG, "GattServer: Error o rechazo al disparar notificación a ${device.address}")
                        return@withLock false
                    }
                    // En notificaciones sin confirmación (confirm=false), el callback onNotificationSent puede no dispararse
                    // de forma consistente en todos los chipsets Android. Si devuelve status explícito, lo validamos;
                    // si expira un breve intervalo de 80ms, asumimos éxito del buffer disparado por el controlador.
                    val status = withTimeoutOrNull(80L) { ack.await() }
                    status == null || status == BluetoothGatt.GATT_SUCCESS
                } finally {
                    if (notifAck[device.address] === ack) {
                        notifAck.remove(device.address)
                    }
                }
                if (!enviado) {
                    Log.w(TAG, "GattServer: Falló entrega de fragmento de notificación a ${device.address}")
                    return@withLock false
                }
                delay(25L) // Pacing para evitar saturación del buffer BLE del kernel
            }
            true
        }
    }

    // ── Clientes GATT ────────────────────────────────────────────────────────

    private val clientes = ConcurrentHashMap<String, ClienteGatt>()

    private fun cerrarClientes() {
        clientes.values.forEach { it.cerrar() }
        clientes.clear()
    }

    private fun iniciarConector() {
        scope.launch {
            detectorProximidad.descubrir().collect { dispositivos ->
                if (gattServer == null) iniciarServidor()
                dispositivos.forEach { dispositivo ->
                    val mac = dispositivo.macAddress ?: return@forEach
                    val uid = dispositivo.usuarioId

                    // Limpiar sesiones previas asociadas al mismo usuario con MAC rotada
                    if (uid != null) {
                        clientes.entries.forEach { (prevMac, prevCli) ->
                            if (prevMac != mac && prevCli.dispositivo.usuarioId == uid) {
                                Log.d(TAG, "Reemplazando MAC rotada para $uid: $prevMac -> $mac")
                                prevCli.cerrar()
                                clientes.remove(prevMac)
                            }
                        }
                    }

                    val cliExistente = clientes[mac]
                    if (cliExistente != null) {
                        if (!cliExistente.estaListo() && cliExistente.debeReintentar()) {
                            cliExistente.conectar()
                        }
                        return@forEach
                    }

                    if (clientes.size >= MAX_CLIENTES) {
                        val desconectado = clientes.entries.firstOrNull { !it.value.estaListo() }
                        if (desconectado != null) {
                            desconectado.value.cerrar()
                            clientes.remove(desconectado.key)
                        } else {
                            return@forEach
                        }
                    }

                    val miId = usuarioIdProveedor()
                    // Desempate determinista mínimo: si el par tiene un ID mayor que el nuestro,
                    // nos conectamos de inmediato. Si tiene un ID menor, introducimos un micro-desfase (150ms)
                    // para evitar colisiones de slots al inicializar el enlace BLE.
                    val esIniciador = when {
                        miId == null || uid == null -> true
                        else -> miId < uid
                    }

                    if (!esIniciador) {
                        val jobPrevio = reintentosDiferidos[mac]
                        if (jobPrevio == null || !jobPrevio.isActive) {
                            val nuevoJob = scope.launch {
                                delay(3500L + (mac.hashCode().toLong() and 0x1FF))
                                reintentosDiferidos.remove(mac)
                                // Si el par iniciador ya se conectó a nuestro servidor GATT, ya disponemos de
                                // un enlace bidireccional plenamente operativo; no abrir cliente redundante.
                                if (!clientes.containsKey(mac) && !servidoresPeers.containsKey("srv-$mac")) {
                                    if (clientes.size < MAX_CLIENTES) {
                                        val cliente = ClienteGatt(mac, dispositivo)
                                        if (clientes.putIfAbsent(mac, cliente) == null) cliente.conectar()
                                    }
                                }
                            }
                            reintentosDiferidos[mac] = nuevoJob
                        }
                        return@forEach
                    }

                    val cliente = ClienteGatt(mac, dispositivo)
                    if (clientes.putIfAbsent(mac, cliente) == null) cliente.conectar()
                }
            }
        }
    }

    // ── Cliente GATT de un peer ──────────────────────────────────────────────

    private inner class ClienteGatt(val mac: String, val dispositivo: Dispositivo) {
        @Volatile private var gatt: BluetoothGatt? = null
        @Volatile private var mtu = 23
        @Volatile private var cerrado = false
        @Volatile private var ackPendiente: CompletableDeferred<Int>? = null
        @Volatile private var cccdAck: CompletableDeferred<Boolean>? = null
        @Volatile private var ultimoIntentoConexion = 0L
        private val listo = AtomicBoolean(false)
        private val cola = ArrayDeque<PaqueteMesh>()           // protegido con synchronized(cola)
        private val senal = Channel<Unit>(Channel.CONFLATED)
        private var jobEnvio: Job? = null
        private var jobLecturaUbicacion: Job? = null
        private var fallosSeguidos = 0
        private val gattMutex = Mutex()

        private suspend fun habilitarCccd(g: BluetoothGatt, char: BluetoothGattCharacteristic): Boolean {
            val cccd = char.getDescriptor(ANTIRED_CCCD_UUID) ?: return false
            val ack = CompletableDeferred<Boolean>()
            cccdAck = ack
            return try {
                g.setCharacteristicNotification(char, true)
                val ok = gattMutex.withLock {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }
                if (!ok) return false
                withTimeoutOrNull(2500L) { ack.await() } ?: false
            } finally {
                if (cccdAck === ack) cccdAck = null
            }
        }

        private val maxPayload get() = (mtu - 3).coerceAtLeast(20)

        fun estaListo() = listo.get() && !cerrado

        fun debeReintentar(): Boolean {
            if (cerrado || listo.get()) return false
            val baseBackoffMs = when (fallosSeguidos) {
                0 -> 2_500L
                1 -> 4_000L
                2 -> 8_000L
                else -> 15_000L
            }
            val jitter = (mac.hashCode().toLong() and 0x1FF)
            return (System.currentTimeMillis() - ultimoIntentoConexion > (baseBackoffMs + jitter))
        }

        fun conectar() {
            if (cerrado || !tienePermisoConnect() || !bluetoothAdapter.isEnabled) return
            ultimoIntentoConexion = System.currentTimeMillis()
            val device = dispositivoBtProveedor?.invoke(mac)
                ?: runCatching { bluetoothAdapter.getRemoteDevice(mac) }.getOrNull()
                ?: return
            listo.set(false)
            Log.d(TAG, "[$mac] Iniciando conexión GATT (fallos=$fallosSeguidos)")

            val cb = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    Log.d(TAG, "ClienteGatt onConnectionStateChange: mac=$mac status=$status newState=$newState")
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        gatt = g
                        if (!g.requestMtu(512)) g.discoverServices()   // → onMtuChanged / onServicesDiscovered
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                        alDesconectar(g, status)
                    }
                }

                override fun onMtuChanged(g: BluetoothGatt, mtuSize: Int, status: Int) {
                    Log.d(TAG, "ClienteGatt onMtuChanged: mac=$mac mtuSize=$mtuSize status=$status")
                    if (status == BluetoothGatt.GATT_SUCCESS) mtu = mtuSize
                    g.discoverServices()
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    Log.d(TAG, "ClienteGatt onServicesDiscovered: mac=$mac status=$status")
                    val srv = g.getService(ANTIRED_GATT_SERVICE_UUID)
                    val char = srv?.getCharacteristic(ANTIRED_GATT_MSG_UUID)
                    if (status != BluetoothGatt.GATT_SUCCESS || char == null) {
                        Log.w(TAG, "[$mac] Servicio AntiRed no encontrado (status=$status)")
                        alDesconectar(g, status)
                        return
                    }
                    fallosSeguidos = 0
                    listo.set(true)
                    val self = this@ClienteGatt
                    val idPeerCli = "cli-$mac"
                    val dispCli = self.dispositivo.copy(id = idPeerCli)
                    messageRouter.registrarPeer(object : PeerConnection {
                        override val dispositivo = dispCli
                        override fun estaConectado() = self.estaListo()
                        override suspend fun conectar(): Boolean {
                            if (!self.estaListo()) self.conectar()
                            return self.estaListo()
                        }
                        override suspend fun desconectar() { self.listo.set(false) }
                        override fun observarPaquetes() = emptyFlow<PaqueteMesh>()
                        override suspend fun enviar(paquete: PaqueteMesh): Boolean = self.encolar(paquete)
                    })

                    scope.launch {
                        // 1. Confirmar registro de notificaciones CCCD antes de saturar el stack GATT
                        habilitarCccd(g, char)

                        // 2. Iniciar transmisión serial
                        iniciarEnvio(g, char)

                        // 3. Autenticar enlace punto a punto emitiendo un PING directo firmado
                        val miId = usuarioIdProveedor()
                        val peerUid = self.dispositivo.usuarioId
                        if (miId != null && peerUid != null && miId.isNotBlank() && peerUid.isNotBlank()) {
                            val ping = PaqueteMesh(
                                id = "ping-${java.util.UUID.randomUUID()}",
                                chatId = "DIRECTO",
                                remitenteId = miId,
                                destinatarioId = peerUid,
                                contenido = "[PING_DIRECTO]",
                                ttl = 1,
                                saltosRealizados = 0,
                                timestamp = System.currentTimeMillis()
                            )
                            val pingFirmado = messageRouter.firmarPaquete(ping)
                            self.encolar(pingFirmado)
                        }

                        // 4. Iniciar keepalive y lectura de ubicación/estado
                        val charLoc = srv.getCharacteristic(ANTIRED_GATT_LOC_UUID)
                        val charEstado = srv.getCharacteristic(ANTIRED_GATT_ESTADO_UUID)
                        jobLecturaUbicacion?.cancel()
                        jobLecturaUbicacion = scope.launch {
                            // Primera lectura inmediata (tras breve estabilización de la conexión)
                            delay(500L)
                            var paso = 0
                            while (isActive && estaListo()) {
                                // Leer ubicación y estado solo si no hay operaciones en vuelo
                                if (ackPendiente == null && synchronized(cola) { cola.isEmpty() }) {
                                    gattMutex.withLock {
                                        if (ackPendiente == null && synchronized(cola) { cola.isEmpty() } && estaListo()) {
                                            // Primera iteración: leer ambas características para actualización inmediata
                                            if (paso == 0) {
                                                if (charLoc != null) runCatching { g.readCharacteristic(charLoc) }
                                                delay(300L)
                                                if (charEstado != null) runCatching { g.readCharacteristic(charEstado) }
                                            } else if (paso % 2 == 0) {
                                                if (charLoc != null) runCatching { g.readCharacteristic(charLoc) }
                                            } else {
                                                if (charEstado != null) runCatching { g.readCharacteristic(charEstado) }
                                            }
                                        }
                                    }
                                }
                                paso++
                                // Esperar entre lecturas periódicas (10s): el delay va al FINAL
                                delay(10_000L)
                            }
                        }
                    }
                }

                override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    Log.d(TAG, "ClienteGatt onDescriptorWrite: mac=$mac desc=${descriptor.uuid} status=$status")
                    if (descriptor.uuid == ANTIRED_CCCD_UUID) {
                        cccdAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
                    }
                }

                override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                    Log.d(TAG, "ClienteGatt onCharacteristicWrite: mac=$mac status=$status")
                    ackPendiente?.complete(status)
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
                    if (char.uuid == ANTIRED_GATT_MSG_UUID) {
                        val datos = char.value
                        procesarNotificacionEntrante(mac, datos)
                    }
                }

                override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                    if (char.uuid == ANTIRED_GATT_MSG_UUID) {
                        procesarNotificacionEntrante(mac, value)
                    }
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onCharacteristicRead(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && char.uuid == ANTIRED_GATT_LOC_UUID) {
                        val datos = char.value
                        if (datos != null && datos.size >= 16) {
                            val bb = java.nio.ByteBuffer.wrap(datos)
                            val lat = bb.double
                            val lon = bb.double
                            // M-03: Validar que sean números finitos y dentro de rango geográfico real
                            if (lat.isFinite() && lon.isFinite() &&
                                lat in -90.0..90.0 && lon in -180.0..180.0 &&
                                (lat != 0.0 || lon != 0.0)) {
                                val ubi = com.renea.arete_social.domain.model.Ubicacion(
                                    latitud = lat,
                                    longitud = lon,
                                    timestamp = System.currentTimeMillis()
                                )
                                onUbicacionPeerRecibida(mac, dispositivo.usuarioId, ubi)
                            }
                        }
                    } else if (status == BluetoothGatt.GATT_SUCCESS && char.uuid == ANTIRED_GATT_ESTADO_UUID) {
                        val datos = char.value
                        if (datos != null && datos.size >= 8) {
                            val bb = java.nio.ByteBuffer.wrap(datos)
                            val vence = bb.long
                            val textoBytes = ByteArray(datos.size - 8)
                            bb.get(textoBytes)
                            val estadoTexto = String(textoBytes, Charsets.UTF_8).trim()
                            if (estadoTexto.isNotEmpty() && vence > System.currentTimeMillis()) {
                                onEstadoPeerRecibido(mac, dispositivo.usuarioId, estadoTexto, vence)
                            } else {
                                onEstadoPeerRecibido(mac, dispositivo.usuarioId, null, null)
                            }
                        } else if (datos != null && datos.isEmpty()) {
                            onEstadoPeerRecibido(mac, dispositivo.usuarioId, null, null)
                        }
                    }
                }
            }

            runCatching {
                device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
            }.onFailure { Log.e(TAG, "[$mac] Error en connectGatt: ${it.message}") }
        }

        private fun procesarNotificacionEntrante(origenMac: String, value: ByteArray?) {
            if (value == null || value.isEmpty()) return
            val payloadCompleto = GattFramer.ensamblar(origenMac, value)
            if (payloadCompleto != null) {
                val paquete = runCatching {
                    json.decodeFromString<PaqueteMesh>(String(payloadCompleto, Charsets.UTF_8))
                }.getOrNull()
                if (paquete != null) {
                    scope.launch { messageRouter.enrutar(paquete, origenMac = origenMac) }
                }
            }
        }

        private fun alDesconectar(g: BluetoothGatt, status: Int) {
            Log.d(TAG, "[$mac] Desconectado (status=$status)")
            listo.set(false)
            jobEnvio?.cancel()
            jobLecturaUbicacion?.cancel()
            ackPendiente?.complete(-1)
            ackPendiente = null
            cccdAck?.complete(false)
            cccdAck = null
            messageRouter.removerPeer("cli-$mac")
            GattFramer.limpiarSesion(mac)
            runCatching { g.close() }
            gatt = null
            if (cerrado) return

            fallosSeguidos++
            if (fallosSeguidos > MAX_FALLOS_SEGUIDOS) {
                // Se libera el hueco; el descubrimiento lo recreará si el peer sigue a la vista.
                cerrar()
                clientes.remove(mac, this)
                return
            }
            // Si fue una desconexión tras haber estado conectado, reintentar pronto sin castigar con 60s
            val espera = if (fallosSeguidos <= 1) 2_000L else minOf(3_000L shl minOf(fallosSeguidos - 2, 3), 20_000L)
            scope.launch {
                delay(espera)
                if (!cerrado && clientes[mac] === this@ClienteGatt && !listo.get()) conectar()
            }
        }

        fun cerrar() {
            cerrado = true
            listo.set(false)
            jobEnvio?.cancel()
            jobLecturaUbicacion?.cancel()
            ackPendiente?.complete(-1)
            ackPendiente = null
            cccdAck?.complete(false)
            cccdAck = null
            messageRouter.removerPeer("cli-$mac")
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        /** @return true si se aceptó en la cola (la entrega real es asíncrona). */
        fun encolar(paquete: PaqueteMesh): Boolean {
            if (cerrado) return false
            synchronized(cola) {
                if (cola.size >= MAX_COLA_POR_PEER) cola.removeFirst()   // descarta el más antiguo
                cola.addLast(paquete)
            }
            if (!estaListo()) {
                conectar()
            } else {
                senal.trySend(Unit)
            }
            return true
        }

        /** Un único consumidor por conexión: la cola se procesa estrictamente en serie. */
        private fun iniciarEnvio(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
            jobEnvio?.cancel()
            jobEnvio = scope.launch {
                senal.trySend(Unit)   // por si quedó algo en cola de una conexión anterior
                while (isActive && listo.get()) {
                    val paquete = synchronized(cola) { cola.removeFirstOrNull() }
                    if (paquete == null) {
                        senal.receive()
                        continue
                    }
                    if (!enviarPaquete(g, char, paquete)) {
                        synchronized(cola) { cola.addFirst(paquete) }
                        delay(REINTENTO_ENVIO_MS)
                    }
                }
            }
        }

        private suspend fun enviarPaquete(g: BluetoothGatt, char: BluetoothGattCharacteristic, paquete: PaqueteMesh): Boolean {
            val bytes = runCatching { json.encodeToString(paquete).toByteArray(Charsets.UTF_8) }.getOrNull()
                ?: return true   // no serializable: descartar (reintentar no lo arreglaría)
            val fragmentos = GattFramer.fragmentar(bytes, maxPayload)
            for (fragmento in fragmentos) {
                val ack = CompletableDeferred<Int>()
                ackPendiente = ack
                try {
                    if (!escribir(g, char, fragmento)) return false
                    val status = withTimeoutOrNull(TIMEOUT_ACK_MS) { ack.await() }
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "[$mac] Fragmento sin ACK válido (status=$status)")
                        return false
                    }
                } finally {
                    if (ackPendiente === ack) {
                        ackPendiente = null
                    }
                }
            }
            return true
        }

        /** Todos los fragmentos van con ACK (WRITE_TYPE_DEFAULT): una sola operación GATT en vuelo. */
        @Suppress("DEPRECATION")
        private suspend fun escribir(g: BluetoothGatt, char: BluetoothGattCharacteristic, datos: ByteArray): Boolean {
            repeat(6) { intento ->
                val ok = gattMutex.withLock {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeCharacteristic(char, datos, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                                BluetoothStatusCodes_SUCCESS
                        } else {
                            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            char.value = datos
                            g.writeCharacteristic(char)
                        }
                    }.getOrDefault(false)
                }
                if (ok) return true
                // Liberar el mutex antes del backoff para no bloquear lecturas de ubicación
                if (intento < 5) delay(80)
            }
            return false
        }
    }

    companion object {
        /** android.bluetooth.BluetoothStatusCodes.SUCCESS (0) sin depender de la clase (API 33). */
        private const val BluetoothStatusCodes_SUCCESS = 0
        private const val MAX_TRAMA_BYTES = 600
        private const val MAX_CLIENTES = 6
        private const val MAX_FALLOS_SEGUIDOS = 8
        private const val MAX_COLA_POR_PEER = 256
        private const val TIMEOUT_ACK_MS = 5_000L
        private const val REINTENTO_ENVIO_MS = 500L
    }
}

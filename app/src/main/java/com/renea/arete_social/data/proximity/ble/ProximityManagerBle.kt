package com.renea.arete_social.data.proximity.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.proximity.DetectorProximidad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

private const val TAG = "ProximityBLE"

// UUID de servicio dedicado de 16 bits para nodos de AntiRed Social (0xFED0)
// Compatible con paquetes de anuncio legados BLE (límite de 31 bytes)
val ANTIRED_SERVICE_UUID: UUID = UUID.fromString("0000FED0-0000-1000-8000-00805F9B34FB")
val ANTIRED_PARCEL_UUID = ParcelUuid(ANTIRED_SERVICE_UUID)

// Segundo UUID de servicio (0xFED1), exclusivo para el nombre de perfil. Va en un
// paquete de "scan response" separado del anuncio principal porque el ID (16 bytes)
// ya casi llena el límite de 31 bytes del paquete legado: no entran los dos en uno.
val ANTIRED_NOMBRE_SERVICE_UUID: UUID = UUID.fromString("0000FED1-0000-1000-8000-00805F9B34FB")
val ANTIRED_NOMBRE_PARCEL_UUID = ParcelUuid(ANTIRED_NOMBRE_SERVICE_UUID)

private const val VENTANA_EXPIRACION_BLE_MS  = 45_000L
private const val TX_POWER_DEFAULT           = -59.0   // Potencia de transmisión promedio medida a 1m
private const val EXPONENTE_PROPAGACION      = 2.0     // Exponente de pérdidas en espacio libre

// §1.1 — Constantes BLE derivadas matemáticamente (sin magic numbers)
// Paquete Scan Response BLE legado: límite absoluto = 31 bytes.
// Overhead por anunciar el UUID128 del servicio de nombre (ANTIRED_NOMBRE_PARCEL_UUID):
//   2 bytes (cabecera AD: longitud + tipo)
//   + 16 bytes (UUID128)
//   + 2 bytes (cabecera AD de datos de servicio: longitud + tipo)
//   = 20 bytes de overhead → quedan 11 bytes para el payload del nombre.
private const val LIMITE_BLE_LEGADO_BYTES    = 31
private const val OVERHEAD_SCAN_RESPONSE_BYTES = 2 + 16 + 2  // AD header + UUID128 + AD data header
private const val MAX_BYTES_NOMBRE           = LIMITE_BLE_LEGADO_BYTES - OVERHEAD_SCAN_RESPONSE_BYTES  // = 11

/**
 * Tiempo en modo LOW_LATENCY antes de bajar a BALANCED para ahorrar batería.
 * 30 s de discovery agresivo son suficientes para encontrar peers cercanos;
 * después BALANCED mantiene el scan activo con menor impacto energético.
 */
private const val DISCOVERY_LATENCY_MS       = 30_000L
private const val REINTENTO_REQUISITOS_MS     = 2_000L

/**
 * Implementación de [DetectorProximidad] basada en Bluetooth Low Energy (BLE).
 *
 * Anuncia la presencia del usuario local (id + nombre de perfil) y escanea en tiempo
 * real otros dispositivos del campus sin necesidad de conexión a Internet ni GPS.
 *
 * ## Modo de escaneo adaptativo (Mejora #8)
 * - Primeros [DISCOVERY_LATENCY_MS] ms: `SCAN_MODE_LOW_LATENCY` (máxima velocidad de discovery).
 * - Después: `SCAN_MODE_BALANCED` (mayor latencia, mucho menor consumo de batería).
 * El scanner se reinicia automáticamente con el nuevo modo al finalizar el periodo inicial.
 *
 * ## truncarUtf8 O(1) (Mejora #9)
 * Reemplaza el bucle O(n²) anterior (drop-one-char + re-encode por iteración) con una
 * codificación directa usando `CharsetEncoder.encode()` con límite de bytes, que opera
 * en tiempo lineal sobre los bytes, no sobre los caracteres.
 *
 * [nombreProveedor] debe devolver el mejor nombre disponible en el momento (lo ideal:
 * el perfil ya cargado; si no, el último recordado localmente).
 */
class ProximityManagerBle(
    private val context: Context,
    private val usuarioIdProveedor: () -> String?,
    private val nombreProveedor: () -> String? = { null },
    private val visibilidadProveedor: () -> com.renea.arete_social.domain.model.Visibilidad = { com.renea.arete_social.domain.model.Visibilidad.VISIBLE },
    private val onDispositivoDetectado: ((String, String?) -> Unit)? = null,
    private val resolverNombreProveedor: ((String) -> String?)? = null
) : DetectorProximidad {

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val advertiserScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var advertiserJob: Job? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var ultimoUsuarioAnunciado: String? = null
    @Volatile private var publicidadActiva = false
    @Volatile private var tierActivo = 0

    // §1.2 — true cuando la publicidad con nombre (scan response) o de baja latencia falló
    // y se cayó a un tier de respaldo (BALANCED o LOW_POWER). Se resetea a false cuando
    // una llamada a iniciarPublicidad() tiene éxito con el paquete completo (Tier 1).
    @Volatile private var cayoEnFallback = false

    private val dispositivosDescubiertos = ConcurrentHashMap<String, Dispositivo>()
    private val dispositivosBt = ConcurrentHashMap<String, android.bluetooth.BluetoothDevice>()
    private val estadosLocalesPorUsuario = ConcurrentHashMap<String, Pair<String, Long>>()

    fun obtenerBluetoothDevice(mac: String): android.bluetooth.BluetoothDevice? = dispositivosBt[mac]

    private fun tienePermisosBle(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan      = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)      == PackageManager.PERMISSION_GRANTED
            val advertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            val connect   = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)   == PackageManager.PERMISSION_GRANTED
            return scan && advertise && connect && hasLocation
        }
        return hasLocation
    }

    override fun ubicacionPropia(): Flow<Ubicacion> = emptyFlow()

    @SuppressLint("MissingPermission")
    fun actualizarVisibilidad() {
        if (visibilidadProveedor() == com.renea.arete_social.domain.model.Visibilidad.INVISIBLE) {
            detenerPublicidad()
        } else {
            iniciarPublicidad()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarPublicidad() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !tienePermisosBle()) return

        // H-03: si el modo es INVISIBLE, detener de inmediato cualquier anuncio para
        // evitar fugar el ID de usuario estable y el nombre por radiofrecuencia BLE
        if (visibilidadProveedor() == com.renea.arete_social.domain.model.Visibilidad.INVISIBLE) {
            detenerPublicidad()
            return
        }

        val usuarioId = usuarioIdProveedor() ?: return
        if (advertiser != null && advertiseCallback != null && ultimoUsuarioAnunciado == usuarioId && publicidadActiva && !cayoEnFallback) {
            return
        }

        advertiserJob?.cancel()
        advertiserJob = advertiserScope.launch {
            detenerPublicidadInterno()
            // Pausa de enfriamiento (300 ms) para que el daemon Bluetooth de Android libere el slot en HAL
            delay(300)
            ejecutarTierPublicidad(usuarioId, tier = 1)
        }
    }

    private data class PaquetesPublicidad(
        val settings: AdvertiseSettings,
        val data: AdvertiseData,
        val scanResponse: AdvertiseData?
    )

    private fun construirPaquetesTier(usuarioId: String, tier: Int): PaquetesPublicidad {
        // Empaquetar el ID de usuario en bytes binarios con cabecera de tipo (17 bytes total para UUIDs)
        val dataBytes = if (usuarioId.startsWith("invitado-local-")) {
            val uuidPart = usuarioId.removePrefix("invitado-local-")
            runCatching {
                val uuid = UUID.fromString(uuidPart)
                java.nio.ByteBuffer.allocate(17)
                    .put(0x02.toByte()) // Tipo 0x02: UUID sintético de invitado local
                    .putLong(uuid.mostSignificantBits)
                    .putLong(uuid.leastSignificantBits)
                    .array()
            }.getOrElse {
                val raw = usuarioId.toByteArray(Charsets.UTF_8)
                val len = minOf(raw.size, 16)
                java.nio.ByteBuffer.allocate(1 + len)
                    .put(0x03.toByte())
                    .put(raw, 0, len)
                    .array()
            }
        } else {
            runCatching {
                val uuid = UUID.fromString(usuarioId)
                java.nio.ByteBuffer.allocate(17)
                    .put(0x01.toByte()) // Tipo 0x01: UUID binario estándar (16 bytes)
                    .putLong(uuid.mostSignificantBits)
                    .putLong(uuid.leastSignificantBits)
                    .array()
            }.getOrElse {
                val raw = usuarioId.toByteArray(Charsets.UTF_8)
                val len = minOf(raw.size, 16)
                java.nio.ByteBuffer.allocate(1 + len)
                    .put(0x03.toByte()) // Tipo 0x03: Texto plano UTF-8
                    .put(raw, 0, len)
                    .array()
            }
        }

        // Paquete principal: 28 bytes (Flags: 3B + ServiceUuid: 4B + ServiceData: 21B) <= límite 31B legado.
        val data = AdvertiseData.Builder()
            .addServiceUuid(ANTIRED_PARCEL_UUID)
            .addServiceData(ANTIRED_PARCEL_UUID, dataBytes)
            .setIncludeDeviceName(false)
            .build()

        // Paquete separado (scan response) con el nombre de perfil truncado a bytes UTF-8 completos
        val nombreBytes = nombreProveedor()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { truncarUtf8(it, MAX_BYTES_NOMBRE) }

        val scanResponse = if ((tier == 1 || tier == 2) && nombreBytes != null) {
            AdvertiseData.Builder()
                .addServiceData(ANTIRED_NOMBRE_PARCEL_UUID, nombreBytes)
                .setIncludeDeviceName(false)
                .build()
        } else {
            null
        }

        val settings = when (tier) {
            1 -> AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            2 -> AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            3 -> AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            else -> AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(0)
                .build()
        }

        return PaquetesPublicidad(settings, data, scanResponse)
    }

    @SuppressLint("MissingPermission")
    private fun ejecutarTierPublicidad(usuarioId: String, tier: Int) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !tienePermisosBle()) return
        if (visibilidadProveedor() == com.renea.arete_social.domain.model.Visibilidad.INVISIBLE) return

        val adv = adapter.bluetoothLeAdvertiser ?: return
        advertiser = adv

        val (settings, data, scanResponse) = construirPaquetesTier(usuarioId, tier)

        lateinit var callback: AdvertiseCallback
        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Publicidad BLE iniciada exitosamente (Tier $tier) para $usuarioId")
                publicidadActiva = true
                ultimoUsuarioAnunciado = usuarioId
                tierActivo = tier
                cayoEnFallback = (tier > 1)
            }

            override fun onStartFailure(errorCode: Int) {
                val errorMsg = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "ADVERTISE_FAILED_DATA_TOO_LARGE (1)"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS (2)"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ADVERTISE_FAILED_ALREADY_STARTED (3)"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "ADVERTISE_FAILED_INTERNAL_ERROR (4)"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5)"
                    else -> "código desconocido: $errorCode"
                }
                Log.e(TAG, "Publicidad BLE Tier $tier falló ($errorMsg)")
                publicidadActiva = false
                cayoEnFallback = true

                if (tier < 4) {
                    advertiserJob?.cancel()
                    advertiserJob = advertiserScope.launch {
                        runCatching { adv.stopAdvertising(callback) }
                        delay(350) // Pausa para que MediaTek/Xiaomi libere el set de publicidad
                        ejecutarTierPublicidad(usuarioId, tier + 1)
                    }
                } else {
                    Log.e(TAG, "Todos los tiers de publicidad BLE fallaron para $usuarioId")
                }
            }
        }
        advertiseCallback = callback

        runCatching {
            Log.d(TAG, "Invocando startAdvertising Tier $tier (conScanResponse=${scanResponse != null})...")
            if (scanResponse != null) {
                adv.startAdvertising(settings, data, scanResponse, callback)
            } else {
                adv.startAdvertising(settings, data, callback)
            }
        }.onFailure { e ->
            Log.e(TAG, "Fallo al invocar startAdvertising Tier $tier: ${e.message}", e)
            if (tier < 4) {
                advertiserJob?.cancel()
                advertiserJob = advertiserScope.launch {
                    delay(350)
                    ejecutarTierPublicidad(usuarioId, tier + 1)
                }
            }
        }
    }

    /**
     * Trunca [texto] a lo sumo [maxBytes] en codificación UTF-8 sin partir caracteres
     * multi-byte a la mitad.
     *
     * ## Implementación O(1) (respecto al nº de caracteres del string)
     * Usa `CharsetEncoder.encode(CharBuffer)` con un `ByteBuffer` de capacidad [maxBytes].
     * El encoder rellena el buffer hasta el límite y corta en el límite de código-punto
     * más cercano, devolviendo sólo los bytes válidos. Sin bucles de re-encoding.
     */
    private fun truncarUtf8(texto: String, maxBytes: Int): ByteArray {
        val encoder = Charset.forName("UTF-8").newEncoder()
        val buffer  = java.nio.ByteBuffer.allocate(maxBytes)
        val result  = encoder.encode(java.nio.CharBuffer.wrap(texto), buffer, true)
        // CoderResult puede ser OVERFLOW (truncado) o UNDERFLOW (cupó entero) — ambos válidos aquí
        buffer.flip()
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    @SuppressLint("MissingPermission")
    private fun detenerPublicidad() {
        advertiserJob?.cancel()
        advertiserJob = null
        detenerPublicidadInterno()
    }

    @SuppressLint("MissingPermission")
    private fun detenerPublicidadInterno() {
        runCatching {
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        }
        advertiseCallback = null
        advertiser        = null
        ultimoUsuarioAnunciado = null
        publicidadActiva  = false
        tierActivo        = 0
    }

    @Volatile
    private var emisorDescubrimiento: kotlinx.coroutines.channels.SendChannel<List<Dispositivo>>? = null

    @SuppressLint("MissingPermission")
    override fun descubrir(): Flow<List<Dispositivo>> = callbackFlow {
        emisorDescubrimiento = channel
        // Emisión inicial inmediata para no bloquear a combine() en ProximityManagerHibrido
        trySend(emptyList())

        // Esperar (sin terminar el flujo) a que existan: sesión (id para anunciar), permisos y
        // Bluetooth encendido. Antes, si faltaba algo en el momento de suscribirse el flujo se
        // quedaba inerte para siempre; con un flujo COMPARTIDO (ver ProximityManagerHibrido) el
        // primer suscriptor llega al arrancar la app, antes de que el usuario conceda permisos,
        // así que hay que reintentar hasta que se cumplan.
        var adapter = bluetoothAdapter
        var scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
        var avisado = false
        while (adapter == null || !adapter.isEnabled || scanner == null ||
            !tienePermisosBle() || usuarioIdProveedor() == null
        ) {
            if (!avisado) {
                Log.d(TAG, "BLE en espera: adapter=${adapter != null}, isEnabled=${adapter?.isEnabled}, scanner=${scanner != null}, permisos=${tienePermisosBle()}, usuarioId=${usuarioIdProveedor()}")
                avisado = true
            }
            delay(REINTENTO_REQUISITOS_MS)
            adapter = bluetoothAdapter
            scanner = adapter?.bluetoothLeScanner
        }

        Log.d(TAG, "Requisitos BLE cumplidos para usuario=${usuarioIdProveedor()}. Iniciando publicidad y escáner...")
        iniciarPublicidad()
        val scannerListo = scanner

        // ── Escaneo por filtro de servicio AntiRed (0xFED0) y datos de servicio ──────
        val filtros = listOf(
            ScanFilter.Builder().setServiceUuid(ANTIRED_PARCEL_UUID).build(),
            ScanFilter.Builder().setServiceData(ANTIRED_PARCEL_UUID, null).build(),
            ScanFilter.Builder().setServiceData(ANTIRED_NOMBRE_PARCEL_UUID, null).build()
        )

        val settingsAgresivos = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        // Referencia mutable a los settings activos para que el backoff de onScanFailed
        // siempre use el mismo modo que estaba vigente cuando el scanner murió.
        var settingsActivos: ScanSettings = settingsAgresivos
        var ultimaEmisionScanMs = 0L

        // Contador de reintentos activos: evita acumular corrutinas paralelas si el
        // throttle dispara onScanFailed varias veces antes de que el backoff expire.
        var reintentosActivosScan = 0
        lateinit var scanCallback: ScanCallback

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { procesarResultadoScan(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { procesarResultadoScan(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                // errorCode 1 = SCAN_FAILED_ALREADY_STARTED (inofensivo, ignorar)
                if (errorCode == 1) {
                    Log.d(TAG, "Escaneo BLE: ya estaba iniciado (errorCode=1), ignorando")
                    return
                }
                Log.e(TAG, "Escaneo BLE fallido (código: $errorCode) — programando reintento con backoff")
                if (reintentosActivosScan > 0) return  // ya hay un reintento pendiente
                reintentosActivosScan++
                launch {
                    // Backoff exponencial: 5 s, 10 s, 20 s … máximo 60 s
                    var esperaMs = 5_000L
                    val esperaMaxMs = 60_000L
                    var intentos = 0
                    var filtrosActuales = filtros
                    while (isActive) {
                        delay(esperaMs)
                        val sc = bluetoothAdapter?.bluetoothLeScanner
                        if (sc == null || bluetoothAdapter?.isEnabled != true) {
                            Log.d(TAG, "BLE aún no disponible en reintento de scan, esperando ${esperaMs}ms más")
                        } else {
                            runCatching { sc.stopScan(scanCallback) }
                            // Si falla con código 5 (OUT_OF_HARDWARE_RESOURCES) o tras varios reintentos,
                            // degradar a filtros vacíos para forzar filtrado software en procesarResultadoScan
                            if (errorCode == ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES || intentos >= 2) {
                                filtrosActuales = emptyList()
                            }
                            val r = runCatching { sc.startScan(filtrosActuales, settingsActivos, scanCallback) }
                            if (r.isSuccess) {
                                Log.d(TAG, "Escaneo BLE recuperado tras $intentos reintento(s) (backoff=${esperaMs}ms, filtros=${filtrosActuales.size})")
                                break
                            } else {
                                Log.e(TAG, "Reintento de scan fallido: ${r.exceptionOrNull()?.message}")
                            }
                        }
                        intentos++
                        esperaMs = minOf(esperaMs * 2, esperaMaxMs)
                    }
                    reintentosActivosScan--
                }
            }

            private fun procesarResultadoScan(result: ScanResult) {
                val record = result.scanRecord
                val serviceData = record?.getServiceData(ANTIRED_PARCEL_UUID)
                    ?: record?.serviceData?.entries?.firstOrNull { it.key.uuid == ANTIRED_SERVICE_UUID }?.value
                val nombreData  = record?.getServiceData(ANTIRED_NOMBRE_PARCEL_UUID)
                    ?: record?.serviceData?.entries?.firstOrNull { it.key.uuid == ANTIRED_NOMBRE_SERVICE_UUID }?.value
                val tieneUuid   = record?.serviceUuids?.any { it.uuid == ANTIRED_SERVICE_UUID } == true ||
                                  record?.serviceData?.keys?.any { it.uuid == ANTIRED_SERVICE_UUID } == true

                // Ignorar dispositivos que no contengan firmas de AntiRed Social
                if (serviceData == null && nombreData == null && !tieneUuid) {
                    return
                }

                val usuarioId = serviceData?.let { bytes ->
                    when {
                        bytes.size == 17 && bytes[0] == 0x01.toByte() -> {
                            val buffer = java.nio.ByteBuffer.wrap(bytes, 1, 16)
                            UUID(buffer.long, buffer.long).toString()
                        }
                        bytes.size == 17 && bytes[0] == 0x02.toByte() -> {
                            val buffer = java.nio.ByteBuffer.wrap(bytes, 1, 16)
                            "invitado-local-${UUID(buffer.long, buffer.long)}"
                        }
                        bytes.size in 2..21 && bytes[0] == 0x03.toByte() -> {
                            val str = String(bytes, 1, bytes.size - 1, Charsets.UTF_8).trim()
                            if (str.length >= 3 && str.all { it.isLetterOrDigit() || it in "-_." }) str else null
                        }
                        bytes.size == 16 -> {
                            val str = String(bytes, Charsets.UTF_8).trim()
                            if (str.startsWith("invita") && str.length >= 8) {
                                str
                            } else {
                                runCatching {
                                    val buffer = java.nio.ByteBuffer.wrap(bytes)
                                    UUID(buffer.long, buffer.long).toString()
                                }.getOrNull()
                            }
                        }
                        else -> null
                    }
                }
                val miUsuarioId = usuarioIdProveedor()

                // Filtrar el propio dispositivo para no detectarse a sí mismo
                if (usuarioId != null && miUsuarioId != null) {
                    val u1 = usuarioId.replace("-", "").trim().lowercase()
                    val u2 = miUsuarioId.replace("-", "").trim().lowercase()
                    if (u1 == u2) {
                        return
                    }
                }

                val deviceAddress      = result.device?.address ?: return
                val rssi               = result.rssi
                val distanciaEstimada  = calcularDistanciaRssi(rssi)

                // Fusión por MAC: si el paquete principal (0xFED0) y el scan response (0xFED1)
                // llegan por separado (frecuente en Android/MediaTek), se combinan de forma atómica.
                val existente = dispositivosDescubiertos[deviceAddress]
                    ?: dispositivosDescubiertos.values.firstOrNull { it.macAddress == deviceAddress }

                val usuarioIdFinal = usuarioId ?: existente?.usuarioId
                val idFinal        = usuarioIdFinal ?: deviceAddress

                val nombreAnunciado = nombreData
                    ?.let { String(it, Charsets.UTF_8).trim() }
                    ?.takeIf { it.isNotEmpty() }

                val nombreFinal = nombreAnunciado
                    ?: (if (existente?.nombre != null && existente.nombre != "Cargando…" && existente.nombre != "Alguien") existente.nombre else null)
                    ?: resolverNombreProveedor?.invoke(idFinal)?.takeIf { it.isNotBlank() && it != "Alguien" }
                    ?: result.scanRecord?.deviceName
                    ?: "Cargando…"

                onDispositivoDetectado?.invoke(idFinal, nombreAnunciado)

                val ubicacionPrevia = existente?.ubicacion
                    ?: (if (usuarioIdFinal != null) dispositivosDescubiertos.values.firstOrNull { it.usuarioId == usuarioIdFinal }?.ubicacion else null)

                val ahoraMs = System.currentTimeMillis()
                // ⚠️ Bug fix: se eliminó el fallback a dispositivosDescubiertos porque restauraba
                // el estado anterior del peer incluso después de que fue limpiado en actualizarEstadoDispositivo(),
                // haciendo que la segunda actualización de estado (p.ej. borrado) no se propagara.
                val estadoPrevioPar = usuarioIdFinal?.let { estadosLocalesPorUsuario[it] }
                    ?: estadosLocalesPorUsuario[deviceAddress]
                val estadoVigente = estadoPrevioPar?.first?.takeIf { estadoPrevioPar.second > ahoraMs }

                val dispositivo = Dispositivo(
                    id              = idFinal,
                    nombre          = nombreFinal,
                    señal           = rssi,
                    ultimaConexion  = ahoraMs,
                    usuarioId       = usuarioIdFinal,
                    distanciaMetros = (distanciaEstimada * 10.0).toInt() / 10.0, // 1 decimal
                    ubicacion       = ubicacionPrevia,
                    macAddress      = deviceAddress,   // Dirección MAC necesaria para conexiones RFCOMM/GATT
                    estadoMichi     = estadoVigente,
                    estadoMichiHasta = estadoPrevioPar?.second
                )

                // Guardar siempre indexado por MAC física para evitar duplicados entre paquetes principales y scan response
                val esNuevo = !dispositivosDescubiertos.containsKey(deviceAddress)
                dispositivosDescubiertos[deviceAddress] = dispositivo
                val huboExpirados = limpiarDispositivosExpirados()
                val ahoraMsEmision = System.currentTimeMillis()
                if (esNuevo || huboExpirados || ahoraMsEmision - ultimaEmisionScanMs >= 400L) {
                    ultimaEmisionScanMs = ahoraMsEmision
                    trySend(dispositivosDescubiertos.values.toList())
                }
            }
        }

        runCatching {
            scannerListo.startScan(filtros, settingsAgresivos, scanCallback)
            Log.d(TAG, "Escaneo BLE iniciado en modo LOW_LATENCY con filtro 0xFED0")
        }.onFailure {
            Log.e(TAG, "Error al iniciar escaneo BLE: ${it.message}")
        }

        // Fase 2: tras DISCOVERY_LATENCY_MS, bajar a BALANCED para ahorrar batería
        launch {
            delay(DISCOVERY_LATENCY_MS)
            val currentScanner = bluetoothAdapter?.bluetoothLeScanner ?: scannerListo
            runCatching { currentScanner.stopScan(scanCallback) }
            val settingsBalanceados = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0L)
                .build()
            runCatching {
                currentScanner.startScan(filtros, settingsBalanceados, scanCallback)
                settingsActivos = settingsBalanceados   // actualizar referencia activa
                Log.d(TAG, "Escaneo BLE cambiado a modo BALANCED (filtro 0xFED0)")
            }.onFailure {
                Log.e(TAG, "No se pudo reiniciar en modo BALANCED: ${it.message}")
            }
        }

        // Reanudación automática si el usuario enciende o apaga el Bluetooth en caliente
        val receptorBt = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "Bluetooth reactivado por el sistema: reiniciando publicidad y escáner")
                    iniciarPublicidad()
                    val nuevoScanner = bluetoothAdapter?.bluetoothLeScanner
                    if (nuevoScanner != null) {
                        runCatching { nuevoScanner.stopScan(scanCallback) }
                        runCatching { nuevoScanner.startScan(filtros, settingsActivos, scanCallback) }
                    }
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    Log.d(TAG, "Bluetooth desactivado por el sistema: limpiando dispositivos")
                    detenerPublicidad()
                    dispositivosDescubiertos.clear()
                    trySend(emptyList())
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receptorBt,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Limpieza periódica continua de dispositivos BLE que salieron del alcance (sin requerir nuevos paquetes)
        launch {
            val INTERVALO_EXPIRACION_MS = 3_000L
            while (isActive) {
                delay(INTERVALO_EXPIRACION_MS)
                if (limpiarDispositivosExpirados()) {
                    trySend(dispositivosDescubiertos.values.toList())
                }
            }
        }

        // §1.2 y H-03 — Monitoreo periódico de visibilidad y reintento de publicidad con nombre.
        launch {
            val INTERVALO_CHECK_MS = 5_000L
            var contadorFallback = 0
            var ultimaVisibilidad = visibilidadProveedor()
            while (isActive) {
                delay(INTERVALO_CHECK_MS)
                val visActual = visibilidadProveedor()
                if (visActual != ultimaVisibilidad) {
                    ultimaVisibilidad = visActual
                    Log.d(TAG, "Cambio de visibilidad detectado en BLE: $visActual")
                    if (visActual == com.renea.arete_social.domain.model.Visibilidad.INVISIBLE) {
                        detenerPublicidad()
                    } else {
                        iniciarPublicidad()
                    }
                }
                contadorFallback += 5
                if (contadorFallback >= 60) {
                    contadorFallback = 0
                    if ((cayoEnFallback || !publicidadActiva) && visActual != com.renea.arete_social.domain.model.Visibilidad.INVISIBLE) {
                        Log.d(TAG, "Reintentando publicidad BLE óptima (cayoEnFallback=$cayoEnFallback, activa=$publicidadActiva)...")
                        iniciarPublicidad()
                    }
                }
            }
        }

        awaitClose {
            if (emisorDescubrimiento === channel) emisorDescubrimiento = null
            runCatching { context.unregisterReceiver(receptorBt) }
            runCatching { (bluetoothAdapter?.bluetoothLeScanner ?: scannerListo).stopScan(scanCallback) }
            detenerPublicidad()
            dispositivosDescubiertos.clear()
            Log.d(TAG, "Escaneo BLE detenido")
        }
    }

    fun actualizarUbicacionDispositivo(idOMac: String, ubicacion: Ubicacion) {
        val ahora = System.currentTimeMillis()
        var modificado = false
        dispositivosDescubiertos.forEach { (key, disp) ->
            if (disp.id == idOMac || disp.usuarioId == idOMac || disp.macAddress == idOMac) {
                dispositivosDescubiertos[key] = disp.copy(
                    ubicacion = ubicacion,
                    ultimaConexion = ahora
                )
                modificado = true
            }
        }
        if (modificado) {
            limpiarDispositivosExpirados()
            emisorDescubrimiento?.trySend(dispositivosDescubiertos.values.toList())
        }
    }

    fun actualizarEstadoDispositivo(idOMac: String, estado: String?, vence: Long?) {
        val ahora = System.currentTimeMillis()
        var modificado = false
        val targetNorm = idOMac.replace("-", "").trim().lowercase()
        val sinPrefijo = if (idOMac.startsWith("invitado-local-")) idOMac.removePrefix("invitado-local-") else null
        val conPrefijo = if (!idOMac.startsWith("invitado-local-")) "invitado-local-$idOMac" else null

        dispositivosDescubiertos.forEach { (key, disp) ->
            val coincide = disp.id.equals(idOMac, ignoreCase = true) ||
                disp.usuarioId?.equals(idOMac, ignoreCase = true) == true ||
                disp.macAddress?.equals(idOMac, ignoreCase = true) == true ||
                (sinPrefijo != null && (disp.id.equals(sinPrefijo, ignoreCase = true) || disp.usuarioId?.equals(sinPrefijo, ignoreCase = true) == true)) ||
                (conPrefijo != null && (disp.id.equals(conPrefijo, ignoreCase = true) || disp.usuarioId?.equals(conPrefijo, ignoreCase = true) == true)) ||
                (targetNorm.isNotEmpty() && (
                    disp.id.replace("-", "").trim().lowercase() == targetNorm ||
                    disp.usuarioId?.replace("-", "")?.trim()?.lowercase() == targetNorm ||
                    disp.macAddress?.replace("-", "")?.trim()?.lowercase() == targetNorm
                ))
            if (coincide) {
                dispositivosDescubiertos[key] = disp.copy(
                    estadoMichi = estado,
                    estadoMichiHasta = vence,
                    ultimaConexion = ahora
                )
                modificado = true
                // Vincular también la MAC del dispositivo en la caché de estados
                disp.macAddress?.let { mac ->
                    if (estado != null && vence != null && vence > ahora) {
                        estadosLocalesPorUsuario[mac] = estado to vence
                    } else {
                        estadosLocalesPorUsuario.remove(mac)
                    }
                }
            }
        }
        if (idOMac.isNotBlank()) {
            if (estado != null && vence != null && vence > ahora) {
                estadosLocalesPorUsuario[idOMac] = estado to vence
                if (targetNorm.isNotEmpty()) estadosLocalesPorUsuario[targetNorm] = estado to vence
                if (sinPrefijo != null) estadosLocalesPorUsuario[sinPrefijo] = estado to vence
                if (conPrefijo != null) estadosLocalesPorUsuario[conPrefijo] = estado to vence
            } else {
                estadosLocalesPorUsuario.remove(idOMac)
                if (targetNorm.isNotEmpty()) estadosLocalesPorUsuario.remove(targetNorm)
                if (sinPrefijo != null) estadosLocalesPorUsuario.remove(sinPrefijo)
                if (conPrefijo != null) estadosLocalesPorUsuario.remove(conPrefijo)
            }
        }
        if (modificado || dispositivosDescubiertos.isNotEmpty()) {
            limpiarDispositivosExpirados()
            emisorDescubrimiento?.trySend(dispositivosDescubiertos.values.toList())
        }
    }

    private fun limpiarDispositivosExpirados(): Boolean {
        val ahora = System.currentTimeMillis()
        val tamInicial = dispositivosDescubiertos.size
        dispositivosDescubiertos.entries.removeIf { ahora - it.value.ultimaConexion > VENTANA_EXPIRACION_BLE_MS }
        return dispositivosDescubiertos.size != tamInicial
    }

    /**
     * Modelo de pérdidas en el trayecto logarítmico (Log-Distance Path Loss Model).
     */
    private fun calcularDistanciaRssi(rssi: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = (TX_POWER_DEFAULT - rssi) / (10.0 * EXPONENTE_PROPAGACION)
        return 10.0.pow(ratio)
    }

    override fun detener() {
        detenerPublicidad()
        dispositivosDescubiertos.clear()
        emisorDescubrimiento?.trySend(emptyList())
        Log.d(TAG, "ProximityManagerBle detenido")
    }
}

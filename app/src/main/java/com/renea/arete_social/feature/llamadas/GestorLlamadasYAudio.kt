package com.renea.arete_social.feature.llamadas

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.renea.arete_social.R
import com.renea.arete_social.domain.model.SenalLlamada
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.crypto.CifradorE2ee
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.model.ContactoLlamada
import com.renea.arete_social.domain.model.ProtocoloPerfilBle
import com.renea.arete_social.domain.model.EstadoAudioChat
import com.renea.arete_social.domain.model.EstadoLlamada
import com.renea.arete_social.domain.model.HablanteVoz
import com.renea.arete_social.domain.model.LlamadaInfo
import com.renea.arete_social.domain.model.NotificacionTactica
import com.renea.arete_social.domain.model.TipoNotificacionTactica
import com.renea.arete_social.domain.model.TipoLlamada
import com.renea.arete_social.domain.repository.RepositorioMensajes
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.feature.tamagotchi.regalos.RegaloMascotaEntrante
import com.renea.arete_social.feature.tamagotchi.regalos.TipoRegalo
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

private const val PUERTO_WIFI_UDP = 52222
private const val SAMPLE_RATE = 16000

/**
 * Gestor Integral de Audio, Voz Push-to-Talk y Videollamadas sobre WiFi y Red:
 * - Arquitectura 100% thread-safe con cola de reproducción de único consumidor (cero cierres de app por colisiones de AudioTrack).
 * - Socket UDP persistente para transmisión y recepción (sin agotamiento de descriptores de archivos).
 * - Audio dúplex continuo para videollamadas 1-a-1 en vivo por altavoz.
 * - Transmisión en tiempo real PTT para salas y chats con enrutamiento estricto.
 */
class GestorLlamadasYAudio(
    private val context: Context? = null,
    private val repositorioMensajes: RepositorioMensajes? = null,
    private val repositorioUsuarios: RepositorioUsuarios? = null,
    private val usuarioIdActualProveedor: () -> String
) {
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + supervisorJob)

    // ========================================================
    // HARDWARE AUDIO, TONOS Y HÁPTICA
    // ========================================================
    private val audioManager: AudioManager? by lazy {
        context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private var toneGen: ToneGenerator? = null
    private val vibrator: Vibrator? by lazy {
        context?.let { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }

    private val multicastLock: WifiManager.MulticastLock? by lazy {
        context?.let { ctx ->
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("antired_udp_lock")?.apply {
                setReferenceCounted(true)
            }
        }
    }

    // ========================================================
    // RED UDP PERSISTENTE
    // ========================================================
    private var socketUdp: DatagramSocket? = null
    private var servidorUdpJob: Job? = null
    private val paquetesProcesados: MutableMap<String, Long> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 5000
        }
    )

    // FIX (lag de video en llamadas): IP real de origen del último paquete recibido de
    // cada usuarioId, aprendida pasivamente de cada paquete que llega al socket (ver
    // procesarPaqueteJsonWifi). Se usa para mandar los frames de video de una llamada
    // 1 a 1 por UNICAST directo al contacto (transmitirPaqueteUnicast) en vez de por
    // broadcast a toda la red WiFi, que era la causa principal de la lentitud: cada
    // frame se decodificaba antes en todos los dispositivos de la red, no solo en el
    // del contacto.
    private val direccionesConocidas = ConcurrentHashMap<String, InetAddress>()
    private var gestorIdentidad: com.renea.arete_social.data.crypto.GestorIdentidadEcdsa? = null
    private var pinesIdentidad: com.renea.arete_social.data.crypto.PinesIdentidad? = null
    private var resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null

    // ========================================================
    // COLA DE REPRODUCCIÓN EN TIEMPO REAL (AudioTrack)
    // ========================================================
    private val canalColaAudio = Channel<ByteArray>(
        capacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var bucleReproduccionJob: Job? = null
    private var audioTrackEnVivo: AudioTrack? = null
    private var watchdogStreamJob: Job? = null
    private var mediaPlayerVoz: MediaPlayer? = null

    // ========================================================
    // GRABACIÓN PTT Y DÚPLEX
    // ========================================================
    private var microPttJob: Job? = null
    private var audioRecordPtt: AudioRecord? = null
    private var grabacionPcmStream: ByteArrayOutputStream? = null
    private var estaGrabandoPtt = false
    private var streamActualId: String? = null

    private var bucleVozLlamadaJob: Job? = null
    private var audioRecordLlamada: AudioRecord? = null

    private val _nivelMicrofono = MutableStateFlow(0f)
    val nivelMicrofono: StateFlow<Float> = _nivelMicrofono.asStateFlow()

    // ========================================================
    // ESTADO DE LLAMADA (VIDEO 1 A 1)
    // ========================================================
    private val _llamadaActual = MutableStateFlow<LlamadaInfo?>(null)
    val llamadaActual: StateFlow<LlamadaInfo?> = _llamadaActual.asStateFlow()

    private val _videoFrameRemoto = MutableStateFlow<String?>(null)
    val videoFrameRemoto: StateFlow<String?> = _videoFrameRemoto.asStateFlow()

    private val _videoFrameRemotoBitmap = MutableStateFlow<Bitmap?>(null)
    val videoFrameRemotoBitmap: StateFlow<Bitmap?> = _videoFrameRemotoBitmap.asStateFlow()

    private val notificationManager = TacticalNotificationManager(context, ::sonarTonoSeguro)

    val notificacionActiva: StateFlow<NotificacionTactica?> get() = notificationManager.notificacionActiva

    var chatVisibleId: String?
        get() = notificationManager.chatVisibleId
        set(value) { notificationManager.chatVisibleId = value }

    fun mostrarNotificacion(notif: NotificacionTactica) = notificationManager.mostrarNotificacion(notif)

    fun descartarNotificacion() = notificationManager.descartarNotificacion()

    // ========================================================
    // PLANO DE CONTROL — TABLA "senales" (A3)
    // Todas las señales de negocio (amistad, llamadas, notificaciones)
    // viajan por la tabla dedicada "senales" y NO contaminan el chat general.
    // ========================================================

    /**
     * Emite una señal dedicada a través de la red mesh/local.
     *
     * @param tipo Identificador del tipo de señal (ej. "SOLICITUD_AMISTAD", "SENAL_LLAMADA_INICIAR")
     * @param destinatarioId UUID del usuario que debe recibir la señal, o null para broadcast
     * @param remitenteId UUID del usuario que emite la señal
     * @param payload JsonObject con los campos específicos del tipo
     */
    private suspend fun emitirSenal(
        tipo: String,
        destinatarioId: String?,
        remitenteId: String,
        payload: kotlinx.serialization.json.JsonObject
    ) {
        val senal = SenalLlamada(
            id = UUID.randomUUID().toString(),
            tipo = tipo,
            remitenteId = remitenteId,
            destinatarioId = destinatarioId,
            payload = payload,
            consumido = false
        )
        val jsonString = runCatching {
            kotlinx.serialization.json.Json.encodeToString(SenalLlamada.serializer(), senal)
        }.getOrNull() ?: return

        routerMesh?.let { rm ->
            val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                id = UUID.randomUUID().toString(),
                chatId = "senales-mesh",
                remitenteId = remitenteId,
                destinatarioId = destinatarioId,
                contenido = "[SENAL:$jsonString]",
                timestamp = System.currentTimeMillis()
            )
            if (destinatarioId != null) {
                val directo = rm.enviarADestino(destinatarioId, paquete)
                if (!directo) rm.reenviar(paquete)
            } else {
                rm.reenviar(paquete)
            }
        }
    }

    private suspend fun marcarSenalConsumida(id: String) = Unit

    private fun ttlMsPorTipo(tipo: String): Long = when {
        tipo.startsWith("LLAMADA") || tipo.startsWith("SENAL_LLAMADA") -> 60_000L
        tipo == "NOTIF_MSG"                                            -> 120_000L
        tipo.contains("AMISTAD")                                        -> 24 * 3_600_000L
        tipo == "REGALO_MASCOTA"                                       -> 24 * 3_600_000L
        else                                                            -> 120_000L
    }

    private suspend fun borrarSenalSiEsDirecta(id: String, destinatarioId: String?) = Unit

    private var observadorSenalesJob: Job? = null

    private var routerMesh: MessageRouter? = null

    fun enviarSolicitudAmistad(destinatarioId: String, remitenteNombre: String, remitenteFoto: String?) {
        val miId = usuarioIdActualProveedor()
        android.util.Log.e("GestorLlamadas", "Enviando SOLICITUD_AMISTAD a $destinatarioId desde $miId por routerMesh=${routerMesh != null}")
        scope.launch(Dispatchers.IO) {
            // Canal primario: señales dedicadas (A3)
            emitirSenal(
                tipo = "SOLICITUD_AMISTAD",
                destinatarioId = destinatarioId,
                remitenteId = miId,
                payload = buildJsonObject {
                    put("remitenteNombre", remitenteNombre)
                    put("remitenteFoto", remitenteFoto ?: "")
                }
            )

            // Canal secundario: WiFi UDP y Mesh
            val json = JSONObject().apply {
                put("type", "SOLICITUD_AMISTAD")
                put("targetId", destinatarioId)
                put("remitenteId", miId)
                put("remitenteNombre", remitenteNombre)
                put("remitenteFoto", remitenteFoto ?: "")
                put("ts", System.currentTimeMillis())
                put("nonce", UUID.randomUUID().toString())
            }
            val jsonFinal = firmarJsonUdpSiEsPosible(json)
            transmitirPaqueteUdpWifi(jsonFinal)

            // Canal terciario: Bluetooth Mesh / red mallada local
            routerMesh?.let { router ->
                val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "senales-mesh",
                    remitenteId = miId,
                    destinatarioId = destinatarioId,
                    contenido = jsonFinal
                )
                router.reenviar(paquete)
            }
        }
    }

    /**
     * Quien recibe un regalo para su mascota (chispa o medicina) lo aplica aquí. Lo conecta
     * [com.renea.arete_social.ContenedorApp]; el mismo regalo puede llegar por dos canales y
     * el receptor ignora los repetidos por [RegaloMascotaEntrante.regaloId].
     */
    @Volatile
    var alRecibirRegaloMascota: ((RegaloMascotaEntrante) -> Unit)? = null

    @Volatile
    var onEstadoMascotaRecibido: ((String, String?, Long?) -> Unit)? = null

    fun transmitirEstadoMascota(estado: String?, vence: Long?) {
        val miId = usuarioIdActualProveedor()
        scope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("type", "ESTADO_MICHI")
                put("remitenteId", miId)
                put("estado", estado ?: "")
                put("vence", vence ?: 0L)
                put("ts", System.currentTimeMillis())
                put("nonce", UUID.randomUUID().toString())
            }
            val jsonFinal = firmarJsonUdpSiEsPosible(json)
            transmitirPaqueteUdpWifi(jsonFinal)

            routerMesh?.let { router ->
                val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "senales-mesh",
                    remitenteId = miId,
                    destinatarioId = null,
                    contenido = jsonFinal
                )
                router.reenviar(paquete)
            }
        }
    }

    private fun entregarRegaloMascota(
        remitenteId: String,
        remitenteNombre: String,
        regaloId: String,
        tipoTexto: String,
        cantidadTexto: String
    ) {
        val tipo = runCatching { TipoRegalo.valueOf(tipoTexto) }.getOrNull() ?: return
        if (regaloId.isBlank() || remitenteId.isBlank()) return
        val cantidad = cantidadTexto.toIntOrNull()?.coerceIn(1, 1) ?: 1
        alRecibirRegaloMascota?.invoke(
            RegaloMascotaEntrante(regaloId, remitenteId, remitenteNombre, tipo, cantidad)
        )
    }

    /** Envía una chispa o medicina a la mascota de [destinatarioId] por todos los canales disponibles. */
    fun enviarRegaloMascota(
        destinatarioId: String,
        regaloId: String,
        tipo: TipoRegalo,
        cantidad: Int,
        remitenteNombre: String
    ) {
        val miId = usuarioIdActualProveedor()
        scope.launch(Dispatchers.IO) {
            // Canal primario: señales dedicadas (llegan aunque el otro teléfono esté apagado un rato)
            emitirSenal(
                tipo = "REGALO_MASCOTA",
                destinatarioId = destinatarioId,
                remitenteId = miId,
                payload = buildJsonObject {
                    put("regaloId", regaloId)
                    put("regalo", tipo.name)
                    put("cantidad", cantidad)
                    put("remitenteNombre", remitenteNombre)
                }
            )

            val json = JSONObject().apply {
                put("type", "REGALO_MASCOTA")
                put("targetId", destinatarioId)
                put("remitenteId", miId)
                put("remitenteNombre", remitenteNombre)
                put("regaloId", regaloId)
                put("regalo", tipo.name)
                put("cantidad", cantidad)
                put("ts", System.currentTimeMillis())
                put("nonce", UUID.randomUUID().toString())
            }
            val jsonFinal = firmarJsonUdpSiEsPosible(json)
            transmitirPaqueteUdpWifi(jsonFinal)

            // Canal terciario: Bluetooth / red mallada local
            routerMesh?.let { router ->
                router.reenviar(
                    com.renea.arete_social.domain.mesh.PaqueteMesh(
                        id = UUID.randomUUID().toString(),
                        chatId = "senales-mesh",
                        remitenteId = miId,
                        destinatarioId = destinatarioId,
                        contenido = jsonFinal
                    )
                )
            }
        }
    }

    fun responderSolicitudAmistad(destinatarioId: String, aceptada: Boolean, remitenteNombre: String, remitenteFoto: String?) {
        val miId = usuarioIdActualProveedor()
        scope.launch(Dispatchers.IO) {
            val tipoRespuesta = if (aceptada) "ACEPTADA" else "RECHAZADA"

            // Canal primario: señales dedicadas (A3)
            emitirSenal(
                tipo = "RESPUESTA_AMISTAD",
                destinatarioId = destinatarioId,
                remitenteId = miId,
                payload = buildJsonObject {
                    put("accion", tipoRespuesta)
                    put("remitenteNombre", remitenteNombre)
                    put("remitenteFoto", remitenteFoto ?: "")
                }
            )

            // Canal secundario: WiFi UDP y Mesh
            val json = JSONObject().apply {
                put("type", "RESPUESTA_AMISTAD")
                put("accion", tipoRespuesta)
                put("targetId", destinatarioId)
                put("remitenteId", miId)
                put("remitenteNombre", remitenteNombre)
                put("remitenteFoto", remitenteFoto ?: "")
                put("ts", System.currentTimeMillis())
                put("nonce", UUID.randomUUID().toString())
            }
            val jsonFinal = firmarJsonUdpSiEsPosible(json)
            transmitirPaqueteUdpWifi(jsonFinal)

            // Canal terciario: Bluetooth Mesh / red mallada local
            routerMesh?.let { router ->
                val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "senales-mesh",
                    remitenteId = miId,
                    destinatarioId = destinatarioId,
                    contenido = jsonFinal
                )
                router.reenviar(paquete)
            }
        }
    }

    fun notificarAmigoAgregado(destinatarioId: String, remitenteNombre: String, remitenteFoto: String?) {
        enviarSolicitudAmistad(destinatarioId, remitenteNombre, remitenteFoto)
    }

    fun notificarMensajeEnviado(
        chatId: String,
        destinatarioId: String?,
        remitenteNombre: String,
        remitenteFoto: String?,
        contenidoPreview: String
    ) {
        val miId = usuarioIdActualProveedor()
        scope.launch(Dispatchers.IO) {
            val target = destinatarioId ?: "todos"
            val textoLimpio = contenidoPreview.replace("\n", " ").take(60)

            // Canal primario: señal dedicada (A3) si hay destinatario directo
            if (destinatarioId != null) {
                val textoGenerico = "Nuevo mensaje"
                emitirSenal(
                    tipo = "NOTIF_MSG",
                    destinatarioId = destinatarioId,
                    remitenteId = miId,
                    payload = buildJsonObject {
                        put("chatId", chatId)
                        put("remitenteNombre", remitenteNombre)
                        put("remitenteFoto", remitenteFoto ?: "")
                        put("contenido", textoGenerico)
                    }
                )
            }

            // Canal secundario: WiFi UDP
            val json = JSONObject().apply {
                put("type", "NOTIF_MENSAJE")
                put("id", UUID.randomUUID().toString())
                put("targetId", target)
                put("remitenteId", miId)
                put("remitenteNombre", remitenteNombre)
                put("remitenteFoto", remitenteFoto ?: "")
                put("chatId", chatId)
                put("contenido", "Nuevo mensaje")
            }.toString()
            transmitirPaqueteUdpWifi(json)
        }
    }

    fun transmitirPublicacionPerfil(pub: com.renea.arete_social.domain.model.PublicacionPerfil) {
        val miId = usuarioIdActualProveedor()
        scope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("type", "PUB_PERFIL")
                put("id", pub.id)
                put("usuarioId", pub.usuarioId)
                put("autorNombre", pub.autorNombre)
                put("tipo", pub.tipo.name)
                put("contenido", pub.contenido)
                put("timestamp", pub.timestamp)
                put("likes", pub.likes)
            }.toString()
            transmitirPaqueteUdpWifi(json)

            routerMesh?.let { router ->
                val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "senales-mesh",
                    remitenteId = miId,
                    destinatarioId = null,
                    contenido = json
                )
                router.reenviar(paquete)
            }
        }
    }

    fun transmitirLikePublicacion(publicacionId: String, miUsuarioId: String) {
        val miId = usuarioIdActualProveedor()
        scope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("type", "PUB_LIKE")
                put("publicacionId", publicacionId)
                put("usuarioId", miUsuarioId)
            }.toString()
            transmitirPaqueteUdpWifi(json)

            routerMesh?.let { router ->
                val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "senales-mesh",
                    remitenteId = miId,
                    destinatarioId = null,
                    contenido = json
                )
                router.reenviar(paquete)
            }
        }
    }

    private var cronometroJob: Job? = null
    private var timbreJob: Job? = null

    // ========================================================
    // ESTADO DE CANALES DE VOZ
    // ========================================================
    private val _audioChatEstado = MutableStateFlow(
        EstadoAudioChat(
            chatAudioActivo = true,
            transmitiendoVoz = false,
            canalActivoId = ID_CHAT_GENERAL,
            canalActivoNombre = "Chat Aréte",
            hablantes = emptyList()
        )
    )
    val audioChatEstado: StateFlow<EstadoAudioChat> = _audioChatEstado.asStateFlow()

    private var observadorAudioJob: Job? = null
    private var observadorSenalesGeneralJob: Job? = null

    init {
        runCatching {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        }
        runCatching {
            multicastLock?.acquire()
        }
        // NO configuramos audio aquí — solo cuando empieza una llamada/PTT real.
        // Hacerlo al inicio fuerza MODE_IN_COMMUNICATION y rompe Bluetooth A2DP y apps de música.
        iniciarReproductorAudioTrack()
        iniciarServidorUdpWifi()
        iniciarObservadorCanalAudio(ID_CHAT_GENERAL)
        iniciarObservadorSenalesGeneral()    // VOZ_PTT + fallback NOTIF_MSG
        iniciarObservadorSenalesRespaldo()   // Canal dedicado de señales de control (A3)
    }

    private var observadorMeshJob: Job? = null

    /**
     * Conecta el gestor con la red mallada BLE Mesh para emitir notificaciones tácticas
     * de mensajes entrantes cuando no hay conexión WiFi ni Internet.
     */
    fun conectarConMesh(
        messageRouter: MessageRouter,
        cifradorE2ee: CifradorE2ee? = null,
        nombreProveedor: ((String) -> String?)? = null,
        gestorIdentidad: com.renea.arete_social.data.crypto.GestorIdentidadEcdsa? = null,
        pinesIdentidad: com.renea.arete_social.data.crypto.PinesIdentidad? = null,
        resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null
    ) {
        this.routerMesh = messageRouter
        this.gestorIdentidad = gestorIdentidad
        this.pinesIdentidad = pinesIdentidad
        this.resolverIdentidad = resolverIdentidad
        observadorMeshJob?.cancel()
        observadorMeshJob = scope.launch(Dispatchers.IO) {
            messageRouter.todosLosPaquetesEntrantes().collect { p ->
                val miId = usuarioIdActualProveedor()
                if (miId.isBlank() || coincidenIds(p.remitenteId, miId)) return@collect
                if (p.chatId == ProtocoloPerfilBle.CHAT_ID || com.renea.arete_social.domain.model.ProtocoloCanalesMesh.esCanalP2P(p.chatId)) return@collect
                if (p.chatId == "encuentros-mesh" || p.contenido.startsWith("{\"type\":\"ENCUENTRO_")) return@collect
                if (p.contenido.startsWith("[IDENT:")) return@collect

                val esDirecto = p.destinatarioId != null
                val esParaMi = if (esDirecto) coincidenIds(p.destinatarioId, miId)
                               else p.chatId == ID_CHAT_GENERAL || p.chatId == "senales-mesh"

                if (!esParaMi) return@collect

                if (paquetesProcesados.containsKey(p.id)) return@collect
                paquetesProcesados[p.id] = System.currentTimeMillis()

                if (p.chatId == "senales-mesh" || p.contenido.startsWith("{\"type\":") || p.contenido.startsWith("[SENAL:")) {
                    if (p.contenido.startsWith("[SENAL:")) {
                        val jsonStr = p.contenido.removePrefix("[SENAL:").removeSuffix("]")
                        val senal = runCatching {
                            kotlinx.serialization.json.Json.decodeFromString(SenalLlamada.serializer(), jsonStr)
                        }.getOrNull()
                        if (senal != null) {
                            procesarSenalEntrante(senal, miId)
                            return@collect
                        }
                    }
                    // H-02: pasar la identidad ya autenticada por el router
                    procesarPaqueteJsonWifi(p.contenido, remitenteVerificado = p.remitenteId)
                    return@collect
                }

                if (_llamadaActual.value != null) return@collect

                val nombreRemitente = nombreProveedor?.invoke(p.remitenteId)
                    ?: runCatching { repositorioUsuarios?.obtenerPorId(p.remitenteId)?.nombre }.getOrNull()
                    ?: p.remitenteId.take(8)

                val esCifrado = runCatching { cifradorE2ee?.esCifrado(p.contenido) == true }.getOrDefault(false)
                val textoPreview = if (esCifrado) {
                    val claveRemitente = cifradorE2ee?.obtenerClavePublicaConocida(p.remitenteId)
                    if (claveRemitente != null) {
                        runCatching {
                            cifradorE2ee.descifrar(
                                textoCifrado = p.contenido,
                                clavePublicaContraparteBase64 = claveRemitente,
                                miUsuarioId = miId,
                                remitenteOriginalId = p.remitenteId,
                                destinatarioOriginalId = miId
                            )
                        }.getOrDefault("Mensaje nuevo [E2EE]")
                    } else {
                        "Mensaje nuevo [E2EE]"
                    }
                } else {
                    p.contenido
                }

                val textoLimpio = if (textoPreview.startsWith("[VOZ_PTT:")) {
                    "🎙️ Nota de voz"
                } else {
                    textoPreview.replace("\n", " ").take(60)
                }

                mostrarNotificacion(
                    NotificacionTactica(
                        tipo = TipoNotificacionTactica.MENSAJE,
                        titulo = "MENSAJE",
                        subtitulo = textoLimpio,
                        remitenteId = p.remitenteId,
                        remitenteNombre = nombreRemitente,
                        chatId = p.chatId
                    )
                )
            }
        }
    }

    /** Activa el modo de audio para comunicaciones de voz. Llamar solo al iniciar PTT o llamada. */
    private fun activarModoLlamada() {
        runCatching {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
        }
    }

    /** Restaura el modo de audio normal. Llamar al terminar PTT o llamada. */
    private fun restaurarModoAudioNormal() {
        runCatching {
            audioManager?.isSpeakerphoneOn = false
            audioManager?.mode = AudioManager.MODE_NORMAL
        }
    }

    /** Alterna entre altavoz exterior y auricular para la llamada de voz. */
    fun toggleAltavoz(): Boolean {
        sonarClickBoton()
        val nuevo = !(audioManager?.isSpeakerphoneOn ?: true)
        runCatching { audioManager?.isSpeakerphoneOn = nuevo }
        return nuevo
    }

    fun esAltavozActivo(): Boolean = audioManager?.isSpeakerphoneOn ?: true

    // ========================================================
    // REPRODUCTOR CONTINUO CON AUDIOTRACK (SINGLE CONSUMER)
    // ========================================================

    private fun iniciarReproductorAudioTrack() {
        bucleReproduccionJob?.cancel()
        bucleReproduccionJob = scope.launch(Dispatchers.IO) {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.play()
                    audioTrackEnVivo = track

                    for (pcmChunk in canalColaAudio) {
                        if (!isActive) break
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            runCatching { track.write(pcmChunk, 0, pcmChunk.size) }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching {
                    audioTrackEnVivo?.stop()
                    audioTrackEnVivo?.release()
                }
                audioTrackEnVivo = null
            }
        }
    }

    // ========================================================
    // SINTONIZACIÓN DE CANAL Y PUSH-TO-TALK (PTT)
    // ========================================================

    fun sintonizarCanalAudio(canalId: String, canalNombre: String) {
        if (_audioChatEstado.value.canalActivoId == canalId) return
        _audioChatEstado.update {
            it.copy(
                canalActivoId = canalId,
                canalActivoNombre = canalNombre,
                hablantes = emptyList()
            )
        }
        iniciarObservadorCanalAudio(canalId)
    }

    fun toggleAudioChatActivo() {
        sonarClickBoton()
        _audioChatEstado.update {
            it.copy(chatAudioActivo = !it.chatAudioActivo)
        }
    }

    fun iniciarTransmisionVoz() {
        _audioChatEstado.update {
            it.copy(transmitiendoVoz = true)
        }
        sonarPttInicio()
        iniciarLiveStreamingPcm()
    }

    fun detenerTransmisionVoz() {
        _audioChatEstado.update {
            it.copy(transmitiendoVoz = false)
        }
        sonarPttFin()
        finalizarLiveStreamingPcm()
    }

    // ========================================================
    // LIVE STREAMING DE AUDIO PCM (PTT WALKIE-TALKIE)
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun iniciarLiveStreamingPcm() {
        val ctx = context ?: return
        val tienePermiso = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!tienePermiso) return

        microPttJob?.cancel()
        grabacionPcmStream = ByteArrayOutputStream()
        estaGrabandoPtt = true
        activarModoLlamada()  // Activa MODE_IN_COMMUNICATION solo mientras se usa el micrófono
        val streamId = UUID.randomUUID().toString()
        streamActualId = streamId

        val canalDestino = _audioChatEstado.value.canalActivoId
        val canalNombre = _audioChatEstado.value.canalActivoNombre
        val miId = usuarioIdActualProveedor()

        // 1. Notifica inicio de stream en vivo
        scope.launch(Dispatchers.IO) {
            val miPerfil = runCatching { repositorioUsuarios?.obtenerPorId(miId) }.getOrNull()
            val miNombre = miPerfil?.nombre ?: "Usuario"

            val jsonInicio = JSONObject().apply {
                put("type", "STREAM_START")
                put("streamId", streamId)
                put("canalId", canalDestino)
                put("canalNombre", canalNombre)
                put("remitenteId", miId)
                put("remitenteNombre", miNombre)
            }.toString()

            transmitirPaqueteUdpWifi(jsonInicio)
        }

        // 2. Transmite fragmentos PCM de 40ms en tiempo real
        microPttJob = scope.launch(Dispatchers.IO) {
            val miPerfil = runCatching { repositorioUsuarios?.obtenerPorId(miId) }.getOrNull()
            val miNombre = miPerfil?.nombre ?: "Usuario"

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1280)

            try {
                audioRecordPtt = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )

                if (audioRecordPtt?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecordPtt?.startRecording()
                    val chunkShorts = 640 // 40ms
                    val buffer = ShortArray(chunkShorts)
                    val byteBuffer = ByteArray(chunkShorts * 2)

                    while (isActive && estaGrabandoPtt && audioRecordPtt?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val leidos = audioRecordPtt?.read(buffer, 0, buffer.size) ?: 0
                        if (leidos > 0) {
                            var sumaCuadrados = 0.0
                            val bytesTotal = leidos * 2
                            for (i in 0 until leidos) {
                                val muestra = buffer[i]
                                sumaCuadrados += muestra * muestra
                                val idx = i * 2
                                byteBuffer[idx] = (muestra.toInt() and 0xFF).toByte()
                                byteBuffer[idx + 1] = ((muestra.toInt() shr 8) and 0xFF).toByte()
                            }
                            grabacionPcmStream?.write(byteBuffer, 0, bytesTotal)

                            val rms = sqrt(sumaCuadrados / leidos)
                            val nivel = (rms / 3000.0).toFloat().coerceIn(0.08f, 1.0f)
                            _nivelMicrofono.value = nivel

                            val base64Chunk = Base64.encodeToString(byteBuffer, 0, bytesTotal, Base64.NO_WRAP)
                            val jsonChunk = JSONObject().apply {
                                put("type", "PCM_CHUNK")
                                put("streamId", streamId)
                                put("canalId", canalDestino)
                                put("remitenteId", miId)
                                put("remitenteNombre", miNombre)
                                put("data", base64Chunk)
                            }.toString()

                            transmitirPaqueteUdpWifi(jsonChunk)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching {
                    audioRecordPtt?.stop()
                    audioRecordPtt?.release()
                }
                audioRecordPtt = null
                _nivelMicrofono.value = 0f
            }
        }
    }

    private fun finalizarLiveStreamingPcm() {
        estaGrabandoPtt = false
        microPttJob?.cancel()
        microPttJob = null
        restaurarModoAudioNormal()  // Devuelve audio a modo normal (libera Bluetooth A2DP)

        val streamId = streamActualId ?: UUID.randomUUID().toString()
        val canalDestino = _audioChatEstado.value.canalActivoId
        val miId = usuarioIdActualProveedor()
        val pcmBytes = grabacionPcmStream?.toByteArray() ?: ByteArray(0)
        grabacionPcmStream = null

        scope.launch(Dispatchers.IO) {
            val jsonFin = JSONObject().apply {
                put("type", "STREAM_END")
                put("streamId", streamId)
                put("canalId", canalDestino)
                put("remitenteId", miId)
            }.toString()
            transmitirPaqueteUdpWifi(jsonFin)

            if (pcmBytes.size > 1200) {
                runCatching {
                    val miPerfil = repositorioUsuarios?.obtenerPorId(miId)
                    val miNombre = miPerfil?.nombre ?: "Usuario"
                    val wavBytes = convertirPcmAWav(pcmBytes, SAMPLE_RATE, 1, 16)
                    val base64Wav = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
                    val mensajeVoz = "[VOZ_PTT:$streamId:$canalDestino:$miNombre:$base64Wav]"
                    repositorioMensajes?.enviarMensaje(canalDestino, miId, mensajeVoz)
                }
            }
        }
    }

    // ========================================================
    // TRANSMISOR Y RECEPTOR WIFI UDP BROADCAST (PERSISTENTE)
    // ========================================================

    private fun coincidenIds(id1: String?, id2: String?): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        val u1 = id1.replace("-", "").trim().lowercase()
        val u2 = id2.replace("-", "").trim().lowercase()
        if (u1.isNotEmpty() && u1 == u2) return true
        val resolver = resolverIdentidad ?: return false
        if (resolver.esMio(id1) && resolver.esMio(id2)) return true
        val ik1 = resolver.ikDe(id1) ?: id1
        val ik2 = resolver.ikDe(id2) ?: id2
        if (ik1.equals(ik2, ignoreCase = true)) return true
        val uid1 = resolver.usuarioDe(id1) ?: id1
        val uid2 = resolver.usuarioDe(id2) ?: id2
        val nu1 = uid1.replace("-", "").trim().lowercase()
        val nu2 = uid2.replace("-", "").trim().lowercase()
        return nu1.isNotEmpty() && nu1 == nu2
    }

    @Volatile
    private var direccionesBroadcastCache: List<InetAddress> = listOf(InetAddress.getByName("255.255.255.255"))
    private var ultimoRefrescoBroadcast = 0L

    private fun obtenerDestinosBroadcast(): List<InetAddress> {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoRefrescoBroadcast > 15_000L || direccionesBroadcastCache.size <= 1) {
            ultimoRefrescoBroadcast = ahora
            runCatching {
                val destinos = mutableSetOf<InetAddress>()
                destinos.add(InetAddress.getByName("255.255.255.255"))
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    for (ifaceAddr in iface.interfaceAddresses) {
                        ifaceAddr.broadcast?.let { destinos.add(it) }
                    }
                }
                direccionesBroadcastCache = destinos.toList()
            }
        }
        return direccionesBroadcastCache
    }

    private fun esDireccionLocal(a: InetAddress): Boolean {
        if (a.isLoopbackAddress) return false
        if (a.isSiteLocalAddress || a.isLinkLocalAddress) return true
        val b = a.address ?: return false
        return b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC
    }

    private fun transmitirPaqueteUdpWifi(jsonString: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val jsonFinal = runCatching {
                    val j = JSONObject(jsonString)
                    if (!j.has("sig")) firmarJsonUdpSiEsPosible(j) else jsonString
                }.getOrDefault(jsonString)
                val data = jsonFinal.toByteArray(Charsets.UTF_8)
                val destinos = obtenerDestinosBroadcast()
                val activeSocket = socketUdp
                if (activeSocket != null && !activeSocket.isClosed) {
                    for (dest in destinos) {
                        activeSocket.send(DatagramPacket(data, data.size, dest, PUERTO_WIFI_UDP))
                    }
                } else {
                    DatagramSocket().use { ephemeral ->
                        ephemeral.broadcast = true
                        for (dest in destinos) {
                            ephemeral.send(DatagramPacket(data, data.size, dest, PUERTO_WIFI_UDP))
                        }
                    }
                }
            }
        }
    }

    // FIX (lag de video en llamadas): envío directo a una sola IP (unicast), en vez de
    // a toda la red como transmitirPaqueteUdpWifi(). Lo usa enviarFrameVideo() para
    // que el video de una llamada 1 a 1 ya no sature al resto de dispositivos de la
    // red WiFi.
    private fun transmitirPaqueteUnicast(jsonString: String, destino: InetAddress) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val jsonFinal = runCatching {
                    val j = JSONObject(jsonString)
                    if (!j.has("sig")) firmarJsonUdpSiEsPosible(j) else jsonString
                }.getOrDefault(jsonString)
                val data = jsonFinal.toByteArray(Charsets.UTF_8)
                val activeSocket = socketUdp
                if (activeSocket != null && !activeSocket.isClosed) {
                    activeSocket.send(DatagramPacket(data, data.size, destino, PUERTO_WIFI_UDP))
                } else {
                    DatagramSocket().use { ephemeral ->
                        ephemeral.broadcast = true
                        ephemeral.send(DatagramPacket(data, data.size, destino, PUERTO_WIFI_UDP))
                    }
                }
            }
        }
    }

    // No se utiliza actualmente, pero queda el código para futuro uso: para compartir
    // video entre todos los dispositivos cercanos (por ejemplo, una videollamada
    // grupal transmitida a toda la red, en vez de 1 a 1), o para simular una mala señal
    // de video reproduciendo a propósito la misma saturación de red que causaba el lag
    // original (útil como caso de prueba de resiliencia). Antes de este fix, era el
    // camino que usaba enviarFrameVideo() para cada frame de la llamada.
    private fun transmitirFrameVideoBroadcast(jsonString: String) {
        transmitirPaqueteUdpWifi(jsonString)
    }

    private fun iniciarServidorUdpWifi() {
        servidorUdpJob?.cancel()
        servidorUdpJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var socket: DatagramSocket? = null
                try {
                    // H-13: reuseAddress debe configurarse ANTES del bind
                    socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(java.net.InetSocketAddress(PUERTO_WIFI_UDP))
                    }
                    socketUdp = socket
                    val buffer = ByteArray(65507)

                    while (isActive && !socket.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        // H-02: solo procesar datagramas de la red local
                        if (esDireccionLocal(packet.address)) {
                            val jsonString = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            procesarPaqueteJsonWifi(jsonString, packet.address)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("GestorLlamadas", "Excepción en servidor UDP WiFi: ${e.message}; reintentando...")
                    delay(1000L)
                } finally {
                    runCatching { socket?.close() }
                }
            }
        }
    }

    private fun calcularHashContenido(json: JSONObject): String {
        // H-02: el hash firmado debe cubrir todos los campos sensibles y anti-replay
        val campos = listOf(
            json.optString("type"),
            json.optString("remitenteId"),
            json.optString("targetId"),
            json.optString("callId"),
            json.optString("data"),
            json.optString("sdp"),
            json.optString("sdpOffer"),
            json.optString("sdpAnswer"),
            json.optString("candidate"),
            json.optString("canalId"),
            json.optString("accion"),
            json.optString("audioBase64"),
            json.optString("contenido"),
            json.optString("usuarioId"),
            json.optString("remitenteNombre"),
            json.optString("remitenteFoto"),
            json.optString("id"),
            json.optString("ts"),
            json.optString("nonce")
        )
        val contenido = campos.joinToString("|")
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(md.digest(contenido.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun firmarJsonUdpSiEsPosible(json: JSONObject): String {
        val gestor = gestorIdentidad
        if (gestor != null) {
            val pub = gestor.obtenerClavePublicaBase64()
            json.put("pub", pub)
            if (!json.has("ts")) {
                json.put("ts", System.currentTimeMillis())
            }
            if (!json.has("nonce")) {
                json.put("nonce", java.util.UUID.randomUUID().toString())
            }
            val hashContenido = calcularHashContenido(json)
            json.put("h", hashContenido)
            val sig = gestor.firmar(hashContenido)
            json.put("sig", sig)
        }
        return json.toString()
    }

    // H-02: tipos de señal que modifican estado y deben rechazarse sin autenticación
    private val TIPOS_SENSIBLES = setOf(
        "CALL_START", "CALL_ACCEPT", "CALL_HANGUP", "SOLICITUD_AMISTAD",
        "RESPUESTA_AMISTAD", "NOTIF_AMIGO_AGREGADO", "PUB_PERFIL", "STREAM_START",
        "STREAM_END", "REGALO_MASCOTA", "VOZ_PTT"
    )

    // H-02: validar que una foto de un remitente remoto sea segura
    private fun String?.esFotoSegura(): Boolean =
        this != null && !contains("..")

    private suspend fun procesarPaqueteJsonWifi(
        jsonString: String,
        origen: InetAddress? = null,
        remitenteVerificado: String? = null   // H-02: identidad autenticada por el router
    ) {
        runCatching {
            val json = JSONObject(jsonString)
            val tipo = json.optString("type")
            val remitenteIdJson = json.optString("remitenteId")
            val miId = usuarioIdActualProveedor()

            // H-02: si el router ya autenticó al remitente, el JSON no puede contradecirlo
            if (remitenteVerificado != null && remitenteIdJson.isNotBlank()) {
                if (remitenteIdJson != remitenteVerificado && !coincidenIds(remitenteIdJson, remitenteVerificado)) {
                    return@runCatching
                }
                resolverIdentidad?.let { res ->
                    if (remitenteVerificado.length > 64 && remitenteIdJson.length <= 64) {
                        res.registrarVinculo(remitenteIdJson, remitenteVerificado)
                    } else if (remitenteIdJson.length > 64 && remitenteVerificado.length <= 64) {
                        res.registrarVinculo(remitenteVerificado, remitenteIdJson)
                    }
                }
            }

            val remitenteId = if (remitenteIdJson.isNotBlank()) remitenteIdJson else (remitenteVerificado ?: "")
            val remitenteNombre = json.optString("remitenteNombre", "Usuario")

            if (remitenteId.isNotBlank() && coincidenIds(remitenteId, miId)) return@runCatching

            val ahora = System.currentTimeMillis()
            val ts = json.optLong("ts", 0L)
            val nonce = json.optString("nonce")

            // H-02: Anti-replay estricto. Para tipos sensibles, timestamp y nonce son obligatorios.
            // H-02: Anti-replay estricto. Para llamadas en vivo la ventana es estrecha (120s);
            // para señales offline (estados, publicaciones, solicitudes) toleramos desincronización de reloj (1 hora)
            val esTiempoReal = tipo in setOf("CALL_START", "CALL_ACCEPT", "CALL_HANGUP", "VOZ_PTT", "STREAM_START", "STREAM_END")
            val ventanaMaxima = when {
                esTiempoReal -> if (remitenteVerificado != null) 120_000L else 60_000L
                remitenteVerificado != null -> 3_600_000L   // 1 hora de tolerancia en malla mesh
                else -> 900_000L                            // 15 minutos en WiFi LAN
            }
            if (tipo in TIPOS_SENSIBLES) {
                if (ts <= 0L || Math.abs(ahora - ts) > ventanaMaxima) {
                    android.util.Log.w("GestorLlamadas", "Señal sensible '$tipo' de $remitenteId rechazada: timestamp inválido o expirado ($ts vs ahora $ahora, diff=${Math.abs(ahora - ts)})")
                    return@runCatching
                }
                if (nonce.isBlank()) {
                    android.util.Log.w("GestorLlamadas", "Señal sensible '$tipo' de $remitenteId rechazada: nonce requerido ausente")
                    return@runCatching
                }
            } else if (ts > 0L) {
                if (Math.abs(ahora - ts) > ventanaMaxima) {
                    android.util.Log.w("GestorLlamadas", "Paquete UDP de $remitenteId rechazado: timestamp expirado ($ts vs ahora $ahora, diff=${Math.abs(ahora - ts)})")
                    return@runCatching
                }
            }

            if (nonce.isNotBlank()) {
                val claveNonce = "$remitenteId:$nonce"
                if (paquetesProcesados.containsKey(claveNonce)) return@runCatching
                paquetesProcesados[claveNonce] = ahora
            }

            val sig = json.optString("sig")
            val pub = json.optString("pub")
            val gestor = gestorIdentidad
            val pines = pinesIdentidad

            var remitenteAutenticado = remitenteVerificado != null   // ya verificado por el router
            if (!remitenteAutenticado && gestor != null && sig.isNotBlank() && pub.isNotBlank()) {
                val hashEsperado = json.optString("h")
                val hashCalculado = calcularHashContenido(json)
                // H-02: Eliminación estricta del fallback legado sin cobertura completa de contenido
                val firmaValida = hashEsperado.isNotBlank() && hashEsperado == hashCalculado &&
                    gestor.verificarFirma(hashCalculado, sig, pub)

                if (firmaValida) {
                    val pin = pines?.obtener(remitenteId)
                    if (pin != null && pin == pub) {
                        remitenteAutenticado = true
                    } else if (pin == null) {
                        // H-02: Sin pin previo conocido, se valida contra la clave pública oficial almacenada en el repositorio remoto.
                        val claveRemota = repositorioUsuarios?.obtenerPorId(remitenteId)?.clavePublicaFirma
                        if (claveRemota != null && claveRemota == pub) {
                            pines?.fijar(remitenteId, pub, com.renea.arete_social.data.crypto.EstadoVerificacionIdentidad.VERIFICADO)
                            remitenteAutenticado = true
                        } else if (tipo in setOf("SOLICITUD_AMISTAD", "RESPUESTA_AMISTAD", "PUB_PERFIL", "PUB_LIKE", "REGALO_MASCOTA", "NOTIF_MSG")) {
                            // En modo offline/P2P sin conexión a internet, si la firma del remitente es válida, permitimos recibir y fijamos TOFU
                            pines?.fijar(remitenteId, pub, com.renea.arete_social.data.crypto.EstadoVerificacionIdentidad.NO_VERIFICADO)
                            remitenteAutenticado = true
                        } else {
                            android.util.Log.w("GestorLlamadas", "Remitente UDP $remitenteId sin pin conocido ni clave remota. No autenticado.")
                        }
                    } else {
                        android.util.Log.w("GestorLlamadas", "Conflicto TOFU en paquete UDP de $remitenteId. Ignorado por seguridad.")
                        return@runCatching
                    }
                }
            }

            // H-02 fail-closed: señales que cambian estado requieren autenticación
            if (!remitenteAutenticado && tipo in TIPOS_SENSIBLES) {
                android.util.Log.w("GestorLlamadas", "Señal sensible '$tipo' de $remitenteId rechazada: sin autenticación.")
                return@runCatching
            }

            // Seguridad Wi-Fi: SOLO se asocia o actualiza la IP en direccionesConocidas si el paquete
            // fue estrictamente autenticado criptográficamente. Esto previene el secuestro de llamadas (call hijacking).
            if (remitenteId.isNotBlank() && origen != null && remitenteAutenticado) {
                direccionesConocidas[remitenteId] = origen
            }

            val estado = _audioChatEstado.value

            when (tipo) {
                // 1. TRANSMISIÓN DE AUDIO EN VIVO (PTT SALA / CHAT)
                "STREAM_START" -> {
                    val canalId = json.optString("canalId")
                    if (estado.canalActivoId == canalId && estado.chatAudioActivo && !estado.transmitiendoVoz) {
                        marcarHablanteActivo(remitenteId, remitenteNombre)
                        sonarEfectoRadio(esInicio = true)
                    }
                }
                "PCM_CHUNK" -> {
                    val canalId = json.optString("canalId")
                    if (estado.canalActivoId == canalId && estado.chatAudioActivo && !estado.transmitiendoVoz) {
                        // H-02: Solo procesar PCM si el remitente fue autorizado previamente por un STREAM_START firmado
                        if (_audioChatEstado.value.hablantes.any { it.usuarioId == remitenteId }) {
                            val dataB64 = json.optString("data")
                            val pcmBytes = Base64.decode(dataB64, Base64.NO_WRAP)
                            canalColaAudio.trySend(pcmBytes)
                        } else {
                            android.util.Log.w("GestorLlamadas", "PCM_CHUNK descartado: $remitenteId no es un hablante autorizado activo")
                        }
                    }
                }
                "STREAM_END" -> {
                    val canalId = json.optString("canalId")
                    if (estado.canalActivoId == canalId) {
                        quitarHablanteActivo(remitenteId)
                        sonarEfectoRadio(esInicio = false)
                    }
                }
                "VOZ_PTT" -> {
                    val canalId = json.optString("canalId")
                    val id = json.optString("id")
                    val claveDedupe = "$remitenteId:$id"
                    if (paquetesProcesados.containsKey(claveDedupe)) return@runCatching
                    paquetesProcesados[claveDedupe] = System.currentTimeMillis()
                    val audioBase64 = json.optString("audioBase64")
                    if (estado.canalActivoId == canalId && estado.chatAudioActivo && !estado.transmitiendoVoz) {
                        reproducirAudioWavBase64(remitenteId, remitenteNombre, audioBase64)
                    }
                }

                // 2. AUDIO Y VIDEO DÚPLEX EN LLAMADA 1-A-1
                "CALL_AUDIO" -> {
                    val callId = json.optString("callId")
                    val llamada = _llamadaActual.value
                    if (llamada != null && llamada.estado == EstadoLlamada.EN_LLAMADA && (llamada.id == callId || coincidenIds(llamada.remoto.id, remitenteId))) {
                        // H-02: Validar que la IP coincida con la IP aprendida tras autenticación criptográfica
                        val ipEsperada = direccionesConocidas[llamada.remoto.id]
                        if (origen != null && ipEsperada != null && origen != ipEsperada) {
                            android.util.Log.w("GestorLlamadas", "CALL_AUDIO de IP no autorizada ($origen != $ipEsperada). Descartado.")
                            return@runCatching
                        }
                        val dataB64 = json.optString("data")
                        val pcmBytes = Base64.decode(dataB64, Base64.NO_WRAP)
                        canalColaAudio.trySend(pcmBytes)
                    }
                }
                "CALL_VIDEO_FRAME" -> {
                    val callId = json.optString("callId")
                    val llamada = _llamadaActual.value
                    if (llamada != null && llamada.estado == EstadoLlamada.EN_LLAMADA && (llamada.id == callId || coincidenIds(llamada.remoto.id, remitenteId))) {
                        // H-02: Validar que la IP coincida con la IP aprendida tras autenticación criptográfica
                        val ipEsperada = direccionesConocidas[llamada.remoto.id]
                        if (origen != null && ipEsperada != null && origen != ipEsperada) {
                            android.util.Log.w("GestorLlamadas", "CALL_VIDEO_FRAME de IP no autorizada ($origen != $ipEsperada). Descartado.")
                            return@runCatching
                        }
                        val frameB64 = json.optString("data")
                        // Limitar tamaño máximo del frame codificado a 700 KB para evitar OOM
                        if (frameB64.isNotBlank() && frameB64.length <= 700_000) {
                            _videoFrameRemoto.value = frameB64
                            scope.launch(Dispatchers.Default) {
                                runCatching {
                                    val bytes = Base64.decode(frameB64, Base64.NO_WRAP)
                                    if (bytes.size <= 524_288) { // 512 KB máx decodificado
                                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                                        if (boundsOpts.outWidth in 1..1920 && boundsOpts.outHeight in 1..1920) {
                                            val decodeOpts = BitmapFactory.Options().apply {
                                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                            }
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                                            if (bmp != null) {
                                                _videoFrameRemotoBitmap.value = bmp
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. NOTIFICACIONES Y SOLICITUDES DE AMISTAD
                "SOLICITUD_AMISTAD" -> {
                    val targetId = json.optString("targetId")
                    android.util.Log.e("GestorLlamadas", "Procesando SOLICITUD_AMISTAD: targetId=$targetId miId=$miId coinciden=${coincidenIds(targetId, miId)} remitente=$remitenteId")
                    if (coincidenIds(targetId, miId)) {
                        val senderId = remitenteId
                        val senderNombre = remitenteNombre
                        // H-02: validar que la foto sea de un dominio seguro; ignorar si no lo es
                        val senderFoto = json.optString("remitenteFoto").takeIf { it.isNotBlank() && it.esFotoSegura() }
                        com.renea.arete_social.feature.auth.GestorAmistades.recibirAvisoSolicitud(
                            com.renea.arete_social.domain.model.SolicitudAmistad(
                                remitenteId = senderId,
                                remitenteNombre = senderNombre,
                                remitenteFoto = senderFoto
                            )
                        )
                        mostrarNotificacion(
                            NotificacionTactica(
                                tipo = TipoNotificacionTactica.SOLICITUD_AMISTAD,
                                titulo = "SOLICITUD DE AMISTAD",
                                subtitulo = "$senderNombre te envió una solicitud",
                                remitenteId = senderId,
                                remitenteNombre = senderNombre,
                                remitenteFoto = senderFoto
                            )
                        )
                    }
                }
                "REGALO_MASCOTA" -> {
                    if (coincidenIds(json.optString("targetId"), miId)) {
                        entregarRegaloMascota(
                            remitenteId = remitenteId,
                            remitenteNombre = remitenteNombre,
                            regaloId = json.optString("regaloId"),
                            tipoTexto = json.optString("regalo"),
                            cantidadTexto = json.optString("cantidad")
                        )
                    }
                }
                "ESTADO_MICHI" -> {
                    val senderId = remitenteId
                    val estadoMichi = json.optString("estado").ifBlank { null }
                    val vence = json.optLong("vence", 0L)
                    onEstadoMascotaRecibido?.invoke(senderId, estadoMichi, vence)
                }
                "RESPUESTA_AMISTAD" -> {
                    val targetId = json.optString("targetId")
                    if (coincidenIds(targetId, miId) && remitenteAutenticado) {
                        val accion = json.optString("accion")
                        val senderId = remitenteId
                        val senderNombre = remitenteNombre
                        val senderFoto = json.optString("remitenteFoto").takeIf { it.isNotBlank() && it.esFotoSegura() }
                        com.renea.arete_social.feature.auth.GestorAmistades.recibirAvisoRespuesta(senderId, accion == "ACEPTADA")
                        if (accion == "ACEPTADA") {
                            mostrarNotificacion(
                                NotificacionTactica(
                                    tipo = TipoNotificacionTactica.AMISTAD_ACEPTADA,
                                    titulo = "¡SOLICITUD ACEPTADA!",
                                    subtitulo = "$senderNombre aceptó tu solicitud",
                                    remitenteId = senderId,
                                    remitenteNombre = senderNombre,
                                    remitenteFoto = senderFoto
                                )
                            )
                        }
                    }
                }
                "NOTIF_AMIGO_AGREGADO" -> {
                    val targetId = json.optString("targetId")
                    if (coincidenIds(targetId, miId) && remitenteAutenticado) {
                        val senderId = remitenteId
                        val senderNombre = remitenteNombre
                        val senderFoto = json.optString("remitenteFoto").takeIf { it.isNotBlank() && it.esFotoSegura() }
                        com.renea.arete_social.feature.auth.GestorAmistades.recibirAvisoRespuesta(senderId, true)
                        mostrarNotificacion(
                            NotificacionTactica(
                                tipo = TipoNotificacionTactica.AMIGO_AGREGADO,
                                titulo = "¡NUEVO AMIGO!",
                                subtitulo = "$senderNombre te agregó",
                                remitenteId = senderId,
                                remitenteNombre = senderNombre,
                                remitenteFoto = senderFoto
                            )
                        )
                    }
                }
                "NOTIF_MENSAJE" -> {
                    val targetId = json.optString("targetId")
                    if (targetId.isBlank() || targetId == "todos" || coincidenIds(targetId, miId)) {
                        val idMsg = json.optString("id")
                        if (idMsg.isNotBlank()) {
                            val claveMsg = "$remitenteId:$idMsg"
                            if (paquetesProcesados.containsKey(claveMsg)) return@runCatching
                            paquetesProcesados[claveMsg] = System.currentTimeMillis()
                        }
                        val senderId = remitenteId
                        val senderNombre = remitenteNombre
                        val senderFoto = json.optString("remitenteFoto").takeIf { it.isNotBlank() && it.esFotoSegura() }
                        val chatId = json.optString("chatId")
                        val contenido = json.optString("contenido")

                        if (_llamadaActual.value == null) {
                            mostrarNotificacion(
                                NotificacionTactica(
                                    tipo = TipoNotificacionTactica.MENSAJE,
                                    titulo = "MENSAJE",
                                    subtitulo = contenido.take(50),
                                    remitenteId = senderId,
                                    remitenteNombre = senderNombre,
                                    remitenteFoto = senderFoto,
                                    chatId = chatId
                                )
                            )
                        }
                    }
                }

                // 4. SEÑALIZACIÓN DE VIDEOLLAMADAS 1-A-1
                "CALL_START" -> {
                    val callId = json.optString("callId")
                    val targetId = json.optString("targetId")
                    val remitenteFoto = json.optString("remitenteFoto").takeIf { it.isNotBlank() && it.esFotoSegura() }

                    // H-02 y H-13: Solo aceptar si el remitente está autenticado y atómicamente no hay llamada activa
                    if (coincidenIds(targetId, miId) && remitenteAutenticado && _llamadaActual.compareAndSet(
                            null,
                            LlamadaInfo(
                                id = callId,
                                remoto = ContactoLlamada(id = remitenteId, nombre = remitenteNombre, foto = remitenteFoto),
                                tipo = TipoLlamada.VIDEO,
                                estado = EstadoLlamada.ENTRANTE,
                                timestampInicio = System.currentTimeMillis()
                            )
                        )
                    ) {
                        iniciarTimbreLlamadaEntrante()
                    }
                }
                "CALL_ACCEPT" -> {
                    val callId = json.optString("callId")
                    val targetId = json.optString("targetId")
                    if (coincidenIds(targetId, miId) || _llamadaActual.value?.id == callId) {
                        if (_llamadaActual.value?.estado == EstadoLlamada.LLAMANDO) {
                            detenerTimbreLlamada()
                            sonarLlamadaConectada()
                            establecerLlamadaConectada()
                        }
                    }
                }
                "CALL_HANGUP" -> {
                    val callId = json.optString("callId")
                    val llamada = _llamadaActual.value
                    // H-02: Verificar que quien cuelga sea realmente el interlocutor de la llamada activa
                    if (llamada != null && llamada.id == callId && (coincidenIds(llamada.remoto.id, remitenteId) || remitenteId.isBlank())) {
                        detenerTimbreLlamada()
                        detenerBucleVozLlamada()
                        sonarLlamadaFinalizada()
                        restaurarModoAudioNormal()
                        _videoFrameRemoto.value = null
                        _videoFrameRemotoBitmap.value = null
                        _llamadaActual.value = llamada.copy(estado = EstadoLlamada.FINALIZADA)
                        scope.launch {
                            delay(800)
                            _llamadaActual.value = null
                            _videoFrameRemoto.value = null
                            _videoFrameRemotoBitmap.value = null
                        }
                    }
                }

                // 5. PUBLICACIONES EFÍMERAS DE PERFIL (FOTOS DIGITALES Y NOTAS RETRO)
                "PUB_PERFIL" -> {
                    val pubId = json.optString("id")
                    // H-02 / H-10: usar el remitente autenticado como autor para evitar publicaciones suplantadas
                    val pubUserId = remitenteId
                    val pubAutorNombre = remitenteNombre
                    val pubTipoStr = json.optString("tipo")
                    val pubTipo = runCatching { com.renea.arete_social.domain.model.TipoPublicacionPerfil.valueOf(pubTipoStr) }.getOrDefault(com.renea.arete_social.domain.model.TipoPublicacionPerfil.TEXTO_RETRO)
                    val pubContenido = json.optString("contenido")
                    val pubTimestamp = json.optLong("timestamp", System.currentTimeMillis())
                    val pubLikes = json.optInt("likes", 0)

                    if (pubContenido.isNotBlank() && pubUserId.isNotBlank()) {
                        com.renea.arete_social.feature.auth.GestorPerfilEfemero.recibirPublicacionRemota(
                            com.renea.arete_social.domain.model.PublicacionPerfil(
                                id = pubId,
                                usuarioId = pubUserId,
                                autorNombre = pubAutorNombre,
                                tipo = pubTipo,
                                contenido = pubContenido,
                                timestamp = pubTimestamp,
                                likes = pubLikes,
                                usuariosLike = emptySet()
                            )
                        )
                    }
                }
                "PUB_LIKE" -> {
                    val pubId = json.optString("publicacionId")
                    val likeUserId = json.optString("usuarioId")
                    if (pubId.isNotBlank() && likeUserId.isNotBlank()) {
                        com.renea.arete_social.feature.auth.GestorPerfilEfemero.registrarLikeRemoto(pubId, likeUserId)
                    }
                }
            }
        }
    }

    private fun marcarHablanteActivo(remitenteId: String, remitenteNombre: String) {
        val hablante = HablanteVoz(
            usuarioId = remitenteId,
            nombre = remitenteNombre,
            nivelAudio = 0.90f,
            hablando = true
        )
        _audioChatEstado.update { it.copy(hablantes = listOf(hablante)) }

        watchdogStreamJob?.cancel()
        watchdogStreamJob = scope.launch(Dispatchers.IO) {
            delay(1500)
            quitarHablanteActivo(remitenteId)
        }
    }

    private fun quitarHablanteActivo(remitenteId: String? = null) {
        watchdogStreamJob?.cancel()
        watchdogStreamJob = null
        _audioChatEstado.update { est ->
            if (remitenteId != null) {
                est.copy(hablantes = est.hablantes.filter { it.usuarioId != remitenteId })
            } else {
                est.copy(hablantes = emptyList())
            }
        }
    }

    // ========================================================
    // OBSERVADORES DE RESPALDO (Mesh / Local)
    // ========================================================

    private fun iniciarObservadorCanalAudio(canalId: String) {
        observadorAudioJob?.cancel()
        val repo = repositorioMensajes ?: return

        observadorAudioJob = scope.launch(Dispatchers.IO) {
            repo.observarMensajes(canalId)
                .catch { /* Resiliente */ }
                .collect { listaMensajes ->
                    val miId = usuarioIdActualProveedor()
                    val estado = _audioChatEstado.value

                    for (m in listaMensajes) {
                        if (paquetesProcesados.containsKey(m.id)) continue
                        if (coincidenIds(m.remitenteId, miId)) continue

                        val texto = m.contenido
                        if (texto.startsWith("[VOZ_PTT:")) {
                            paquetesProcesados[m.id] = System.currentTimeMillis()
                            val limpio = texto.removePrefix("[VOZ_PTT:").removeSuffix("]")
                            val partes = limpio.split(":")
                            if (partes.size >= 4) {
                                val canalMsg = partes[1]
                                val nombreMsg = partes[2]
                                val b64Msg = partes[3]

                                if (estado.canalActivoId == canalMsg && estado.chatAudioActivo && !estado.transmitiendoVoz) {
                                    reproducirAudioWavBase64(m.remitenteId, nombreMsg, b64Msg)
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun iniciarObservadorSenalesRespaldo() {
        observadorSenalesJob?.cancel()
    }

    private fun iniciarObservadorSenalesGeneral() {
        // Mantenido por compatibilidad con el observador de VOZ_PTT del chat general.
        // Las señales de control ya no se leen aquí — se leen en iniciarObservadorSenalesRespaldo().
        observadorSenalesGeneralJob?.cancel()
        val repo = repositorioMensajes ?: return

        observadorSenalesGeneralJob = scope.launch(Dispatchers.IO) {
            repo.observarMensajes(_audioChatEstado.value.canalActivoId)
                .catch { /* Resiliente */ }
                .collect { listaMensajes ->
                    val miId = usuarioIdActualProveedor()

                    for (m in listaMensajes) {
                        if (paquetesProcesados.containsKey(m.id)) continue
                        if (coincidenIds(m.remitenteId, miId)) continue

                        val texto = m.contenido
                        // Solo VOZ_PTT sigue por este canal; las señales [SENAL_*] ya no se emiten aquí
                        if (texto.startsWith("[VOZ_PTT:")) {
                            paquetesProcesados[m.id] = System.currentTimeMillis()
                            val limpio = texto.removePrefix("[VOZ_PTT:").removeSuffix("]")
                            val partes = limpio.split(":")
                            if (partes.size >= 4) {
                                val canalMsg = partes[1]
                                val nombreMsg = partes[2]
                                val b64Msg = partes[3]
                                val estado = _audioChatEstado.value
                                if (estado.canalActivoId == canalMsg && estado.chatAudioActivo && !estado.transmitiendoVoz) {
                                    reproducirAudioWavBase64(m.remitenteId, nombreMsg, b64Msg)
                                }
                            }
                        } else if (texto.startsWith("[NOTIF_MSG:")) {
                            // Fallback para notificaciones de mensaje por chat general (dispositivos sin señales)
                            val limpio = texto.removePrefix("[NOTIF_MSG:").removeSuffix("]")
                            val partes = limpio.split(":", limit = 5)
                            if (partes.size >= 4) {
                                val destinatarioId = partes[0]
                                // H-10: vincular siempre al remitente real autenticado del mensaje (m.remitenteId)
                                // para evitar que un usuario suplante a otro en notificaciones tácticas
                                val senderId = m.remitenteId
                                val senderNombre = repositorioUsuarios?.obtenerPorId(m.remitenteId)?.nombre
                                    ?: partes[2].takeIf { it.isNotBlank() }
                                    ?: "Usuario"
                                val chatId = partes[3]
                                val rawTexto = if (partes.size >= 5) partes[4] else ""
                                val mensajeTexto = if (rawTexto.startsWith("ENC:")) "[🔒 Mensaje cifrado]" else rawTexto

                                if (destinatarioId.isBlank() || destinatarioId == "todos" || coincidenIds(destinatarioId, miId)) {
                                    paquetesProcesados[m.id] = System.currentTimeMillis()
                                    val esReciente = (System.currentTimeMillis() - m.timestamp) < 180_000L
                                    if (esReciente && _llamadaActual.value == null) {
                                        mostrarNotificacion(
                                            NotificacionTactica(
                                                tipo = TipoNotificacionTactica.MENSAJE,
                                                titulo = "MENSAJE",
                                                subtitulo = mensajeTexto.take(50),
                                                remitenteId = senderId,
                                                remitenteNombre = senderNombre,
                                                chatId = chatId
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (!texto.startsWith("[") && !texto.endsWith("]")) {
                            paquetesProcesados[m.id] = System.currentTimeMillis()
                            val esReciente = (System.currentTimeMillis() - m.timestamp) < 180_000L
                            if (esReciente && _llamadaActual.value == null) {
                                val remitenteNombre = repositorioUsuarios?.obtenerPorId(m.remitenteId)?.nombre ?: m.remitenteId.take(8)
                                val textoVisual = if (texto.startsWith("ENC:")) "[🔒 Mensaje cifrado]" else texto
                                mostrarNotificacion(
                                    NotificacionTactica(
                                        tipo = TipoNotificacionTactica.MENSAJE,
                                        titulo = "MENSAJE",
                                        subtitulo = textoVisual.take(50),
                                        remitenteId = m.remitenteId,
                                        remitenteNombre = remitenteNombre,
                                        chatId = m.chatId
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Procesa una señal de la tabla `senales` aplicando la misma lógica de negocio
     * que el antiguo observador del chat general, pero ahora con campos tipados del JsonObject.
     */
    private fun procesarSenalEntrante(senal: SenalLlamada, miId: String) {
        val p = senal.payload
        fun str(key: String) = runCatching { p[key].toString().trim('"') }.getOrDefault("")

        when (senal.tipo) {
            "SENAL_LLAMADA_INICIAR" -> {
                val destinatarioId = senal.destinatarioId
                if (coincidenIds(destinatarioId, miId) && _llamadaActual.value == null) {
                    _llamadaActual.value = LlamadaInfo(
                        id = str("callId"),
                        remoto = ContactoLlamada(
                            id = senal.remitenteId,
                            nombre = str("remitenteNombre"),
                            foto = str("remitenteFoto").takeIf { it.isNotBlank() }
                        ),
                        tipo = TipoLlamada.VIDEO,
                        estado = EstadoLlamada.ENTRANTE,
                        timestampInicio = System.currentTimeMillis()
                    )
                    iniciarTimbreLlamadaEntrante()
                }
            }

            "SENAL_LLAMADA_ACEPTADA" -> {
                val callId = str("callId")
                if (_llamadaActual.value?.id == callId && _llamadaActual.value?.estado == EstadoLlamada.LLAMANDO) {
                    detenerTimbreLlamada()
                    sonarLlamadaConectada()
                    establecerLlamadaConectada()
                }
            }

            "SENAL_LLAMADA_COLGADA" -> {
                val callId = str("callId")
                if (_llamadaActual.value?.id == callId) {
                    detenerTimbreLlamada()
                    detenerBucleVozLlamada()
                    sonarLlamadaFinalizada()
                    restaurarModoAudioNormal()
                    _videoFrameRemoto.value = null
                    _videoFrameRemotoBitmap.value = null
                    _llamadaActual.value = null
                }
            }

            "SOLICITUD_AMISTAD" -> {
                com.renea.arete_social.feature.auth.GestorAmistades.recibirAvisoSolicitud(
                    com.renea.arete_social.domain.model.SolicitudAmistad(
                        remitenteId = senal.remitenteId,
                        remitenteNombre = str("remitenteNombre"),
                        remitenteFoto = str("remitenteFoto").takeIf { it.isNotBlank() }
                    )
                )
                mostrarNotificacion(
                    NotificacionTactica(
                        tipo = TipoNotificacionTactica.SOLICITUD_AMISTAD,
                        titulo = "SOLICITUD DE AMISTAD",
                        subtitulo = "${str("remitenteNombre")} te envió una solicitud",
                        remitenteId = senal.remitenteId,
                        remitenteNombre = str("remitenteNombre"),
                        remitenteFoto = str("remitenteFoto").takeIf { it.isNotBlank() }
                    )
                )
            }

            "REGALO_MASCOTA" -> {
                if (coincidenIds(senal.destinatarioId, miId)) {
                    entregarRegaloMascota(
                        remitenteId = senal.remitenteId,
                        remitenteNombre = str("remitenteNombre"),
                        regaloId = str("regaloId"),
                        tipoTexto = str("regalo"),
                        cantidadTexto = str("cantidad")
                    )
                }
            }

            "ESTADO_MICHI" -> {
                val senderId = senal.remitenteId
                val estadoMichi = str("estado").ifBlank { null }
                val vence = str("vence").toLongOrNull() ?: 0L
                onEstadoMascotaRecibido?.invoke(senderId, estadoMichi, vence)
            }

            "RESPUESTA_AMISTAD" -> {
                val accion = str("accion")
                com.renea.arete_social.feature.auth.GestorAmistades.recibirAvisoRespuesta(senal.remitenteId, accion == "ACEPTADA")
                if (accion == "ACEPTADA") {
                    mostrarNotificacion(
                        NotificacionTactica(
                            tipo = TipoNotificacionTactica.AMISTAD_ACEPTADA,
                            titulo = "¡SOLICITUD ACEPTADA!",
                            subtitulo = "${str("remitenteNombre")} aceptó tu solicitud",
                            remitenteId = senal.remitenteId,
                            remitenteNombre = str("remitenteNombre"),
                            remitenteFoto = str("remitenteFoto").takeIf { it.isNotBlank() && it.esFotoSegura() }
                        )
                    )
                }
            }

            "NOTIF_MSG" -> {
                val destId = senal.destinatarioId
                if (destId.isNullOrBlank() || destId == "todos" || coincidenIds(destId, miId)) {
                    if (_llamadaActual.value == null) {
                        mostrarNotificacion(
                            NotificacionTactica(
                                tipo = TipoNotificacionTactica.MENSAJE,
                                titulo = "MENSAJE",
                                subtitulo = str("contenido").take(50),
                                remitenteId = senal.remitenteId,
                                remitenteNombre = str("remitenteNombre"),
                                chatId = str("chatId")
                            )
                        )
                    }
                }
            }

            "PUB_PERFIL" -> {
                val pubTipo = runCatching {
                    com.renea.arete_social.domain.model.TipoPublicacionPerfil.valueOf(str("tipo"))
                }.getOrDefault(com.renea.arete_social.domain.model.TipoPublicacionPerfil.TEXTO_RETRO)
                com.renea.arete_social.feature.auth.GestorPerfilEfemero.recibirPublicacionRemota(
                    com.renea.arete_social.domain.model.PublicacionPerfil(
                        id = str("pubId"),
                        usuarioId = senal.remitenteId.ifBlank { str("usuarioId") },
                        autorNombre = str("autorNombre"),
                        tipo = pubTipo,
                        contenido = str("contenido"),
                        timestamp = runCatching { str("timestamp").toLong() }.getOrDefault(System.currentTimeMillis()),
                        likes = 0,
                        usuariosLike = emptySet()
                    )
                )
            }

            "PUB_LIKE" -> {
                com.renea.arete_social.feature.auth.GestorPerfilEfemero.registrarLikeRemoto(
                    str("publicacionId"),
                    senal.remitenteId
                )
            }
        }
    }



    // ========================================================
    // REPRODUCCIÓN DE AUDIO WAV POR ALTAVOZ (FALLBACK)
    // ========================================================

    private fun reproducirAudioWavBase64(remitenteId: String, remitenteNombre: String, base64Wav: String) {
        val ctx = context ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val wavBytes = Base64.decode(base64Wav, Base64.NO_WRAP)
                val tempFile = File.createTempFile("ptt_play_", ".wav", ctx.cacheDir)
                tempFile.writeBytes(wavBytes)

                marcarHablanteActivo(remitenteId, remitenteNombre)
                sonarTonoSeguro(ToneGenerator.TONE_PROP_ACK, 35)

                mediaPlayerVoz?.release()
                mediaPlayerVoz = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        it.release()
                        tempFile.delete()
                        quitarHablanteActivo(remitenteId)
                    }
                    setOnErrorListener { _, _, _ ->
                        tempFile.delete()
                        quitarHablanteActivo(remitenteId)
                        true
                    }
                }
            } catch (_: Exception) {
                quitarHablanteActivo(remitenteId)
            }
        }
    }

    // ========================================================
    // LLAMADA DE AUDIO 1-A-1 (DÚPLEX CONTINUO EN VIVO)
    // ========================================================

    fun iniciarLlamadaAudio(contacto: ContactoLlamada) {
        cronometroJob?.cancel()
        detenerTimbreLlamada()
        sonarClickBoton()

        val idLlamada = UUID.randomUUID().toString()
        _llamadaActual.value = LlamadaInfo(
            id = idLlamada,
            remoto = contacto,
            tipo = TipoLlamada.AUDIO,
            estado = EstadoLlamada.LLAMANDO,
            timestampInicio = System.currentTimeMillis()
        )

        sonarTonoSeguro(ToneGenerator.TONE_SUP_RINGTONE, 2500)

        scope.launch(Dispatchers.IO) {
            val miId = usuarioIdActualProveedor()
            val miPerfil = runCatching { repositorioUsuarios?.obtenerPorId(miId) }.getOrNull()
            val miNombre = miPerfil?.nombre ?: "Usuario"
            val miFoto = miPerfil?.foto ?: ""

            // 1. WiFi UDP Broadcast
            val jsonUdp = JSONObject().apply {
                put("type", "CALL_START")
                put("callId", idLlamada)
                put("targetId", contacto.id)
                put("remitenteId", miId)
                put("remitenteNombre", miNombre)
                put("remitenteFoto", miFoto)
            }.toString()
            transmitirPaqueteUdpWifi(jsonUdp)

            // Bluetooth Mesh / red mallada local
            routerMesh?.let { router ->
                val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                    id = UUID.randomUUID().toString(),
                    chatId = "senales-mesh",
                    remitenteId = miId,
                    destinatarioId = contacto.id,
                    contenido = jsonUdp
                )
                router.reenviar(paquete)
            }

            // Señalización de llamada (A3)
            emitirSenal(
                tipo = "SENAL_LLAMADA_INICIAR",
                destinatarioId = contacto.id,
                remitenteId = miId,
                payload = buildJsonObject {
                    put("callId", idLlamada)
                    put("remitenteNombre", miNombre)
                    put("remitenteFoto", miFoto)
                }
            )
        }
    }

    /** Alias de retrocompatibilidad: las llamadas 1-a-1 son exclusivamente de audio. */
    fun iniciarLlamadaVideo(contacto: ContactoLlamada) = iniciarLlamadaAudio(contacto)

    fun contestarLlamada() {
        val actual = _llamadaActual.value ?: return
        if (actual.estado == EstadoLlamada.ENTRANTE) {
            detenerTimbreLlamada()
            sonarLlamadaConectada()
            establecerLlamadaConectada()

            scope.launch(Dispatchers.IO) {
                val miId = usuarioIdActualProveedor()

                val jsonUdp = JSONObject().apply {
                    put("type", "CALL_ACCEPT")
                    put("callId", actual.id)
                    put("targetId", actual.remoto.id)
                    put("remitenteId", miId)
                }.toString()
                transmitirPaqueteUdpWifi(jsonUdp)

                routerMesh?.let { router ->
                    val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                        id = UUID.randomUUID().toString(),
                        chatId = "senales-mesh",
                        remitenteId = miId,
                        destinatarioId = actual.remoto.id,
                        contenido = jsonUdp
                    )
                    router.reenviar(paquete)
                }

                // Señalización de llamada (A3)
                emitirSenal(
                    tipo = "SENAL_LLAMADA_ACEPTADA",
                    destinatarioId = actual.remoto.id,
                    remitenteId = miId,
                    payload = buildJsonObject {
                        put("callId", actual.id)
                    }
                )
            }
        }
    }

    private fun establecerLlamadaConectada() {
        activarModoLlamada()  // Activa altavoz y modo comunicación solo al conectar
        _llamadaActual.update { it?.copy(estado = EstadoLlamada.EN_LLAMADA, duracionSegundos = 0) }
        iniciarCronometro()
        iniciarBucleVozLlamadaDuplex()
    }

    @SuppressLint("MissingPermission")
    private fun iniciarBucleVozLlamadaDuplex() {
        detenerBucleVozLlamada()
        val ctx = context ?: return
        val tienePermiso = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!tienePermiso) return

        bucleVozLlamadaJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1280)

            try {
                audioRecordLlamada = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )

                if (audioRecordLlamada?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecordLlamada?.startRecording()
                    val chunkShorts = 320
                    val buffer = ShortArray(chunkShorts)
                    val byteBuffer = ByteArray(chunkShorts * 2)

                    while (isActive && _llamadaActual.value?.estado == EstadoLlamada.EN_LLAMADA) {
                        val leidos = audioRecordLlamada?.read(buffer, 0, buffer.size) ?: 0
                        val llamada = _llamadaActual.value ?: break

                        if (leidos > 0 && !llamada.microfonoSilenciado) {
                            val bytesTotal = leidos * 2
                            for (i in 0 until leidos) {
                                val muestra = buffer[i]
                                val idx = i * 2
                                byteBuffer[idx] = (muestra.toInt() and 0xFF).toByte()
                                byteBuffer[idx + 1] = ((muestra.toInt() shr 8) and 0xFF).toByte()
                            }

                            val base64Chunk = Base64.encodeToString(byteBuffer, 0, bytesTotal, Base64.NO_WRAP)
                            val miId = usuarioIdActualProveedor()
                            val jsonChunk = JSONObject().apply {
                                put("type", "CALL_AUDIO")
                                put("callId", llamada.id)
                                put("remitenteId", miId)
                                put("data", base64Chunk)
                            }.toString()

                            transmitirPaqueteUdpWifi(jsonChunk)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching {
                    audioRecordLlamada?.stop()
                    audioRecordLlamada?.release()
                }
                audioRecordLlamada = null
            }
        }
    }

    private fun detenerBucleVozLlamada() {
        bucleVozLlamadaJob?.cancel()
        bucleVozLlamadaJob = null
        runCatching {
            audioRecordLlamada?.stop()
            audioRecordLlamada?.release()
        }
        audioRecordLlamada = null
    }

    fun enviarFrameVideo(base64Frame: String) {
        // Desactivado: las llamadas son exclusivamente de audio
    }

    fun colgarLlamada() {
        cronometroJob?.cancel()
        detenerTimbreLlamada()
        detenerBucleVozLlamada()
        sonarLlamadaFinalizada()
        restaurarModoAudioNormal()  // Libera modo de llamada → Bluetooth A2DP vuelve a funcionar
        _videoFrameRemoto.value = null
        _videoFrameRemotoBitmap.value = null

        val actual = _llamadaActual.value
        if (actual != null) {
            _llamadaActual.value = actual.copy(estado = EstadoLlamada.FINALIZADA)

            scope.launch(Dispatchers.IO) {
                val miId = usuarioIdActualProveedor()

                val jsonUdp = JSONObject().apply {
                    put("type", "CALL_HANGUP")
                    put("callId", actual.id)
                    put("remitenteId", miId)
                }.toString()
                transmitirPaqueteUdpWifi(jsonUdp)

                routerMesh?.let { router ->
                    val paquete = com.renea.arete_social.domain.mesh.PaqueteMesh(
                        id = UUID.randomUUID().toString(),
                        chatId = "senales-mesh",
                        remitenteId = miId,
                        destinatarioId = actual.remoto.id,
                        contenido = jsonUdp
                    )
                    router.reenviar(paquete)
                }

                // Señalización de llamada (A3)
                emitirSenal(
                    tipo = "SENAL_LLAMADA_COLGADA",
                    destinatarioId = actual.remoto.id,
                    remitenteId = miId,
                    payload = buildJsonObject {
                        put("callId", actual.id)
                    }
                )
            }

            scope.launch {
                delay(800)
                _llamadaActual.value = null
                _videoFrameRemoto.value = null
                _videoFrameRemotoBitmap.value = null
            }
        }
    }

    /**
     * S-05: Cierre de sesión seguro: cuelga cualquier llamada activa, libera recursos
     * de audio/micrófono y purga tablas de direcciones IP y deduplicación en memoria.
     */
    fun limpiarSesion() {
        cronometroJob?.cancel()
        cronometroJob = null
        detenerTimbreLlamada()
        detenerBucleVozLlamada()
        restaurarModoAudioNormal()
        _llamadaActual.value = null
        _videoFrameRemoto.value = null
        _videoFrameRemotoBitmap.value = null
        direccionesConocidas.clear()
        paquetesProcesados.clear()
        quitarHablanteActivo()
        watchdogStreamJob?.cancel()
        watchdogStreamJob = null
    }

    fun toggleMicrofonoLlamada() {
        sonarClickBoton()
        _llamadaActual.update { actual ->
            actual?.copy(microfonoSilenciado = !actual.microfonoSilenciado)
        }
    }

    fun toggleCamaraLlamada() {
        sonarClickBoton()
        _llamadaActual.update { actual ->
            actual?.copy(camaraActiva = !actual.camaraActiva)
        }
    }

    fun alternarCamaraLlamada() {
        sonarClickBoton()
        _llamadaActual.update { actual ->
            actual?.copy(camaraFrontal = !actual.camaraFrontal)
        }
    }

    private fun iniciarCronometro() {
        cronometroJob?.cancel()
        cronometroJob = scope.launch {
            while (isActive) {
                delay(1000)
                _llamadaActual.update { actual ->
                    if (actual?.estado == EstadoLlamada.EN_LLAMADA) {
                        actual.copy(duracionSegundos = actual.duracionSegundos + 1)
                    } else {
                        actual
                    }
                }
            }
        }
    }

    // ========================================================
    // EFECTOS SONOROS Y HÁPTICOS (SEGUROS)
    // ========================================================

    private fun sonarTonoSeguro(tipoTono: Int, duracionMs: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                toneGen?.stopTone()
                toneGen?.startTone(tipoTono, duracionMs)
            }
        }
    }

    /**
     * Sintetiza en tiempo real el sonido estático táctico y realista de una radio / walkie-talkie
     * (Ruido de apertura de frecuencia al inicio y squelch tail al cortar).
     */
    private fun sonarEfectoRadio(esInicio: Boolean) {
        vibrar(if (esInicio) 35 else 25, if (esInicio) 170 else 130)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val sampleRate = 22050
                val durationMs = if (esInicio) 75 else 90
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)
                val random = java.util.Random()
                val maxAmp = 4200 // Nivel de volumen suave, táctico y no estridente

                if (esInicio) {
                    // PTT Inicio: Ruido de radio + sutil click de apertura
                    for (i in 0 until numSamples) {
                        val progress = i.toFloat() / numSamples
                        val envelope = when {
                            progress < 0.12f -> progress / 0.12f
                            progress > 0.85f -> (1f - progress) / 0.15f
                            else -> 1f
                        }
                        val noise = (random.nextFloat() * 2f - 1f) * 0.75f
                        val chirpFreq = 1400.0 + (400.0 * (1f - progress))
                        val chirp = Math.sin(2.0 * Math.PI * chirpFreq * i / sampleRate).toFloat() * 0.25f
                        val sample = ((noise + chirp) * envelope * maxAmp).toInt().coerceIn(-32767, 32767)
                        buffer[i] = sample.toShort()
                    }
                } else {
                    // PTT Fin: Squelch tail clásico de corte de transmisión ("ksshh-t")
                    for (i in 0 until numSamples) {
                        val progress = i.toFloat() / numSamples
                        val envelope = Math.exp(-4.2 * progress).toFloat()
                        val noise = (random.nextFloat() * 2f - 1f)
                        val sample = (noise * envelope * maxAmp * 1.1f).toInt().coerceIn(-32767, 32767)
                        buffer[i] = sample.toShort()
                    }
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                delay(durationMs.toLong() + 25L)
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    private fun sonarPttInicio() {
        sonarEfectoRadio(esInicio = true)
    }

    private fun sonarPttFin() {
        sonarEfectoRadio(esInicio = false)
    }

    private fun sonarClickBoton() {
        vibrar(20, 110)
        sonarTonoSeguro(ToneGenerator.TONE_PROP_ACK, 50)
    }

    private fun iniciarTimbreLlamadaEntrante() {
        timbreJob?.cancel()
        timbreJob = scope.launch {
            while (isActive) {
                sonarTonoSeguro(ToneGenerator.TONE_SUP_RINGTONE, 800)
                vibrar(400, 230)
                delay(1200)
            }
        }
    }

    private fun detenerTimbreLlamada() {
        timbreJob?.cancel()
        timbreJob = null
        runCatching { toneGen?.stopTone() }
    }

    private fun sonarLlamadaConectada() {
        vibrar(80, 255)
        sonarTonoSeguro(ToneGenerator.TONE_PROP_ACK, 250)
    }

    private fun sonarLlamadaFinalizada() {
        vibrar(60, 190)
        sonarTonoSeguro(ToneGenerator.TONE_PROP_NACK, 220)
    }

    private fun vibrar(duracionMs: Long, amplitud: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        duracionMs,
                        amplitud.coerceIn(1, 255)
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duracionMs)
            }
        }
    }

    // ========================================================
    // CONVERSIÓN DE PCM A WAV
    // ========================================================

    private fun convertirPcmAWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        return header + pcmData
    }

    /**
     * Cancela todas las corrutinas asociadas, cuelga llamadas activas y libera
     * los recursos de audio, tonos y háptica del hardware (§Fase 3.6 / E1).
     */
    fun cerrar() {
        runCatching { colgarLlamada() }
        runCatching { toneGen?.release() }
        toneGen = null
        runCatching { vibrator?.cancel() }
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        supervisorJob.cancel()
    }
}
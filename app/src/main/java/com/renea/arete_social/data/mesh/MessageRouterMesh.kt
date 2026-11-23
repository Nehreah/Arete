package com.renea.arete_social.data.mesh

import android.util.Log
import com.renea.arete_social.data.crypto.EstadoVerificacionIdentidad
import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.data.crypto.PinesIdentidad
import com.renea.arete_social.data.local.outbox.GestorOutboxLocal
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.mesh.PeerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val TAG                          = "MessageRouter"
private const val VENTANA_EXPIRACION_DEDUP_MS  = 5 * 60_000L   // 5 minutos (caché memoria)
private const val MAX_EDAD_PAQUETE_MS          = 24 * 3600 * 1000L // 24 horas (límite anti-replay)
private const val INTERVALO_LIMPIEZA_MS        = 2 * 60_000L   // Limpieza periódica cada 2 min
private const val MAX_OUTBOX_SIZE              = 500           // Límite de la bandeja de salida fuera de línea

/**
 * Motor de enrutamiento para la red mallada (Mesh) de AntiRed.
 *
 * ## Responsabilidades
 * - **Deduplicación multinivel**: Caché en memoria + deduplicación persistente SQLite (anti-replay 24h).
 * - **Validación y decremento de TTL**: Máximo 7 saltos (6 retransmisiones).
 * - **Entrega local**: Mensajes dirigidos al usuario o canal actual.
 * - **Flooding controlado**: Jitter desincronizado, exclusión del peer emisor y relay probabilístico.
 * - **Mitigación TOFU y Anuncio IDENT v2**: Autenticación criptográfica de claves públicas.
 * - **Store-and-Forward**: Cola Outbox persistente ante desconexión de peers.
 */
class MessageRouterMesh(
    private val usuarioIdProveedor: () -> String?,
    private val gestorIdentidad: GestorIdentidadEcdsa? = null,
    private val pinesIdentidad: PinesIdentidad,
    private val resolverClaveRemota: (suspend (String) -> String?)? = null,
    private val e2eePubProveedor: (() -> String?)? = null,
    private val gestorOutboxLocal: GestorOutboxLocal? = null,
    private val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null
) : MessageRouter {

    private val paquetesVistos     = ConcurrentHashMap<String, Long>()
    private val mensajesPendientes = ConcurrentHashMap<String, PaqueteMesh>()
    private val peers              = ConcurrentHashMap<String, PeerConnection>()
    private data class EntradaMacUsuario(val usuarioId: String, val expiracionMs: Long)
    private val macParaUsuario     = ConcurrentHashMap<String, EntradaMacUsuario>()
    private val TTL_MAC_MS         = 60_000L // 1 minuto de expiración

    private class TokenBucket(var tokens: Double, var ultimo: Long)
    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val RATE_TOKENS_POR_SEG = 10.0
    private val BURST_TOKENS        = 30.0

    private val MAX_OUTBOX_PROPIOS = 300
    private val MAX_OUTBOX_AJENOS  = 200

    private fun permitir(origen: String): Boolean {
        val ahora = System.nanoTime()
        // H-13: Podar buckets antiguos si la tabla crece demasiado
        if (buckets.size > 150) {
            val limiteNano = ahora - 60_000_000_000L
            buckets.entries.removeIf { it.value.ultimo < limiteNano }
        }
        val b = buckets.getOrPut(origen) { TokenBucket(BURST_TOKENS, ahora) }
        synchronized(b) {
            b.tokens = minOf(BURST_TOKENS, b.tokens + (ahora - b.ultimo) / 1e9 * RATE_TOKENS_POR_SEG)
            b.ultimo = ahora
            if (b.tokens < 1.0) return false
            b.tokens -= 1.0
            return true
        }
    }

    override fun usuarioAutenticadoDe(mac: String): String? {
        val entrada = macParaUsuario[mac] ?: return null
        if (System.currentTimeMillis() > entrada.expiracionMs) {
            macParaUsuario.remove(mac)
            return null
        }
        return entrada.usuarioId
    }

    private val _paquetesEntrantes = MutableSharedFlow<PaqueteMesh>(
        replay              = 0,
        extraBufferCapacity = 256
    )

    /**
     * §2.1 — Alerta TOFU: se emite cuando la clave pública de un peer conocido cambia o entra
     * en conflicto con el pin previamente verificado. El valor es el `remitenteId`.
     */
    private val _alertaClaveTofu = MutableSharedFlow<String>(
        replay              = 1,
        extraBufferCapacity = 8
    )
    override val alertaClaveTofu: kotlinx.coroutines.flow.SharedFlow<String> = _alertaClaveTofu.asSharedFlow()

    /** Clave pública de firma más reciente que provocó alerta TOFU, para poder confiar en ella si el usuario lo confirma */
    private val clavesSospechosasRecientes = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _peersConectados = MutableStateFlow(0)
    override val peersConectados: StateFlow<Int> = _peersConectados.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(INTERVALO_LIMPIEZA_MS)
                limpiarPaquetesExpirados()
            }
        }
    }

    private fun coincidenIds(id1: String?, id2: String?): Boolean {
        if (id1 == null || id2 == null) return false
        val u1 = id1.trim().lowercase()
        val u2 = id2.trim().lowercase()
        if (u1 == u2 || u1.replace("-", "") == u2.replace("-", "")) return true
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

    override suspend fun enrutar(paquete: PaqueteMesh, origenMac: String?) {
        val miId = usuarioIdProveedor()
        val esPropio = coincidenIds(paquete.remitenteId, miId)

        // M-02: Rate limit por origen para mitigar inundación y DoS de verificación ECDSA
        if (origenMac != null && !permitir(origenMac)) {
            Log.w(TAG, "Rate-limit superado para peer ${anonimizarMac(origenMac)}. Paquete descartado.")
            return
        }

        // M-02: Clave de deduplicación compuesta (remitenteId:id) para evitar que terceros secuestren IDs
        val claveDedupe = "${paquete.remitenteId}:${paquete.id}"

        // 0. Consulta barata de duplicado en memoria
        if (paquetesVistos.containsKey(claveDedupe)) {
            Log.d(TAG, "[dedup-mem] Paquete $claveDedupe ya visto, descartado")
            return
        }

        // 0b. Consulta persistente SQLite (anti-replay ante reinicios y más allá de 5 min)
        if (gestorOutboxLocal?.esPaqueteVisto(claveDedupe) == true) {
            Log.d(TAG, "[dedup-sqlite] Paquete $claveDedupe ya visto en historial persistente, descartado")
            return
        }

        // Un paquete que llega POR LA RED diciendo ser mío es un eco o falsificación: nunca se firma ni procesa
        if (esPropio && origenMac != null) {
            Log.d(TAG, "Paquete propio ${paquete.id.take(8)} devuelto por la red, ignorado")
            return
        }

        // 1. Validación estructural de paquetes ajenos (rechaza futuros y edad > 24h)
        if (!esPropio && !estructuraValida(paquete)) {
            Log.w(TAG, "Paquete ${paquete.id.take(8)} con estructura o timestamp inválido, descartado")
            return
        }

        // 1b. Firma criptográfica (obligatoria en paquetes ajenos)
        var paqueteProcesado = paquete
        if (gestorIdentidad != null) {
            if (esPropio) {
                if (paquete.firma == null) {
                    paqueteProcesado = firmarPropio(paquete)
                }
            } else {
                val firma = paquete.firma
                val clavePublica = paquete.clavePublicaRemitente
                val valida = firma != null && clavePublica != null &&
                    gestorIdentidad.verificarFirma(
                        datos = paquete.datosFirmados(),
                        firmaBase64 = firma,
                        clavePublicaBase64 = clavePublica
                    )
                if (!valida) {
                    Log.w(TAG, "⚠️ Paquete ${paquete.id.take(8)} de ${anonimizarId(paquete.remitenteId)} sin firma válida. Descartado por seguridad.")
                    return
                }

                // TOFU: verificación contra PinesIdentidad local
                val pin = pinesIdentidad.obtener(paquete.remitenteId)
                val aceptar = when {
                    pin != null -> {
                        val estadoActual = pinesIdentidad.obtenerEstado(paquete.remitenteId)
                        if (pin == clavePublica) {
                            if (estadoActual == EstadoVerificacionIdentidad.ALERTA_CONFLICTO) {
                                // La clave coincide con el pin almacenado; restablecer estado para limpiar conflicto previo
                                pinesIdentidad.actualizarEstado(paquete.remitenteId, EstadoVerificacionIdentidad.NO_VERIFICADO)
                            }
                            val remota = resolverClaveRemota?.invoke(paquete.remitenteId)
                            if (remota != null && remota == clavePublica) {
                                pinesIdentidad.actualizarEstado(paquete.remitenteId, EstadoVerificacionIdentidad.VERIFICADO)
                            }
                            true
                        } else {
                            // S-02: La clave no coincide con el pin almacenado.
                            // NUNCA sobreescribir el pin automáticamente, ni para identidades NO_VERIFICADO ni VERIFICADO.
                            // Un cambio de clave requiere confirmación explícita del usuario vía confiarEnNuevaIdentidad.
                            Log.w(TAG, "Conflicto TOFU: Clave de ${anonimizarId(paquete.remitenteId)} discordante con pin fijado. Paquete rechazado.")
                            false
                        }
                    }
                    else -> {
                        // Primer contacto
                        val remota = resolverClaveRemota?.invoke(paquete.remitenteId)
                        if (remota != null) {
                            if (remota == clavePublica) {
                                pinesIdentidad.fijar(paquete.remitenteId, clavePublica, EstadoVerificacionIdentidad.VERIFICADO)
                                true
                            } else {
                                // Conflicto directo: clave ilegítima.
                                pinesIdentidad.fijar(paquete.remitenteId, remota, EstadoVerificacionIdentidad.VERIFICADO)
                                Log.w(TAG, "Conflicto TOFU primer contacto: paquete con clave atacante para ${anonimizarId(paquete.remitenteId)}. Fijando clave verificada.")
                                false
                            }
                        } else {
                            // Primer contacto offline: aceptar provisionalmente como NO_VERIFICADO
                            pinesIdentidad.fijar(paquete.remitenteId, clavePublica, EstadoVerificacionIdentidad.NO_VERIFICADO)
                            true
                        }
                    }
                }

                if (!aceptar) {
                    Log.w(TAG, "⚠️ Clave no coincide con la identidad fijada de ${paquete.remitenteId}. Descartado por TOFU.")
                    clavesSospechosasRecientes[paquete.remitenteId] = clavePublica
                    if (pinesIdentidad.obtenerEstado(paquete.remitenteId) != EstadoVerificacionIdentidad.VERIFICADO) {
                        pinesIdentidad.actualizarEstado(paquete.remitenteId, EstadoVerificacionIdentidad.ALERTA_CONFLICTO)
                    }
                    scope.launch { _alertaClaveTofu.emit(paquete.remitenteId) }
                    return
                }

                // H-04 + H-GPS + S-01: Asignar MAC a remitente verificado SOLO si:
                // 1. El paquete viene directo (saltosRealizados == 0).
                // 2. El paquete está explícitamente dirigido a este nodo (destinatarioId == miId).
                // 3. El timestamp es fresco (<= 60s), impidiendo el replay de paquetes antiguos o de chats grupales.
                val ahoraMs = System.currentTimeMillis()
                val esDirectoYParaMi = coincidenIds(paquete.destinatarioId, miId)
                val esTimestampFresco = Math.abs(ahoraMs - paquete.timestamp) <= 60_000L

                if (origenMac != null && paquete.saltosRealizados == 0 && esDirectoYParaMi && esTimestampFresco) {
                    val exp = ahoraMs + TTL_MAC_MS
                    macParaUsuario[origenMac] = EntradaMacUsuario(paquete.remitenteId, exp)
                }
            }
        }

        // 1c. Marcar visto atómicamente en memoria y persistir en SQLite
        if (paquetesVistos.putIfAbsent(claveDedupe, System.currentTimeMillis()) != null) return
        gestorOutboxLocal?.marcarPaqueteVisto(claveDedupe, System.currentTimeMillis())

        // 2. Entrega local: para mí, o difusión (grupal/lugar/evento).
        val esParaMi = paqueteProcesado.destinatarioId == null || coincidenIds(paqueteProcesado.destinatarioId, miId)
        if (esParaMi && !paqueteProcesado.contenido.startsWith("[PING_DIRECTO")) {
            _paquetesEntrantes.emit(paqueteProcesado)
        }

        // 3. Retransmitir si quedan saltos (TTL > 1)
        val ttlEntrante = paqueteProcesado.ttl.coerceAtMost(PaqueteMesh.TTL_POR_DEFECTO)
        if (ttlEntrante > 1 && !esPropio) {
            val paqueteRetransmision = paqueteProcesado.copy(
                ttl              = ttlEntrante - 1,
                saltosRealizados = paqueteProcesado.saltosRealizados + 1
            )
            reenviarConExclusion(paqueteRetransmision, origenPeerId = origenMac)
        }
    }

    private fun estructuraValida(p: PaqueteMesh): Boolean {
        val ahora = System.currentTimeMillis()
        val esTimestampValido = p.timestamp <= ahora + PaqueteMesh.TOLERANCIA_FUTURO_MS &&
                p.timestamp >= ahora - MAX_EDAD_PAQUETE_MS
        return p.id.isNotBlank() && p.id.length <= 64 &&
            p.chatId.isNotBlank() && p.chatId.length <= 128 &&
            p.remitenteId.isNotBlank() && p.remitenteId.length <= 132 &&
            (p.destinatarioId == null || p.destinatarioId.length <= 132) &&
            p.contenido.length <= PaqueteMesh.MAX_CONTENIDO_MESH_CHARS &&
            esTimestampValido
    }

    override fun firmarPaquete(paquete: PaqueteMesh): PaqueteMesh = firmarPropio(paquete)

    private fun firmarPropio(paquete: PaqueteMesh): PaqueteMesh {
        val gi = gestorIdentidad ?: return paquete
        val firma = gi.firmar(paquete.datosFirmados())
        val pub = gi.obtenerClavePublicaBase64()
        return if (firma != null && pub != null) {
            paquete.copy(firma = firma, clavePublicaRemitente = pub)
        } else {
            Log.e(TAG, "No se pudo firmar el paquete ${paquete.id.take(8)}: los demás nodos lo descartarán")
            paquete
        }
    }

    override suspend fun enviarADestino(destinatarioId: String, paquete: PaqueteMesh): Boolean {
        if (!com.renea.arete_social.domain.model.FeatureFlags.SYNC_ENVIO_DIRIGIDO) {
            return false // Flag inactivo: fallback al comportamiento estándar
        }
        val miId = usuarioIdProveedor()
        var paqueteAEnviar = paquete
        if (coincidenIds(paquete.remitenteId, miId) && paquete.firma == null && gestorIdentidad != null) {
            paqueteAEnviar = firmarPropio(paquete)
        }

        // 1. Buscar por MAC mapeada autenticada
        val macEncontrada = macParaUsuario.entries.firstOrNull { entrada ->
            coincidenIds(entrada.value.usuarioId, destinatarioId) && System.currentTimeMillis() <= entrada.value.expiracionMs
        }?.key

        val peersCandidatos = if (macEncontrada != null) {
            peers.values.filter { it.dispositivo.macAddress == macEncontrada && it.estaConectado() }
        } else {
            peers.values.filter {
                (coincidenIds(it.dispositivo.usuarioId, destinatarioId) || coincidenIds(it.dispositivo.id, destinatarioId)) && it.estaConectado()
            }
        }

        for (peerDirecto in peersCandidatos) {
            val exito = runCatching { peerDirecto.enviar(paqueteAEnviar) }.getOrDefault(false)
            if (exito) {
                Log.d(TAG, "SYNC: Paquete dirigido ${paqueteAEnviar.id.take(8)} entregado a $destinatarioId vía ${peerDirecto.dispositivo.id}")
                return true
            }
        }
        return false
    }

    override suspend fun reenviar(paquete: PaqueteMesh) {
        reenviarConExclusion(paquete, origenPeerId = null)
    }

    suspend fun reenviarConExclusion(paquete: PaqueteMesh, origenPeerId: String? = null) {
        var paqueteAEnviar = paquete
        val miId = usuarioIdProveedor()
        if (coincidenIds(paquete.remitenteId, miId) && paquete.firma == null && gestorIdentidad != null) {
            paqueteAEnviar = firmarPropio(paquete)
        }

        // Relay probabilístico por densidad de red
        val probabilidad = when (peers.size) {
            in 0..3  -> 1.0
            in 4..8  -> 0.7
            in 9..15 -> 0.45
            else     -> 0.25
        }

        if (origenPeerId != null && Random.nextDouble() > probabilidad) {
            Log.d(TAG, "Paquete ${paqueteAEnviar.id.take(8)} descartado por relay probabilístico (densidad=${peers.size})")
            return
        }

        // Jitter aleatorio (20..120 ms) para desincronizar retransmisiones concurrentes
        if (origenPeerId != null) {
            delay(Random.nextLong(20, 120))
        }

        var enviados = 0
        for ((clave, peer) in peers) {
            if (clave != origenPeerId) {
                if (runCatching { peer.enviar(paqueteAEnviar) }.getOrDefault(false)) {
                    enviados++
                }
            }
        }

        if (enviados > 0) {
            Log.d(TAG, "Paquete ${paqueteAEnviar.id.take(8)} reenviado a $enviados peer(s) (TTL=${paqueteAEnviar.ttl})")
            mensajesPendientes.remove(paqueteAEnviar.id)
        } else {
            // M-02: Store-and-Forward separado para mensajes propios vs ajenos
            val miId = usuarioIdProveedor()
            val esPropio = coincidenIds(paqueteAEnviar.remitenteId, miId)
            val maxPermitido = if (esPropio) MAX_OUTBOX_PROPIOS else MAX_OUTBOX_AJENOS
            val pendientesTipo = mensajesPendientes.values.count { coincidenIds(it.remitenteId, miId) == esPropio }
            if (pendientesTipo < maxPermitido) {
                mensajesPendientes[paqueteAEnviar.id] = paqueteAEnviar
                Log.d(TAG, "Paquete ${paqueteAEnviar.id.take(8)} guardado en Outbox (sin peers conectados)")
            }
        }
    }

    override fun paquetesEntrantes(chatId: String): Flow<PaqueteMesh> =
        _paquetesEntrantes.filter { it.chatId == chatId }

    override fun todosLosPaquetesEntrantes(): Flow<PaqueteMesh> =
        _paquetesEntrantes.asSharedFlow()

    override fun paquetesParaMi(miId: String): Flow<PaqueteMesh> =
        _paquetesEntrantes.filter { paquete ->
            (coincidenIds(paquete.destinatarioId, miId) || paquete.destinatarioId == null) &&
            paquete.chatId.startsWith("local-dm-")
        }

    override fun registrarPeer(peer: PeerConnection) {
        val clave = peer.dispositivo.id
        peers[clave] = peer
        _peersConectados.value = peers.size
        Log.d(TAG, "Peer registrado: $clave (total: ${peers.size})")

        // Anuncio de identidad IDENT v2: autentica la clave E2EE dentro del contenido firmado con ECDSA
        anunciarIdentidad()

        // Disparar sincronización de mensajes pendientes en Outbox al conectar nuevo peer
        if (mensajesPendientes.isNotEmpty()) {
            scope.launch {
                val pendientes = mensajesPendientes.values.toList()
                Log.d(TAG, "Sincronizando ${pendientes.size} paquete(s) del Outbox con el nuevo peer $clave")
                for (paquete in pendientes) {
                    if (peer.estaConectado()) {
                        if (runCatching { peer.enviar(paquete) }.getOrDefault(false)) {
                            mensajesPendientes.remove(paquete.id)
                        }
                    }
                }
            }
        }
    }

    /** Emite un paquete IDENT v2 firmado para anunciar claves públicas de firma y E2EE en la malla. */
    fun anunciarIdentidad() {
        val miId = usuarioIdProveedor()
        val pubFirma = gestorIdentidad?.obtenerClavePublicaBase64()
        val pubE2ee = e2eePubProveedor?.invoke()

        if (miId != null && pubFirma != null && pubE2ee != null) {
            scope.launch {
                val paqueteIdent = PaqueteMesh(
                    id = "ident-${java.util.UUID.randomUUID()}",
                    chatId = com.renea.arete_social.domain.model.ProtocoloCanalesMesh.CANAL_IDENT_SYNC,
                    remitenteId = miId,
                    destinatarioId = null,
                    contenido = "[IDENT:v2:$pubFirma:$pubE2ee]",
                    timestamp = System.currentTimeMillis()
                )
                reenviar(paqueteIdent)
            }
        }
    }

    override fun tieneAlertaTofu(remitenteId: String): Boolean {
        if (clavesSospechosasRecientes.keys.any { coincidenIds(it, remitenteId) }) return true
        val estado = pinesIdentidad.obtenerEstado(remitenteId)
        return estado == EstadoVerificacionIdentidad.ALERTA_CONFLICTO
    }

    override fun confiarEnNuevaIdentidad(remitenteId: String) {
        val claveDirecta = clavesSospechosasRecientes.remove(remitenteId)
        val claveCoincidente = if (claveDirecta == null) {
            val k = clavesSospechosasRecientes.keys.firstOrNull { coincidenIds(it, remitenteId) }
            if (k != null) clavesSospechosasRecientes.remove(k) else null
        } else null
        val nuevaClave = claveDirecta ?: claveCoincidente
        if (nuevaClave != null) {
            pinesIdentidad.fijar(remitenteId, nuevaClave, EstadoVerificacionIdentidad.NO_VERIFICADO)
            Log.i(TAG, "Nueva clave de identidad fijada para $remitenteId tras confirmación del usuario.")
        } else {
            pinesIdentidad.borrar(remitenteId)
            Log.i(TAG, "Pin de identidad eliminado para $remitenteId para re-pinado en próximo paquete.")
        }
        anunciarIdentidad()
    }

    override fun reiniciar() {
        paquetesVistos.clear()
        mensajesPendientes.clear()
        macParaUsuario.clear()
        buckets.clear()
    }

    override fun removerPeer(dispositivoId: String) {
        val removido = peers.remove(dispositivoId)
        if (removido == null) {
            peers.entries.removeIf { it.value.dispositivo.macAddress == dispositivoId || it.key == dispositivoId }
        }
        _peersConectados.value = peers.size
        Log.d(TAG, "Peer removido: $dispositivoId (total: ${peers.size})")
    }

    private fun limpiarPaquetesExpirados() {
        val ahora = System.currentTimeMillis()
        val eliminados = paquetesVistos.entries.count { ahora - it.value > VENTANA_EXPIRACION_DEDUP_MS }
        paquetesVistos.entries.removeIf { ahora - it.value > VENTANA_EXPIRACION_DEDUP_MS }
        if (eliminados > 0) {
            Log.d(TAG, "Dedup limpieza: $eliminados entradas expiradas eliminadas")
        }
    }

    private fun anonimizarMac(mac: String): String {
        val partes = mac.split(":")
        return if (partes.size >= 6) "${partes[0]}:${partes[1]}:**:**:**:${partes[5]}" else "***"
    }

    private fun anonimizarId(id: String): String {
        return if (id.length > 8) "${id.take(4)}...${id.takeLast(4)}" else id.take(4)
    }
}

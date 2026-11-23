package com.renea.arete_social.data.mesh

import android.util.Log
import com.renea.arete_social.domain.descubrimiento.DURACION_DESCUBRIMIENTO_MS
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.ProtocoloPaseBle
import com.renea.arete_social.domain.util.Reloj
import com.renea.arete_social.feature.descubrimiento.ResultadoPase
import com.renea.arete_social.feature.descubrimiento.ServicioPases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GestorPasesBle"

/** Mínimo entre dos ofertas a la misma persona (evita repetir si el enlace se corta y se vuelve a levantar). */
private const val INTERVALO_MIN_OFERTA_MS = 60_000L

/** Mínimo entre dos respuestas a SOLICITUDES explícitas de la misma persona (solo frena el spam de toques). */
private const val INTERVALO_MIN_SOLICITUD_MS = 5_000L

/**
 * Comparte por Bluetooth lo que descubriste con quien se cruza contigo, y recibe lo que otros comparten.
 *
 *  - Los anuncios de identidad no comparten contenido. La sincronización solo comienza tras una solicitud
 *    explícita del usuario que toca un Michi con Parche activo.
 *  - Como EMISOR: solo si el dueño activó Parche y recibió una solicitud. Con Parche activo la respuesta es
 *    AUTOMÁTICA: el dueño no confirma nada, activar Parche ya es su consentimiento para todo el que pida
 *    sincronizar. Lo que se ofrece lo decide
 *    [ServicioPases.crear] (solo descubierto físicamente y público; nunca lo recibido por pase ni por el Súper).
 *    No se repite lo ya ofrecido a la misma persona mientras siga vigente (24 h).
 *  - Como RECEPTOR: [ServicioPases.aceptar] valida todo; aquí solo llegan paquetes ya verificados por el router.
 *  - TTL 1: el pase nunca se retransmite; solo entre dispositivos directamente conectados.
 *
 * Mismo patrón que GestorPerfilBle: canal propio ([ProtocoloPaseBle.CHAT_ID]) que la mensajería ignora.
 */
class GestorPasesBle(
    private val router: MessageRouter,
    private val servicioPases: ServicioPases,
    private val miIdProveedor: () -> String?,
    private val compartirActivado: () -> Boolean,
    private val reloj: Reloj = Reloj.Sistema,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var job: Job? = null
    private companion object { const val SOLICITUD_SINCRONIZACION = "[PARCHE_SYNC_REQUEST:v1]" }

    /** receptor → (clave del elemento → cuándo se le ofreció). Solo en memoria. */
    private val ofrecido = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    private val ultimaOferta = ConcurrentHashMap<String, Long>()

    fun iniciar() {
        if (job?.isActive == true) return
        job = scope.launch {
            router.todosLosPaquetesEntrantes().collect { paquete ->
                runCatching { procesar(paquete) }.onFailure { avisar("Paquete ignorado: ${it.message}") }
            }
        }
    }

    fun detener() {
        job?.cancel()
        job = null
        reiniciar()
    }

    /** Olvida lo ofrecido (cerrar sesión). */
    fun reiniciar() {
        ofrecido.clear()
        ultimaOferta.clear()
    }

    // runCatching: en pruebas JVM android.util.Log no existe.
    private fun avisar(mensaje: String) {
        runCatching { Log.w(TAG, mensaje) }
    }

    private suspend fun procesar(p: PaqueteMesh) {
        val miId = miIdProveedor() ?: return
        if (p.remitenteId == miId) return // eco de mis propios paquetes

        if (p.chatId == ProtocoloPaseBle.CHAT_ID) {
            if (p.destinatarioId != miId) return
            if (p.contenido == SOLICITUD_SINCRONIZACION) {
                // Parche activo = consentimiento permanente: se responde sola, sin preguntar al dueño.
                if (compartirActivado()) ofrecer(miId, p.remitenteId, solicitado = true)
            } else {
                recibir(p, miId)
            }
        }
    }

    /** Envía una solicitud explícita de sincronización al dueño de un Michi con Parche activo. */
    suspend fun solicitarSincronizacion(usuarioId: String): Boolean {
        val miId = miIdProveedor() ?: return false
        if (usuarioId.isBlank() || usuarioId == miId) return false
        val ahora = reloj.ahora()
        val paquete = PaqueteMesh(
            id = "solicitud-parche-${UUID.randomUUID()}",
            chatId = ProtocoloPaseBle.CHAT_ID,
            remitenteId = miId,
            destinatarioId = usuarioId,
            contenido = SOLICITUD_SINCRONIZACION,
            ttl = 1,
            timestamp = ahora
        )
        return runCatching {
            router.reenviar(paquete)
            router.enrutar(paquete)
            true
        }.getOrDefault(false)
    }

    private suspend fun recibir(p: PaqueteMesh, miId: String) {
        if (p.destinatarioId != miId) return // dirigido a otra persona
        if (p.contenido == SOLICITUD_SINCRONIZACION) {
            if (compartirActivado()) ofrecer(miId, p.remitenteId, solicitado = true)
            return
        }
        val pase = ProtocoloPaseBle.decodificar(p.contenido) ?: return
        when (val r = servicioPases.aceptar(pase, remitenteTransporte = p.remitenteId, miId = miId)) {
            is ResultadoPase.Aceptado -> runCatching { Log.d(TAG, "Pase de ${p.remitenteId}: ${r.nuevos.size} nuevo(s), ${r.ignorados} ignorado(s)") }
            is ResultadoPase.Rechazado -> avisar("Pase de ${p.remitenteId} rechazado: ${r.motivo}")
        }
    }

    /**
     * Ofrece a [vecinoId] lo que todavía no se le ha ofrecido. Visible para pruebas.
     *
     * [solicitado] = la persona pidió sincronizar a propósito: se responde enseguida y con todo lo compartible
     * (si el pase anterior se perdió por el camino, volver a pedir lo recupera; quien recibe ignora lo que ya ve).
     */
    internal suspend fun ofrecer(miId: String, vecinoId: String, solicitado: Boolean = false) {
        if (!compartirActivado()) return
        val ahora = reloj.ahora()
        val previa = ultimaOferta[vecinoId]
        val intervalo = if (solicitado) INTERVALO_MIN_SOLICITUD_MS else INTERVALO_MIN_OFERTA_MS
        if (previa != null && ahora - previa < intervalo) return

        val yaOfrecidos = ofrecido.getOrPut(vecinoId) { ConcurrentHashMap() }
        yaOfrecidos.entries.removeIf { ahora - it.value >= DURACION_DESCUBRIMIENTO_MS } // lo vencido puede volver a ofrecerse

        val excluidos = if (solicitado) emptySet() else yaOfrecidos.keys.toSet()
        val pase = servicioPases.crear(miId, vecinoId, excluidos) ?: return
        ultimaOferta[vecinoId] = ahora
        pase.entidades.forEach { yaOfrecidos[it.clave] = ahora }

        val paquete = PaqueteMesh(
            id = "pase-${UUID.randomUUID()}",
            chatId = ProtocoloPaseBle.CHAT_ID,
            remitenteId = miId,
            destinatarioId = vecinoId,
            contenido = ProtocoloPaseBle.codificar(pase),
            ttl = 1,
            timestamp = ahora
        )
        // Mismo orden que la mensajería: reenviar() firma y difunde; enrutar() lo marca como visto.
        router.reenviar(paquete)
        router.enrutar(paquete)
    }
}

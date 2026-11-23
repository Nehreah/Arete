package com.renea.arete_social.feature.miausnake.social

import java.util.concurrent.CancellationException

/** Un mensaje de reto pendiente de entregar a un par (envío diferido: el par puede no estar cerca ahora). */
data class OutboxItem(
    val messageId: String,
    val peerId: String,
    /** Texto del reto: un código `MIAU1-…` (CHALLENGE_CREATE) o un paquete `MIAUR1:…` (CHALLENGE_RESULT). */
    val payload: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val attempts: Int,
    val lastAttemptMs: Long
)

/**
 * Cola de salida IDEMPOTENTE y que EXPIRA. Inmutable: cada operación devuelve una cola nueva.
 *  - Encolar el mismo mensaje al mismo par dos veces no lo duplica (mismo [OutboxItem.messageId]).
 *  - Cada mensaje caduca a los [DEFAULT_TTL_MS]; los caducados se descartan en [expire].
 *  - Reintentos con espera creciente (1 min, 2, 4… hasta 6 h) y como máximo [MAX_ATTEMPTS].
 * Las horas son solo para caducar y espaciar reintentos: nunca deciden nada de la partida.
 */
class DeferredOutbox private constructor(val items: List<OutboxItem>) {

    fun contains(messageId: String): Boolean = items.any { it.messageId == messageId }

    /** Mismo (par, contenido) = mismo mensaje. Peer o payload inválidos: no se encola. */
    fun enqueue(peerId: String, payload: String, nowMs: Long, ttlMs: Long = DEFAULT_TTL_MS): DeferredOutbox {
        if (!PEER.matches(peerId) || payload.isEmpty() || payload.length > MAX_PAYLOAD_CHARS) return this
        if (payload.any { it == '|' || it.isWhitespace() }) return this
        val id = messageIdOf(peerId, payload)
        if (contains(id)) return this
        val ttl = ttlMs.coerceIn(1L, MAX_TTL_MS)
        val item = OutboxItem(id, peerId, payload, nowMs, nowMs + ttl, 0, 0L)
        return DeferredOutbox((items + item).takeLast(MAX_ITEMS))
    }

    /** Pendientes que toca intentar ahora (sin caducar, con la espera cumplida y sin pasarse de intentos). */
    fun due(nowMs: Long): List<OutboxItem> = items.filter {
        nowMs < it.expiresAtMs && it.attempts < MAX_ATTEMPTS &&
            (it.attempts == 0 || nowMs - it.lastAttemptMs >= retryDelayMs(it.attempts))
    }

    fun attempted(messageId: String, nowMs: Long): DeferredOutbox = DeferredOutbox(
        items.map { if (it.messageId == messageId) it.copy(attempts = it.attempts + 1, lastAttemptMs = nowMs) else it }
    )

    fun delivered(messageId: String): DeferredOutbox = DeferredOutbox(items.filterNot { it.messageId == messageId })

    /** Quita lo caducado y lo que agotó sus intentos. */
    fun expire(nowMs: Long): DeferredOutbox =
        DeferredOutbox(items.filter { nowMs < it.expiresAtMs && it.attempts < MAX_ATTEMPTS })

    fun toText(): String = items.joinToString("\n") {
        listOf(it.messageId, it.peerId, it.createdAtMs, it.expiresAtMs, it.attempts, it.lastAttemptMs, it.payload)
            .joinToString("|")
    }

    companion object {
        const val MAX_ITEMS = 50
        const val MAX_ATTEMPTS = 20
        const val MAX_PAYLOAD_CHARS = 16_000
        const val DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private val PEER = Regex("[A-Za-z0-9:_.-]{1,64}")
        val EMPTY = DeferredOutbox(emptyList())

        fun messageIdOf(peerId: String, payload: String): String = Ids.of("miausnake-challenge", peerId, payload)

        /** 1 min · 2 · 4 · … con tope de 6 h. */
        fun retryDelayMs(attempts: Int): Long =
            minOf(60_000L shl minOf(maxOf(attempts - 1, 0), 10), 6L * 60 * 60 * 1000)

        fun fromText(text: String?): DeferredOutbox {
            if (text.isNullOrBlank()) return EMPTY
            val out = ArrayList<OutboxItem>()
            val seen = HashSet<String>()
            for (line in text.lineSequence()) {
                val p = line.split('|')
                if (p.size != 7) continue
                val created = p[2].toLongOrNull() ?: continue
                val expires = p[3].toLongOrNull() ?: continue
                val attempts = p[4].toIntOrNull()?.takeIf { it >= 0 } ?: continue
                val last = p[5].toLongOrNull() ?: continue
                val peer = p[1]
                val payload = p[6]
                if (!PEER.matches(peer) || payload.isEmpty() || payload.length > MAX_PAYLOAD_CHARS) continue
                if (p[0] != messageIdOf(peer, payload) || !seen.add(p[0])) continue
                out.add(OutboxItem(p[0], peer, payload, created, expires, attempts, last))
            }
            return DeferredOutbox(out.takeLast(MAX_ITEMS))
        }
    }
}

/**
 * Ids de mensajes ya recibidos, para que un reintento del par no cree el mismo reto dos veces.
 * Acotado en cantidad y en edad (más que el TTL de la cola de salida).
 */
class SeenIds private constructor(private val seen: Map<String, Long>) {
    fun contains(messageId: String): Boolean = messageId in seen

    /** (¿es nuevo?, estado nuevo). El primero es true solo la primera vez. */
    fun accept(messageId: String, nowMs: Long): Pair<Boolean, SeenIds> {
        if (!Ids.isId(messageId)) return false to this
        if (messageId in seen) return false to this
        val kept = seen.entries.filter { nowMs - it.value < MAX_AGE_MS }.sortedBy { it.value }
        val merged = (kept.map { it.key to it.value } + (messageId to nowMs)).takeLast(MAX_IDS)
        return true to SeenIds(merged.toMap())
    }

    fun toText(): String = seen.entries.sortedBy { it.value }.joinToString("\n") { "${it.key}=${it.value}" }

    companion object {
        const val MAX_IDS = 200
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        val EMPTY = SeenIds(emptyMap())

        fun fromText(text: String?): SeenIds {
            if (text.isNullOrBlank()) return EMPTY
            val m = LinkedHashMap<String, Long>()
            for (line in text.lineSequence()) {
                val i = line.indexOf('=')
                if (i <= 0) continue
                val id = line.substring(0, i)
                val at = line.substring(i + 1).toLongOrNull() ?: continue
                if (Ids.isId(id)) m[id] = at
            }
            return SeenIds(m.entries.sortedBy { it.value }.takeLast(MAX_IDS).associate { it.key to it.value })
        }
    }
}

enum class SendOutcome { DELIVERED, RETRY_LATER, REJECTED }

/**
 * Transporte de retos diferidos. La implementación real es un adaptador sobre la malla de Aréte
 * (MessageRouterMesh / RepositorioMensajesHibrido): Miau Snake no abre su propia pila de red.
 */
interface ChallengeTransport {
    suspend fun send(peerId: String, messageId: String, payload: String): SendOutcome
}

/** Sin malla disponible: todo queda pendiente (y expira). Los retos siguen compartiéndose por QR/texto/archivo. */
object NoTransport : ChallengeTransport {
    override suspend fun send(peerId: String, messageId: String, payload: String): SendOutcome =
        SendOutcome.RETRY_LATER
}

data class FlushReport(val delivered: Int, val retried: Int, val dropped: Int, val expired: Int)

/** Vacía la cola contra un transporte. Un fallo del transporte jamás pierde el mensaje: se reintenta. */
class OutboxPump(private val transport: ChallengeTransport) {
    suspend fun flush(outbox: DeferredOutbox, nowMs: Long): Pair<DeferredOutbox, FlushReport> {
        var q = outbox.expire(nowMs)
        val expired = outbox.items.size - q.items.size
        var delivered = 0
        var retried = 0
        var dropped = 0
        for (item in q.due(nowMs)) {
            val r = try {
                transport.send(item.peerId, item.messageId, item.payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SendOutcome.RETRY_LATER
            }
            when (r) {
                SendOutcome.DELIVERED -> { q = q.delivered(item.messageId); delivered++ }
                SendOutcome.REJECTED -> { q = q.delivered(item.messageId); dropped++ }
                SendOutcome.RETRY_LATER -> { q = q.attempted(item.messageId, nowMs); retried++ }
            }
        }
        return q to FlushReport(delivered, retried, dropped, expired)
    }
}

package com.renea.arete_social.feature.miausnake.match

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Umbrales para habilitar la partida cercana (plan V9, Fase 6). Son valores de PARTIDA, pensados para
 * un duelo por resultados (mensajes pequeños de control + un resultado de unos KB), no para sincronizar
 * ticks. Hay que ajustarlos con mediciones en teléfonos reales: ver docs/miau-snake/FASE_6_ESTADO.md.
 */
data class SpikeThresholds(
    val pings: Int = 40,
    val pingIntervalMs: Long = 250L,
    val maxMedianRttMs: Long = 800L,
    val maxP95RttMs: Long = 2_000L,
    val minDeliveryPercent: Int = 90,
    val bigPayloadChars: Int = 4_000,
    val bigProbes: Int = 3,
    val bigMustPass: Int = 3,
    val bigTimeoutMs: Long = 15_000L,
    val stragglersWaitMs: Long = 3_000L
)

/** Resultado medido de una prueba de transporte entre dos teléfonos. */
data class SpikeReport(
    val atMs: Long,
    val sent: Int,
    val received: Int,
    val medianMs: Long,
    val p95Ms: Long,
    val jitterMs: Long,
    val bigSent: Int,
    val bigOk: Int,
    val passed: Boolean,
    val failures: List<String>
) {
    val deliveryPercent: Int get() = if (sent == 0) 0 else received * 100 / sent

    fun toText(): String =
        listOf("v1", atMs, sent, received, medianMs, p95Ms, jitterMs, bigSent, bigOk, if (passed) 1 else 0).joinToString("|")

    fun summary(): String =
        "Entrega $received/$sent ($deliveryPercent %) · RTT mediana $medianMs ms, p95 $p95Ms ms, jitter $jitterMs ms · " +
            "paquetes grandes $bigOk/$bigSent · " + if (passed) "APTO" else "NO APTO: " + failures.joinToString("; ")

    companion object {
        fun fromText(text: String?): SpikeReport? {
            val p = text?.split('|') ?: return null
            if (p.size != 10 || p[0] != "v1") return null
            val n = p.drop(1).map { it.toLongOrNull() ?: return null }
            if (n.any { it < 0 } || n[1] > 10_000 || n[2] > n[1] || n[6] > 100 || n[7] > n[6]) return null
            val passed = n[8] == 1L
            return SpikeReport(n[0], n[1].toInt(), n[2].toInt(), n[3], n[4], n[5], n[6].toInt(), n[7].toInt(), passed, emptyList())
        }

        fun evaluate(
            now: Long, sent: Int, rtts: List<Long>, bigSent: Int, bigOk: Int, t: SpikeThresholds = SpikeThresholds()
        ): SpikeReport {
            val s = rtts.sorted()
            val median = if (s.isEmpty()) 0L else s[(s.size - 1) / 2]
            val p95 = if (s.isEmpty()) 0L else s[maxOf(0, kotlin.math.ceil(s.size * 0.95).toInt() - 1)]
            val why = ArrayList<String>()
            val pct = if (sent == 0) 0 else rtts.size * 100 / sent
            if (sent < t.pings) why += "se enviaron menos pruebas de las necesarias"
            if (pct < t.minDeliveryPercent) why += "entrega $pct % (mínimo ${t.minDeliveryPercent} %)"
            if (s.isEmpty() || median > t.maxMedianRttMs) why += "RTT mediana $median ms (máximo ${t.maxMedianRttMs})"
            if (s.isEmpty() || p95 > t.maxP95RttMs) why += "RTT p95 $p95 ms (máximo ${t.maxP95RttMs})"
            if (bigOk < t.bigMustPass) why += "solo $bigOk de $bigSent paquetes grandes llegaron intactos"
            return SpikeReport(now, sent, rtts.size, median, p95, p95 - median, bigSent, bigOk, why.isEmpty(), why)
        }
    }
}

/** La función solo se abre si la última medición es APTA y reciente. Nunca por suposición. */
object NearbyGate {
    const val VALID_MS = 14L * 24 * 60 * 60 * 1000

    fun isOpen(report: SpikeReport?, now: Long): Boolean =
        report != null && report.passed && now - report.atMs in 0..VALID_MS
}

/**
 * Mide el transporte REAL (el que use la malla en ese momento: BLE o Wi‑Fi) contra un teléfono que esté
 * en la pantalla de cercanía respondiendo ecos: RTT, jitter, pérdida y paquetes grandes con fragmentación.
 * Lógica pura: se prueba en JVM con un transporte falso.
 */
class TransportSpike(
    private val transport: MatchTransport,
    private val me: String,
    private val thresholds: SpikeThresholds = SpikeThresholds(),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val wallMs: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String
) {
    /** [onProgress] recibe (enviados, total) para pintar una barra. */
    suspend fun run(peerId: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): SpikeReport = coroutineScope {
        val session = "spike-" + newId().take(24)
        val sentAt = ConcurrentHashMap<String, Long>()
        val rtt = ConcurrentHashMap<String, Long>()
        var seq = 0L

        val listener = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { m ->
                val e = m.envelope
                if (e.type != MatchType.PONG || e.sessionId != session || m.authenticatedPeerId != peerId) return@collect
                val t0 = sentAt[e.payload] ?: return@collect
                rtt.putIfAbsent(e.payload, nowMs() - t0)
            }
        }

        suspend fun ping(token: String): Boolean {
            sentAt[token] = nowMs()
            val env = MatchEnvelope(
                MATCH_PROTOCOL_VERSION, MATCH_GAME_ID, session, me, "p" + newId().take(30), seq++, wallMs(), MatchType.PING, token
            )
            return transport.send(peerId, env)
        }

        val total = thresholds.pings
        for (i in 0 until total) {
            ping("s$i" + "x" + newId().take(8))
            onProgress(i + 1, total)
            delay(thresholds.pingIntervalMs)
        }
        // Rezagados: lo que llega tarde cuenta, pero con su RTT real.
        withTimeoutOrNull(thresholds.stragglersWaitMs) {
            while (rtt.size < total) delay(100)
        }
        val smallRtts = rtt.values.toList()

        // Paquetes grandes: 4 KB de texto determinista; deben volver idénticos (fragmentación BLE / TCP).
        var bigOk = 0
        for (k in 0 until thresholds.bigProbes) {
            val token = bigToken(thresholds.bigPayloadChars, k)
            ping(token)
            // El eco solo se anota si vuelve con el MISMO texto (la clave es el propio contenido): intacto.
            val ok = withTimeoutOrNull(thresholds.bigTimeoutMs) {
                while (!rtt.containsKey(token)) delay(100)
                true
            } == true
            if (ok) bigOk++
        }
        listener.cancel()
        SpikeReport.evaluate(wallMs(), total, smallRtts, thresholds.bigProbes, bigOk, thresholds)
    }

    private fun bigToken(len: Int, salt: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val sb = StringBuilder(len)
        for (i in 0 until len) sb.append(alphabet[(i * 7 + salt * 13 + i / 64) % alphabet.length])
        return sb.toString()
    }
}

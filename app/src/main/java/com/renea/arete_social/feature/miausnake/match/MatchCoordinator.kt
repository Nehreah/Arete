package com.renea.arete_social.feature.miausnake.match

import com.renea.arete_social.feature.miausnake.core.ChallengePacket
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.TurnInput
import com.renea.arete_social.feature.miausnake.core.Verdict
import com.renea.arete_social.feature.miausnake.core.utcDay
import com.renea.arete_social.feature.miausnake.core.verifyChallengeResult
import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.RULES_VERSION
import com.renea.arete_social.feature.miausnake.data.SocialStore
import com.renea.arete_social.feature.miausnake.social.ChallengeBook
import com.renea.arete_social.feature.miausnake.social.ChallengeImporter
import com.renea.arete_social.feature.miausnake.social.ChallengeRole
import com.renea.arete_social.feature.miausnake.social.Ids
import com.renea.arete_social.feature.miausnake.social.ImportOutcome
import com.renea.arete_social.feature.miausnake.social.LevelResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface SpikeState {
    data object Idle : SpikeState
    data class Running(val sent: Int, val total: Int) : SpikeState
    data class Done(val report: SpikeReport) : SpikeState
}

/**
 * Pega la máquina de estados con el mundo: transporte, reloj, re-simulación y persistencia.
 * Vive SOLO mientras la pantalla de cercanía está abierta ([start]/[stop]): no escucha ni responde en
 * segundo plano, y nada empieza sin una acción explícita de la persona (invitar, aceptar, listo).
 *
 * Seguridad:
 *  - Solo se procesan sobres de paquetes FIRMADOS cuyo remitente autenticado por la malla coincide con
 *    el `senderPeerId` del sobre. Sin firma o con otro remitente: se descarta.
 *  - El resultado del rival jamás se cree: se re-simula ([ChallengeImporter]) y debe ser de ESTE reto.
 *  - Una invitación vieja (`sentAt`) o de una sesión ya cerrada no se puede «resucitar» con balizas tardías.
 *  - Un duelo no da recompensa a Michi ni toca los récords del mapa: solo escribe su propio historial.
 */
class MatchCoordinator(
    private val transport: MatchTransport,
    private val meProvider: () -> String?,
    private val nameProvider: () -> String,
    private val resolver: LevelResolver,
    private val store: SocialStore,
    private val scope: CoroutineScope,
    private val wallMs: () -> Long = { System.currentTimeMillis() },
    private val monoMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val lock = Mutex()
    private var machine: MatchMachine? = null
    private var seq = 0L
    private var finalized = false
    private val recentSessions = LinkedHashSet<String>()
    private val lastBusyReplyAt = HashMap<String, Long>()
    private val lastPongAt = HashMap<String, Long>()
    private var pongSeq = 0L
    private val outbox = Channel<Pair<String, MatchEnvelope>>(Channel.UNLIMITED)
    private var jobs: List<Job> = emptyList()

    private val _session = MutableStateFlow<MatchView?>(null)
    val session: StateFlow<MatchView?> = _session.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    /** Aviso corto para la pantalla (p. ej. «no hay teléfonos conectados»). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _spike = MutableStateFlow<SpikeState>(SpikeState.Idle)
    val spike: StateFlow<SpikeState> = _spike.asStateFlow()

    private val _ledger = MutableStateFlow(runCatching { store.loadMatches() }.getOrDefault(MatchLedger.EMPTY))
    val ledger: StateFlow<MatchLedger> = _ledger.asStateFlow()

    val linkedPeers: StateFlow<Int> get() = transport.linkedPeers

    /** ¿La última medición real del transporte fue APTA y reciente? Sin eso no se puede invitar. */
    fun gateOpen(): Boolean = NearbyGate.isOpen(SpikeReport.fromText(store.loadSpike()), wallMs())
    fun lastSpike(): SpikeReport? = SpikeReport.fromText(store.loadSpike())

    fun clearNotice() { _notice.value = null }

    // ---------------- ciclo de vida ----------------

    fun start() {
        if (jobs.any { it.isActive }) return
        jobs = listOf(
            scope.launch { transport.incoming.collect { m -> runCatching { onInbound(m) } } },
            scope.launch { for ((peer, env) in outbox) runCatching { transport.send(peer, env) } },
            scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    lock.withLock { machine?.let { run(it, it.tick(monoMs())); publish(it) } }
                }
            }
        )
    }

    /** Cierra la pantalla: cancela lo pendiente y avisa al rival si había un duelo en marcha. */
    fun stop() {
        scope.launch {
            lock.withLock { machine?.let { m -> if (!m.isClosed) run(m, m.leave(monoMs())); finalizeIfNeeded(m) } }
            // Da un instante a que salga el aviso de salida antes de cortar el envío.
            delay(300)
            jobs.forEach { it.cancel() }
            jobs = emptyList()
            lock.withLock { machine = null; _session.value = null }
        }
    }

    // ---------------- acciones del jugador ----------------

    fun invite(peer: NearbyPeer, challenge: Challenge) {
        scope.launch {
            val me = meProvider() ?: return@launch
            lock.withLock {
                if (machine?.let { !it.isClosed } == true) return@withLock
                if (transport.linkedPeers.value <= 0) {
                    _notice.value = "No hay teléfonos conectados por Bluetooth o Wi‑Fi ahora mismo."
                    return@withLock
                }
                val m = MatchMachine.host(newId().take(32), me, peer.userId, peer.displayName, challenge, MatchText.cleanName(nameProvider()), monoMs())
                machine = m; seq = 0; finalized = false
                run(m, m.beginEffects()); publish(m)
            }
        }
    }

    fun accept() = act { m, now -> m.accept(now) }
    fun decline() = act { m, now -> m.decline(now) }
    fun ready() = act { m, now -> m.ready(now) }

    /** Salir del duelo (en cualquier momento). Después, [dismiss] quita la pantalla del duelo. */
    fun leave() = act { m, now -> m.leave(now) }

    fun dismiss() {
        scope.launch {
            lock.withLock {
                machine?.let { m -> if (!m.isClosed) run(m, m.leave(monoMs())); finalizeIfNeeded(m) }
                machine = null; _session.value = null
            }
        }
    }

    /** Llamar UNA vez cuando la partida del duelo termina (viene de la pantalla de juego). */
    fun playerFinished(score: Int, inputs: List<TurnInput>) {
        scope.launch {
            val m = lock.withLock { machine } ?: return@launch
            val level = runCatching { resolver.resolve(m.challenge.mapNo, m.challenge.mode) }.getOrNull() ?: return@launch
            val res = ChallengeResult(m.challenge, level.version, MatchText.cleanName(nameProvider()).ifBlank { "Jugador" }, score, inputs)
            val verdict = withContext(workDispatcher) { verifyChallengeResult(res, level) } as? Verdict.Valid ?: return@launch
            val packet = runCatching { ChallengePacket.encode(res) }.getOrNull() ?: return@launch
            lock.withLock {
                if (machine !== m) return@withLock
                run(m, m.iFinished(verdict.outcome, packet, Ids.of(packet), monoMs())); publish(m)
            }
        }
    }

    private fun act(f: (MatchMachine, Long) -> List<MatchEffect>) {
        scope.launch { lock.withLock { machine?.let { m -> run(m, f(m, monoMs())); publish(m) } } }
    }

    // ---------------- spike de transporte ----------------

    /** Mide el transporte real contra [peer] (que debe tener abierta esta pantalla). */
    fun runSpike(peer: NearbyPeer) {
        scope.launch {
            val me = meProvider() ?: return@launch
            if (_spike.value is SpikeState.Running || machine?.let { !it.isClosed } == true) return@launch
            if (transport.linkedPeers.value <= 0) {
                _notice.value = "No hay teléfonos conectados por Bluetooth o Wi‑Fi ahora mismo."
                return@launch
            }
            _spike.value = SpikeState.Running(0, SpikeThresholds().pings)
            val report = TransportSpike(transport, me, newId = newId, nowMs = monoMs, wallMs = wallMs)
                .run(peer.userId) { s, t -> _spike.value = SpikeState.Running(s, t) }
            runCatching { store.saveSpike(report.toText()) }
            _spike.value = SpikeState.Done(report)
        }
    }

    fun closeSpike() { _spike.value = SpikeState.Idle }

    // ---------------- entrada de la malla ----------------

    private suspend fun onInbound(m: InboundMatch) {
        val me = meProvider() ?: return
        val e = m.envelope
        if (!m.signed || e.senderPeerId != m.authenticatedPeerId || m.authenticatedPeerId == me) return
        when (e.type) {
            MatchType.PING -> answerPing(e, me)
            MatchType.PONG -> Unit // los consume TransportSpike
            MatchType.LOBBY_INVITE -> onInvite(e, me)
            else -> lock.withLock { machine?.let { run(it, it.onEnvelope(e, monoMs())); publish(it) } }
        }
    }

    private fun answerPing(e: MatchEnvelope, me: String) {
        val now = monoMs()
        val prev = lastPongAt[e.senderPeerId]
        if (prev != null && now - prev < PONG_MIN_GAP_MS) return // freno anti-abuso
        lastPongAt[e.senderPeerId] = now
        if (lastPongAt.size > 32) lastPongAt.keys.take(16).forEach { lastPongAt.remove(it) }
        val pong = MatchEnvelope(MATCH_PROTOCOL_VERSION, MATCH_GAME_ID, e.sessionId, me, "q" + newId().take(30), pongSeq++, wallMs(), MatchType.PONG, e.payload)
        outbox.trySend(e.senderPeerId to pong)
    }

    private suspend fun onInvite(e: MatchEnvelope, me: String) {
        val cfg = LobbyConfig.parse(e.payload) ?: return
        val age = wallMs() - e.sentAt
        if (age > MatchTimings.INVITE_MAX_AGE_MS || age < -MatchTimings.INVITE_MAX_AGE_MS) return // quedó en una cola
        lock.withLock {
            val cur = machine
            if (cur != null && !cur.isClosed) {
                if (cur.sessionId == e.sessionId) return@withLock // baliza repetida de la invitación que ya tengo
                busyReply(e, me)
                return@withLock
            }
            if (e.sessionId in recentSessions) return@withLock // sesión ya cerrada: no se reabre
            val known = cfg.challenge.rules == RULES_VERSION &&
                runCatching { resolver.resolve(cfg.challenge.mapNo, cfg.challenge.mode) }.getOrNull() != null
            if (!known) {
                outbox.trySend(e.senderPeerId to reply(e.sessionId, me, MatchType.PLAYER_LEFT, LeftReason.ERROR.wire))
                remember(e.sessionId)
                return@withLock
            }
            val g = MatchMachine.guest(e, cfg, me, MatchText.cleanName(nameProvider()), monoMs())
            machine = g; seq = 0; finalized = false
            publish(g)
        }
    }

    private fun busyReply(e: MatchEnvelope, me: String) {
        val now = monoMs()
        val prev = lastBusyReplyAt[e.senderPeerId]
        if (prev != null && now - prev < BUSY_MIN_GAP_MS) return
        lastBusyReplyAt[e.senderPeerId] = now
        if (lastBusyReplyAt.size > 32) lastBusyReplyAt.keys.take(16).forEach { lastBusyReplyAt.remove(it) }
        outbox.trySend(e.senderPeerId to reply(e.sessionId, me, MatchType.PLAYER_LEFT, LeftReason.BUSY.wire))
    }

    private fun reply(session: String, me: String, type: MatchType, payload: String) =
        MatchEnvelope(MATCH_PROTOCOL_VERSION, MATCH_GAME_ID, session, me, "r" + newId().take(30), pongSeq++, wallMs(), type, payload)

    // ---------------- efectos ----------------

    private fun run(m: MatchMachine, effects: List<MatchEffect>) {
        val me = meProvider() ?: return
        for (f in effects) when (f) {
            is MatchEffect.Send -> {
                val env = MatchEnvelope(MATCH_PROTOCOL_VERSION, MATCH_GAME_ID, m.sessionId, me, "m" + newId().take(30), seq++, wallMs(), f.type, f.payload)
                if (MatchCodec.validEnvelope(env) && MatchCodec.validPayload(env.type, env.payload)) outbox.trySend(m.peer to env)
            }
            is MatchEffect.VerifyPeerResult -> verifyPeer(m, f.packet)
            is MatchEffect.StartPlay -> Unit // la pantalla arranca la partida al ver phase == PLAYING
            is MatchEffect.Closed -> remember(m.sessionId)
        }
    }

    /** Re-simula el resultado del rival fuera del hilo principal. Nunca se acepta lo declarado. */
    private fun verifyPeer(m: MatchMachine, packet: String) {
        scope.launch {
            val id = Ids.of(packet)
            val verdict = withContext(workDispatcher) {
                when (val o = ChallengeImporter.import(packet, resolver)) {
                    is ImportOutcome.Verified -> PeerVerdict.Ok(o.result, o.outcome, packet, id)
                    is ImportOutcome.Invalid -> PeerVerdict.Rejected("invalid:" + o.reason.name, id)
                    else -> PeerVerdict.Rejected("unreadable", id)
                }
            }
            lock.withLock {
                if (machine !== m) return@withLock
                run(m, m.peerResultVerified(verdict, monoMs())); publish(m)
            }
        }
    }

    private fun publish(m: MatchMachine) {
        _session.value = m.view(monoMs())
        if (m.phase == MatchPhase.FINISHED || m.phase == MatchPhase.CLOSED) finalizeIfNeeded(m)
    }

    private fun remember(sessionId: String) {
        recentSessions.add(sessionId)
        while (recentSessions.size > 20) recentSessions.remove(recentSessions.first())
    }

    /** Guarda UNA vez el historial y los resultados (idempotente por sesión). Solo si yo jugué hasta el final. */
    private fun finalizeIfNeeded(m: MatchMachine) {
        if (finalized) return
        val v = m.view(monoMs())
        val mine = v.myRun ?: return
        if (v.phase != MatchPhase.FINISHED && v.phase != MatchPhase.CLOSED) return
        finalized = true
        val outcome = when (v.verdict) {
            MatchVerdict.WON -> MatchOutcome.WON
            MatchVerdict.LOST -> MatchOutcome.LOST
            MatchVerdict.TIE -> MatchOutcome.TIE
            MatchVerdict.PEER_INVALID -> MatchOutcome.PEER_INVALID
            MatchVerdict.PENDING -> MatchOutcome.NO_RESULT
        }
        val day = utcDay(wallMs())
        runCatching {
            val rec = MatchRecord(
                id = v.sessionId, atDay = day, peerId = v.peerId, peerName = MatchText.cleanName(v.peerName),
                mapNo = v.challenge.mapNo, mode = v.challenge.mode, myScore = mine.outcome.score,
                peerScore = v.peerRun?.outcome?.score, outcome = outcome
            )
            val next = _ledger.value.add(rec)
            _ledger.value = next
            store.saveMatches(next)
        }
        runCatching {
            // Mi resultado queda como reto enviado (se puede compartir por texto/QR aunque el rival no llegara)
            // y, si el rival terminó, el suyo como recibido para jugar contra su sombra.
            var libro = store.loadChallenges()
            if (ChallengeBook.canStore(mine.packet)) libro = libro.add(ChallengeRole.SENT, mine.packet, mine.outcome.score, day)
            v.peerRun?.let { if (ChallengeBook.canStore(it.packet)) libro = libro.add(ChallengeRole.RECEIVED, it.packet, mine.outcome.score, day) }
            store.saveChallenges(libro)
        }
    }

    private companion object {
        const val TICK_MS = 500L
        const val PONG_MIN_GAP_MS = 100L
        const val BUSY_MIN_GAP_MS = 10_000L
    }
}

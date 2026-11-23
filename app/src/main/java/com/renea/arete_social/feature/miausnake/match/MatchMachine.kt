package com.renea.arete_social.feature.miausnake.match

import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.RunOutcome
import com.renea.arete_social.feature.miausnake.core.compareRuns

object MatchTimings {
    const val INVITE_TTL_MS = 60_000L
    const val LOBBY_TTL_MS = 180_000L
    const val BEACON_MS = 3_000L
    const val GO_BEACON_MS = 1_000L
    const val COUNTDOWN_MS = 3_000
    const val RESULT_WAIT_MS = 600_000L
    const val END_RESEND_MIN_MS = 2_000L
    /** Una invitación más vieja que esto (según el `sentAt` del anfitrión) se ignora: pudo quedar en una cola. */
    const val INVITE_MAX_AGE_MS = 120_000L
    val RESULT_BACKOFF_MS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)
}

enum class MatchRole { HOST, GUEST }

enum class MatchPhase { INVITING, INVITED, LOBBY, COUNTDOWN, PLAYING, WAITING_PEER, FINISHED, CLOSED }

enum class CloseReason { DECLINED_BY_ME, DECLINED_BY_PEER, CANCELLED_BY_ME, CANCELLED_BY_PEER, BUSY, TIMEOUT, PROTOCOL }

enum class MatchVerdict { PENDING, WON, LOST, TIE, PEER_INVALID }

/** Lo que la máquina pide al coordinador. La máquina no hace E/S ni conoce el reloj de pared. */
sealed interface MatchEffect {
    data class Send(val type: MatchType, val payload: String) : MatchEffect
    data class StartPlay(val challenge: Challenge) : MatchEffect
    data class VerifyPeerResult(val packet: String) : MatchEffect
    data class Closed(val reason: CloseReason) : MatchEffect
}

data class MyRun(val outcome: RunOutcome, val packet: String, val resultId: String)
data class PeerRun(val result: ChallengeResult, val outcome: RunOutcome, val packet: String, val resultId: String)

/** Veredicto de re-simular el resultado del rival (lo calcula el coordinador fuera del hilo principal). */
sealed interface PeerVerdict {
    data class Ok(val result: ChallengeResult, val outcome: RunOutcome, val packet: String, val resultId: String) : PeerVerdict
    /** [resultId] es el id del paquete recibido: así el rival sabe QUÉ resultado suyo rechacé. */
    data class Rejected(val why: String, val resultId: String) : PeerVerdict
}

/** Foto inmutable para pintar la pantalla. */
data class MatchView(
    val sessionId: String,
    val role: MatchRole,
    val phase: MatchPhase,
    val peerId: String,
    val peerName: String,
    val challenge: Challenge,
    val myReady: Boolean,
    val peerReady: Boolean,
    val canStart: Boolean,
    val countdownLeftMs: Long,
    val myRun: MyRun?,
    val peerRun: PeerRun?,
    val verdict: MatchVerdict,
    val peerLeft: Boolean,
    /** null = el rival aún no confirmó haber recibido mi resultado. */
    val peerAcceptedMine: Boolean?,
    val resultTooLong: Boolean,
    val closeReason: CloseReason?
)

/**
 * Máquina de estados del duelo cercano. Determinista y sin E/S: se prueba en JVM con un reloj falso y
 * una red simulada que pierde, duplica y reordena.
 *
 * Reglas de diseño:
 *  - Todo mensaje es IDEMPOTENTE: repetirlo no cambia nada. Por eso la fiabilidad es por «balizas»:
 *    cada lado repite lo que el otro todavía necesita ([tick]), con un sobre NUEVO cada vez (la malla
 *    descarta ids repetidos). Las secuencias del emisor solo suben: un sobre viejo se ignora.
 *  - El anfitrión fija mapa, modo y semilla en la invitación; el `GO` debe traer la MISMA semilla, y
 *    el resultado del rival solo vale si es de ESTE reto (mapa, modo, semilla y reglas).
 *  - Cada uno juega su propio tablero. El ganador sale de [compareRuns] sobre tiempos y puntos
 *    VIRTUALES re-simulados: el reloj de pared y la latencia no influyen en el resultado.
 *  - No se usa en ningún sitio la hora del par para decidir nada de la partida.
 */
class MatchMachine private constructor(
    val sessionId: String,
    val role: MatchRole,
    val me: String,
    val peer: String,
    private var peerName: String,
    val challenge: Challenge,
    private val myName: String,
    startPhase: MatchPhase,
    now: Long
) {
    var phase: MatchPhase = startPhase
        private set
    var closeReason: CloseReason? = null
        private set
    private var phaseSince = now
    private var lastPeerSeq = -1L
    private var myReady = false
    private var peerReady = false
    private var peerLeft = false
    private var countdownMs = 0
    private var countdownAt = 0L
    private var lastBeaconAt = now
    private var myRun: MyRun? = null
    private var peerRun: PeerRun? = null
    private var peerRejected = false
    private var verifying = false
    private var peerAcceptedMine: Boolean? = null
    private var resultTooLong = false
    private var finishedAt = 0L
    private var resBeacons = 0
    private var lastResSentAt = 0L
    private var lastEndSentAt = -1L
    private var lastEndAccepted = true
    private var lastEndResultId = ""

    /** Lo que hay que enviar al empezar (solo el anfitrión: la invitación). */
    fun beginEffects(): List<MatchEffect> =
        if (role == MatchRole.HOST) listOf(MatchEffect.Send(MatchType.LOBBY_INVITE, LobbyConfig(challenge, myName).encode())) else emptyList()

    val isClosed: Boolean get() = phase == MatchPhase.CLOSED

    // ---------------- acciones del jugador ----------------

    fun accept(now: Long): List<MatchEffect> {
        if (role != MatchRole.GUEST || phase != MatchPhase.INVITED) return emptyList()
        enter(MatchPhase.LOBBY, now)
        return listOf(MatchEffect.Send(MatchType.LOBBY_ACCEPT, MatchText.cleanName(myName)))
    }

    fun decline(now: Long): List<MatchEffect> {
        if (role != MatchRole.GUEST || phase != MatchPhase.INVITED) return emptyList()
        return close(CloseReason.DECLINED_BY_ME, now, MatchEffect.Send(MatchType.PLAYER_LEFT, LeftReason.DECLINED.wire))
    }

    /** Invitado: «estoy listo». Anfitrión: «empezar» (solo cuenta si el invitado ya está listo). */
    fun ready(now: Long): List<MatchEffect> {
        if (phase != MatchPhase.LOBBY || myReady) return emptyList()
        return when (role) {
            MatchRole.GUEST -> {
                myReady = true
                lastBeaconAt = now
                listOf(MatchEffect.Send(MatchType.LOBBY_READY, MatchText.cleanName(myName)))
            }
            MatchRole.HOST -> if (peerReady) startCountdown(now) else emptyList()
        }
    }

    fun leave(now: Long): List<MatchEffect> {
        if (phase == MatchPhase.CLOSED) return emptyList()
        // Terminada la partida no hay nada que cancelar: el rival solo necesita mi resultado (lo repite la baliza).
        val tell = phase != MatchPhase.FINISHED
        val eff = if (tell) arrayOf<MatchEffect>(MatchEffect.Send(MatchType.PLAYER_LEFT, LeftReason.CANCELLED.wire)) else emptyArray()
        return close(CloseReason.CANCELLED_BY_ME, now, *eff)
    }

    /** Mi partida terminó y ya está verificada por mi propio motor. */
    fun iFinished(outcome: RunOutcome, packet: String, resultId: String, now: Long): List<MatchEffect> {
        if (phase != MatchPhase.PLAYING && phase != MatchPhase.COUNTDOWN) return emptyList()
        if (myRun != null) return emptyList()
        myRun = MyRun(outcome, packet, resultId)
        finishedAt = now
        val out = ArrayList<MatchEffect>()
        if (packet.length <= MatchLimits.MAX_RESULT_PAYLOAD) {
            lastResSentAt = now
            out += MatchEffect.Send(MatchType.CHALLENGE_RESULT, packet)
        } else {
            resultTooLong = true
        }
        phase = MatchPhase.WAITING_PEER
        phaseSince = now
        maybeFinish(now)
        return out
    }

    // ---------------- mensajes del par ----------------

    fun onEnvelope(env: MatchEnvelope, now: Long): List<MatchEffect> {
        if (phase == MatchPhase.CLOSED) return emptyList()
        if (env.sessionId != sessionId || env.senderPeerId != peer) return emptyList()
        if (env.sequence <= lastPeerSeq) return emptyList()
        lastPeerSeq = env.sequence
        return when (env.type) {
            MatchType.PLAYER_LEFT -> onLeft(LeftReason.fromWire(env.payload) ?: LeftReason.ERROR, now)
            MatchType.LOBBY_ACCEPT -> onAccept(env.payload, now)
            MatchType.LOBBY_READY -> onReady(env.payload, now)
            MatchType.GAME_START -> onGo(env.payload, now)
            MatchType.CHALLENGE_RESULT -> onResult(env.payload, now)
            MatchType.GAME_END -> onEnd(env.payload)
            else -> emptyList() // INV duplicada, PING/PONG: no cambian la partida
        }
    }

    private fun onLeft(reason: LeftReason, now: Long): List<MatchEffect> {
        val started = phase == MatchPhase.PLAYING || phase == MatchPhase.WAITING_PEER ||
            phase == MatchPhase.FINISHED || phase == MatchPhase.COUNTDOWN
        if (started) {
            peerLeft = true
            return emptyList() // mi partida sigue: se resuelve cuando termine
        }
        val why = when (reason) {
            LeftReason.DECLINED -> CloseReason.DECLINED_BY_PEER
            LeftReason.BUSY -> CloseReason.BUSY
            LeftReason.TIMEOUT -> CloseReason.TIMEOUT
            LeftReason.ERROR -> CloseReason.PROTOCOL
            LeftReason.CANCELLED -> CloseReason.CANCELLED_BY_PEER
        }
        return close(why, now)
    }

    private fun onAccept(name: String, now: Long): List<MatchEffect> {
        if (role != MatchRole.HOST) return emptyList()
        if (name.isNotBlank()) peerName = name
        if (phase == MatchPhase.INVITING) enter(MatchPhase.LOBBY, now)
        return emptyList()
    }

    private fun onReady(name: String, now: Long): List<MatchEffect> {
        if (role != MatchRole.HOST) return emptyList()
        if (name.isNotBlank()) peerName = name
        if (phase == MatchPhase.INVITING) enter(MatchPhase.LOBBY, now) // la baliza RDY implica aceptar
        if (phase == MatchPhase.LOBBY) peerReady = true // el anfitrión empieza con su botón (canStart)
        return emptyList()
    }

    private fun onGo(payload: String, now: Long): List<MatchEffect> {
        if (role != MatchRole.GUEST || phase != MatchPhase.LOBBY) return emptyList()
        val go = GameStart.parse(payload) ?: return emptyList()
        // La semilla no puede cambiar después de aceptar: un GO con otra semilla es un anfitrión roto o tramposo.
        if (go.seed != (challenge.seed and 0xFFFFFFL)) {
            return close(CloseReason.PROTOCOL, now, MatchEffect.Send(MatchType.PLAYER_LEFT, LeftReason.ERROR.wire))
        }
        countdownMs = go.countdownMs
        countdownAt = now
        enter(MatchPhase.COUNTDOWN, now)
        return emptyList()
    }

    private fun onResult(packet: String, now: Long): List<MatchEffect> {
        val ok = phase == MatchPhase.COUNTDOWN || phase == MatchPhase.PLAYING ||
            phase == MatchPhase.WAITING_PEER || phase == MatchPhase.FINISHED
        if (!ok) return emptyList()
        if (peerRun != null || peerRejected) {
            // Repetición de algo ya resuelto: mi acuse se pudo perder. Se repite, como mucho cada 2 s.
            return if (lastEndSentAt >= 0 && now - lastEndSentAt >= MatchTimings.END_RESEND_MIN_MS) {
                lastEndSentAt = now
                listOf(MatchEffect.Send(MatchType.GAME_END, endPayload()))
            } else emptyList()
        }
        if (verifying) return emptyList()
        verifying = true
        return listOf(MatchEffect.VerifyPeerResult(packet))
    }

    private fun endPayload(): String = GameEnd(lastEndResultId, lastEndAccepted).encode()

    private fun onEnd(payload: String): List<MatchEffect> {
        val end = GameEnd.parse(payload) ?: return emptyList()
        val mine = myRun ?: return emptyList()
        if (end.resultId == mine.resultId && peerAcceptedMine == null) peerAcceptedMine = end.accepted
        return emptyList()
    }

    /** El coordinador terminó de re-simular el resultado del rival. */
    fun peerResultVerified(v: PeerVerdict, now: Long): List<MatchEffect> {
        if (phase == MatchPhase.CLOSED || !verifying) return emptyList()
        verifying = false
        val out = ArrayList<MatchEffect>()
        when (v) {
            is PeerVerdict.Ok -> {
                val c = v.result.challenge
                val sameChallenge = c.rules == challenge.rules && c.mapNo == challenge.mapNo &&
                    c.mode == challenge.mode && (c.seed and 0xFFFFFFL) == (challenge.seed and 0xFFFFFFL)
                if (sameChallenge) {
                    peerRun = PeerRun(v.result, v.outcome, v.packet, v.resultId)
                    lastEndResultId = v.resultId
                    lastEndAccepted = true
                } else {
                    peerRejected = true // era un resultado válido, pero de OTRO reto
                    lastEndResultId = v.resultId
                    lastEndAccepted = false
                }
            }
            is PeerVerdict.Rejected -> {
                peerRejected = true
                lastEndResultId = v.resultId
                lastEndAccepted = false
            }
        }
        lastEndSentAt = now
        out += MatchEffect.Send(MatchType.GAME_END, endPayload())
        maybeFinish(now)
        return out
    }

    // ---------------- tiempo ----------------

    fun tick(now: Long): List<MatchEffect> {
        val elapsed = now - phaseSince
        return when (phase) {
            MatchPhase.INVITING -> when {
                elapsed > MatchTimings.INVITE_TTL_MS ->
                    close(CloseReason.TIMEOUT, now, MatchEffect.Send(MatchType.PLAYER_LEFT, LeftReason.TIMEOUT.wire))
                now - lastBeaconAt >= MatchTimings.BEACON_MS -> {
                    lastBeaconAt = now
                    listOf(MatchEffect.Send(MatchType.LOBBY_INVITE, LobbyConfig(challenge, myName).encode()))
                }
                else -> emptyList()
            }
            MatchPhase.INVITED ->
                if (elapsed > MatchTimings.INVITE_TTL_MS) close(CloseReason.TIMEOUT, now) else emptyList()
            MatchPhase.LOBBY -> when {
                elapsed > MatchTimings.LOBBY_TTL_MS ->
                    close(CloseReason.TIMEOUT, now, MatchEffect.Send(MatchType.PLAYER_LEFT, LeftReason.TIMEOUT.wire))
                role == MatchRole.GUEST && now - lastBeaconAt >= MatchTimings.BEACON_MS -> {
                    lastBeaconAt = now
                    // RDY implica ACC: así una ACC perdida se recupera sola.
                    listOf(MatchEffect.Send(if (myReady) MatchType.LOBBY_READY else MatchType.LOBBY_ACCEPT, MatchText.cleanName(myName)))
                }
                else -> emptyList()
            }
            MatchPhase.COUNTDOWN -> when {
                now - countdownAt >= countdownMs -> {
                    enter(MatchPhase.PLAYING, now)
                    listOf(MatchEffect.StartPlay(challenge))
                }
                role == MatchRole.HOST && now - lastBeaconAt >= MatchTimings.GO_BEACON_MS -> {
                    lastBeaconAt = now
                    listOf(MatchEffect.Send(MatchType.GAME_START, GameStart(challenge.seed, 0, countdownMs).encode()))
                }
                else -> emptyList()
            }
            MatchPhase.WAITING_PEER, MatchPhase.FINISHED -> resultBeacon(now)
            else -> emptyList()
        }
    }

    /** Repite MI resultado (con espera creciente) hasta que el rival confirme que lo recibió. */
    private fun resultBeacon(now: Long): List<MatchEffect> {
        val mine = myRun ?: return emptyList()
        if (phase == MatchPhase.WAITING_PEER && peerLeft) {
            return close(CloseReason.CANCELLED_BY_PEER, now) // nadie más va a mandar nada
        }
        if (now - finishedAt > MatchTimings.RESULT_WAIT_MS) {
            return if (phase == MatchPhase.WAITING_PEER) close(CloseReason.TIMEOUT, now) else emptyList()
        }
        if (peerAcceptedMine != null || resultTooLong || peerLeft) return emptyList()
        val wait = MatchTimings.RESULT_BACKOFF_MS[minOf(resBeacons, MatchTimings.RESULT_BACKOFF_MS.size - 1)]
        if (now - lastResSentAt < wait) return emptyList()
        resBeacons++
        lastResSentAt = now
        return listOf(MatchEffect.Send(MatchType.CHALLENGE_RESULT, mine.packet))
    }

    // ---------------- internos ----------------

    private fun startCountdown(now: Long): List<MatchEffect> {
        myReady = true
        countdownMs = MatchTimings.COUNTDOWN_MS
        countdownAt = now
        lastBeaconAt = now
        enter(MatchPhase.COUNTDOWN, now)
        return listOf(MatchEffect.Send(MatchType.GAME_START, GameStart(challenge.seed, 0, countdownMs).encode()))
    }

    private fun enter(p: MatchPhase, now: Long) {
        phase = p
        phaseSince = now
    }

    private fun maybeFinish(now: Long) {
        if (myRun != null && (peerRun != null || peerRejected) && phase == MatchPhase.WAITING_PEER) {
            enter(MatchPhase.FINISHED, now)
        }
    }

    private fun close(why: CloseReason, now: Long, vararg extra: MatchEffect): List<MatchEffect> {
        phase = MatchPhase.CLOSED
        closeReason = why
        phaseSince = now
        return extra.toList() + MatchEffect.Closed(why)
    }

    fun verdict(): MatchVerdict {
        val mine = myRun ?: return MatchVerdict.PENDING
        if (peerRejected) return MatchVerdict.PEER_INVALID
        val theirs = peerRun ?: return MatchVerdict.PENDING
        val c = compareRuns(challenge.mode, mine.outcome, theirs.outcome)
        return when {
            c > 0 -> MatchVerdict.WON
            c < 0 -> MatchVerdict.LOST
            else -> MatchVerdict.TIE
        }
    }

    fun view(now: Long): MatchView = MatchView(
        sessionId = sessionId, role = role, phase = phase, peerId = peer, peerName = peerName.ifBlank { "Rival" },
        challenge = challenge, myReady = myReady, peerReady = peerReady,
        canStart = role == MatchRole.HOST && phase == MatchPhase.LOBBY && peerReady,
        countdownLeftMs = if (phase == MatchPhase.COUNTDOWN) maxOf(0L, countdownMs - (now - countdownAt)) else 0L,
        myRun = myRun, peerRun = peerRun, verdict = verdict(), peerLeft = peerLeft,
        peerAcceptedMine = peerAcceptedMine, resultTooLong = resultTooLong, closeReason = closeReason
    )

    companion object {
        fun host(sessionId: String, me: String, peer: String, peerName: String, challenge: Challenge, myName: String, now: Long) =
            MatchMachine(sessionId, MatchRole.HOST, me, peer, peerName, challenge, myName, MatchPhase.INVITING, now)

        /** [invite] ya validado por el coordinador (sesión nueva, remitente autenticado, no caducada). */
        fun guest(invite: MatchEnvelope, config: LobbyConfig, me: String, myName: String, now: Long): MatchMachine =
            MatchMachine(invite.sessionId, MatchRole.GUEST, me, invite.senderPeerId, config.hostName, config.challenge, myName, MatchPhase.INVITED, now)
                .also { it.lastPeerSeq = invite.sequence }
    }
}

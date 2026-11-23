package com.renea.arete_social.feature.miausnake.match

import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengePacket

const val MATCH_PROTOCOL_VERSION = 1
const val MATCH_GAME_ID = "miausnake"

/**
 * Tipos de mensaje (plan V9, sección 00.7). Fase 6 = duelo por resultados: cada teléfono juega SU tablero
 * con la misma semilla y se comparan las repeticiones. No hay INPUT/STATE_CHECK/SNAPSHOT: eso es la
 * carrera simultánea («futuro», fuera de alcance).
 *
 * Flujo: INV (anfitrión) → ACC (invitado) → RDY (invitado) → GO (anfitrión) → RES (ambos) → END (ambos).
 * LEFT cierra en cualquier punto. PING/PONG solo sirven al spike de transporte.
 */
enum class MatchType(val wire: String) {
    LOBBY_INVITE("INV"),
    LOBBY_ACCEPT("ACC"),
    LOBBY_READY("RDY"),
    GAME_START("GO"),
    CHALLENGE_RESULT("RES"),
    GAME_END("END"),
    PLAYER_LEFT("LEFT"),
    PING("PING"),
    PONG("PONG");

    companion object {
        fun fromWire(w: String): MatchType? = entries.firstOrNull { it.wire == w }
    }
}

/**
 * Sobre de juego. [sentAt] es solo diagnóstico y caducidad de invitaciones: NUNCA decide nada de la
 * partida (los ticks los fija el motor determinista). [senderPeerId] debe coincidir con el remitente
 * autenticado por la malla; si no, el mensaje se descarta.
 */
data class MatchEnvelope(
    val protocolVersion: Int,
    val gameId: String,
    val sessionId: String,
    val senderPeerId: String,
    val messageId: String,
    val sequence: Long,
    val sentAt: Long,
    val type: MatchType,
    val payload: String
)

enum class EnvelopeError { NOT_ENVELOPE, TOO_LONG, UNSUPPORTED_VERSION, WRONG_GAME, BAD_FIELD, UNKNOWN_TYPE, BAD_PAYLOAD }

sealed interface EnvelopeDecode {
    data class Ok(val envelope: MatchEnvelope) : EnvelopeDecode
    data class Bad(val error: EnvelopeError) : EnvelopeDecode
}

object MatchLimits {
    /** La malla rechaza contenidos de más de 16.000 caracteres; se deja margen. */
    const val MAX_ENVELOPE_CHARS = 14_000
    const val MAX_SEQUENCE = 1_000_000L
    const val MAX_PING_PAYLOAD = 4_200
    const val MAX_RESULT_PAYLOAD = 13_000
    val ID = Regex("[A-Za-z0-9:_.-]{1,64}")
    val PEER = Regex("[A-Za-z0-9:_.-]{1,132}")
    val TOKEN = Regex("[A-Za-z0-9_-]*")
}

/** Texto de jugador seguro para viajar en un payload: sin controles, ≤ 24 bytes y sin `|` ni `;`. */
object MatchText {
    fun cleanName(raw: String): String =
        ChallengePacket.sanitizeName(raw.replace('|', ' ').replace(';', ' ')).trim()
}

/** Invitación: el anfitrión fija mapa, modo, semilla y reglas en un código `MIAU1-17-M-7F3A9C`. */
data class LobbyConfig(val challenge: Challenge, val hostName: String) {
    fun encode(): String = challenge.toCode() + ";" + MatchText.cleanName(hostName)

    companion object {
        fun parse(payload: String): LobbyConfig? {
            val i = payload.indexOf(';')
            if (i < 0) return null
            val c = Challenge.parse(payload.substring(0, i)) ?: return null
            val name = payload.substring(i + 1)
            if (name != MatchText.cleanName(name)) return null
            return LobbyConfig(c, name)
        }
    }
}

/** `GO`: semilla acordada, tick inicial (siempre 0 en esta fase) y cuenta atrás. */
data class GameStart(val seed: Long, val startTick: Int, val countdownMs: Int) {
    fun encode(): String =
        (seed and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0') + ";$startTick;$countdownMs"

    companion object {
        const val MIN_COUNTDOWN_MS = 1_000
        const val MAX_COUNTDOWN_MS = 10_000
        private val SEED = Regex("[0-9A-F]{6}")

        fun parse(payload: String): GameStart? {
            val p = payload.split(';')
            if (p.size != 3 || !SEED.matches(p[0])) return null
            val tick = p[1].toIntOrNull() ?: return null
            val cd = p[2].toIntOrNull() ?: return null
            if (tick != 0 || cd !in MIN_COUNTDOWN_MS..MAX_COUNTDOWN_MS) return null
            return GameStart(p[0].toLong(16), tick, cd)
        }
    }
}

enum class LeftReason(val wire: String) {
    DECLINED("D"), CANCELLED("C"), BUSY("B"), TIMEOUT("T"), ERROR("E");

    companion object {
        fun fromWire(w: String): LeftReason? = entries.firstOrNull { it.wire == w }
    }
}

/** `END`: acuse de que recibí tu resultado (identificado por [resultId]) y qué concluí al re-simularlo. */
data class GameEnd(val resultId: String, val accepted: Boolean) {
    fun encode(): String = resultId + ";" + (if (accepted) "OK" else "BAD")

    companion object {
        private val ID = Regex("[0-9a-f]{32}")

        fun parse(payload: String): GameEnd? {
            val p = payload.split(';')
            if (p.size != 2 || !ID.matches(p[0])) return null
            return when (p[1]) {
                "OK" -> GameEnd(p[0], true)
                "BAD" -> GameEnd(p[0], false)
                else -> null
            }
        }
    }
}

/**
 * Codec de texto de una sola línea, acotado y que NUNCA lanza por datos externos:
 * `MSM1|juego|sesión|remitente|mensaje|secuencia|sentAt|TIPO|payload`.
 */
object MatchCodec {
    private const val FIELDS = 9

    fun encode(e: MatchEnvelope): String {
        require(validEnvelope(e)) { "sobre inválido" }
        return "MSM${e.protocolVersion}|${e.gameId}|${e.sessionId}|${e.senderPeerId}|${e.messageId}|" +
            "${e.sequence}|${e.sentAt}|${e.type.wire}|${e.payload}"
    }

    fun decode(text: String): EnvelopeDecode {
        if (text.length > MatchLimits.MAX_ENVELOPE_CHARS) return EnvelopeDecode.Bad(EnvelopeError.TOO_LONG)
        if (!text.startsWith("MSM")) return EnvelopeDecode.Bad(EnvelopeError.NOT_ENVELOPE)
        val p = text.split('|', limit = FIELDS)
        if (p.size != FIELDS) return EnvelopeDecode.Bad(EnvelopeError.BAD_FIELD)
        val version = p[0].removePrefix("MSM").toIntOrNull() ?: return EnvelopeDecode.Bad(EnvelopeError.NOT_ENVELOPE)
        if (version != MATCH_PROTOCOL_VERSION) return EnvelopeDecode.Bad(EnvelopeError.UNSUPPORTED_VERSION)
        if (p[1] != MATCH_GAME_ID) return EnvelopeDecode.Bad(EnvelopeError.WRONG_GAME)
        val type = MatchType.fromWire(p[7]) ?: return EnvelopeDecode.Bad(EnvelopeError.UNKNOWN_TYPE)
        val seq = p[5].toLongOrNull() ?: return EnvelopeDecode.Bad(EnvelopeError.BAD_FIELD)
        val sentAt = p[6].toLongOrNull() ?: return EnvelopeDecode.Bad(EnvelopeError.BAD_FIELD)
        val env = MatchEnvelope(version, p[1], p[2], p[3], p[4], seq, sentAt, type, p[8])
        if (!validEnvelope(env)) return EnvelopeDecode.Bad(EnvelopeError.BAD_FIELD)
        if (!validPayload(env.type, env.payload)) return EnvelopeDecode.Bad(EnvelopeError.BAD_PAYLOAD)
        return EnvelopeDecode.Ok(env)
    }

    fun validEnvelope(e: MatchEnvelope): Boolean =
        e.protocolVersion == MATCH_PROTOCOL_VERSION && e.gameId == MATCH_GAME_ID &&
            MatchLimits.ID.matches(e.sessionId) && MatchLimits.PEER.matches(e.senderPeerId) &&
            MatchLimits.ID.matches(e.messageId) && e.sequence in 0..MatchLimits.MAX_SEQUENCE && e.sentAt >= 0 &&
            e.payload.none { it == '\n' || it == '\r' }

    fun validPayload(type: MatchType, payload: String): Boolean = when (type) {
        MatchType.LOBBY_INVITE -> LobbyConfig.parse(payload) != null
        MatchType.LOBBY_ACCEPT, MatchType.LOBBY_READY -> payload == MatchText.cleanName(payload)
        MatchType.GAME_START -> GameStart.parse(payload) != null
        MatchType.CHALLENGE_RESULT -> payload.length <= MatchLimits.MAX_RESULT_PAYLOAD &&
            payload.startsWith(ChallengePacket.PREFIX) && payload.none { it == '|' || it.isWhitespace() }
        MatchType.GAME_END -> GameEnd.parse(payload) != null
        MatchType.PLAYER_LEFT -> LeftReason.fromWire(payload) != null
        MatchType.PING, MatchType.PONG ->
            payload.length <= MatchLimits.MAX_PING_PAYLOAD && MatchLimits.TOKEN.matches(payload)
    }
}

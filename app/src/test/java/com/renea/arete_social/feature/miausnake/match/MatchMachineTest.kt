package com.renea.arete_social.feature.miausnake.match

import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.RunOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dos máquinas conectadas en memoria. La red real (pérdida, duplicados) se simula en scripts/miausnake/phase6_check.py. */
class MatchMachineTest {
    private val ch = Challenge(1, 5, 'M', 0x7F3A9C)
    private val seq = mutableMapOf("A" to 1L, "B" to 0L) // A empieza en 1: la invitación (seq 0) ya cuenta

    private fun deliver(from: String, to: MatchMachine, effs: List<MatchEffect>, now: Long, pending: MutableList<MatchEffect> = mutableListOf()) {
        for (e in effs) if (e is MatchEffect.Send) {
            val env = MatchEnvelope(1, MATCH_GAME_ID, to.sessionId, from, "m${seq[from]}", seq.getValue(from), now, e.type, e.payload)
            seq[from] = seq.getValue(from) + 1
            val back = to.onEnvelope(env, now)
            pending += back
        }
    }

    private fun result(score: Int, won: Boolean, ms: Long) = RunOutcome(score, score, won, ms / 100, ms)
    private fun dummyResult() = ChallengeResult(ch, 1, "x", 0, emptyList())

    @Test fun `flujo completo y gana el mas rapido`() {
        val h = MatchMachine.host("S1", "A", "B", "Bo", ch, "Ana", 0)
        val g = MatchMachine.guest(
            MatchEnvelope(1, MATCH_GAME_ID, "S1", "A", "i", 0, 0, MatchType.LOBBY_INVITE, LobbyConfig(ch, "Ana").encode()),
            LobbyConfig(ch, "Ana"), "B", "Bo", 0
        )
        assertEquals(MatchPhase.INVITED, g.phase)
        deliver("B", h, g.accept(10), 10)
        assertEquals(MatchPhase.LOBBY, h.phase)
        assertFalse(h.view(10).canStart)
        deliver("B", h, g.ready(20), 20)
        assertTrue(h.view(20).canStart)
        deliver("A", g, h.ready(30), 30)
        assertEquals(MatchPhase.COUNTDOWN, h.phase); assertEquals(MatchPhase.COUNTDOWN, g.phase)
        assertTrue(h.tick(3_100).any { it is MatchEffect.StartPlay }); assertTrue(g.tick(3_200).any { it is MatchEffect.StartPlay })
        val pend = mutableListOf<MatchEffect>()
        deliver("A", g, h.iFinished(result(10, true, 40_000), "MIAUR1:AAAA", "a".repeat(32), 40_000), 40_000, pend)
        assertTrue(pend.any { it is MatchEffect.VerifyPeerResult })
        val fin = g.iFinished(result(10, true, 55_000), "MIAUR1:BBBB", "b".repeat(32), 55_000)
        // el invitado ya tenía pendiente verificar el resultado del anfitrión
        g.peerResultVerified(PeerVerdict.Ok(dummyResult(), result(10, true, 40_000), "MIAUR1:AAAA", "a".repeat(32)), 55_100)
        assertEquals(MatchVerdict.LOST, g.verdict()); assertEquals(MatchPhase.FINISHED, g.phase)
        assertTrue(fin.any { it is MatchEffect.Send && it.type == MatchType.CHALLENGE_RESULT })
    }

    @Test fun `un GO con otra semilla cierra`() {
        val g = MatchMachine.guest(
            MatchEnvelope(1, MATCH_GAME_ID, "S1", "A", "i", 0, 0, MatchType.LOBBY_INVITE, LobbyConfig(ch, "Ana").encode()),
            LobbyConfig(ch, "Ana"), "B", "Bo", 0
        )
        g.accept(1)
        g.onEnvelope(MatchEnvelope(1, MATCH_GAME_ID, "S1", "A", "g", 1, 0, MatchType.GAME_START, GameStart(1, 0, 3000).encode()), 2)
        assertEquals(CloseReason.PROTOCOL, g.closeReason)
    }

    @Test fun `la invitacion sin respuesta caduca`() {
        val h = MatchMachine.host("S1", "A", "B", "Bo", ch, "Ana", 0)
        assertTrue(h.tick(MatchTimings.INVITE_TTL_MS + 1).any { it is MatchEffect.Closed })
        assertEquals(CloseReason.TIMEOUT, h.closeReason)
    }

    @Test fun `sobre de otra sesion o remitente ajeno se ignora`() {
        val h = MatchMachine.host("S1", "A", "B", "Bo", ch, "Ana", 0)
        h.onEnvelope(MatchEnvelope(1, MATCH_GAME_ID, "OTRA", "B", "x", 1, 0, MatchType.PLAYER_LEFT, "C"), 1)
        h.onEnvelope(MatchEnvelope(1, MATCH_GAME_ID, "S1", "Z", "x", 2, 0, MatchType.PLAYER_LEFT, "C"), 1)
        assertEquals(MatchPhase.INVITING, h.phase)
    }

    @Test fun `secuencia repetida o vieja no se procesa`() {
        val h = MatchMachine.host("S1", "A", "B", "Bo", ch, "Ana", 0)
        h.onEnvelope(MatchEnvelope(1, MATCH_GAME_ID, "S1", "B", "x", 5, 0, MatchType.LOBBY_ACCEPT, "Bo"), 1)
        h.onEnvelope(MatchEnvelope(1, MATCH_GAME_ID, "S1", "B", "y", 4, 0, MatchType.PLAYER_LEFT, "C"), 2)
        assertEquals(MatchPhase.LOBBY, h.phase)
    }
}

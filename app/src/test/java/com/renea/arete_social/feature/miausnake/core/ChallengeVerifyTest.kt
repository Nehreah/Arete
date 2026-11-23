package com.renea.arete_social.feature.miausnake.core

import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ChallengeVerifyTest {
    private val meta = TestLevels.open(goalBites = 12)
    private val infinito = TestLevels.open(goalBites = null)
    private val seed = 7L

    private fun legit(): ChallengeResult {
        val s = BfsBot.play(meta, seed = seed)
        return ChallengeResult(Challenge(1, 5, 'M', seed), 1, "Ana", s.state.score, s.replay().inputs)
    }

    @Test fun `un resultado legitimo es valido`() {
        val v = verifyChallengeResult(legit(), meta) as Verdict.Valid
        assertTrue(v.outcome.won)
        assertEquals(legit().claimedScore, v.outcome.score)
    }

    @Test fun `puntuacion declarada falsa se rechaza y se da la real`() {
        val r = legit()
        val v = verifyChallengeResult(r.copy(claimedScore = r.claimedScore + 10), meta) as Verdict.Rejected
        assertEquals(Rejection.SCORE_MISMATCH, v.reason)
        assertEquals(r.claimedScore, v.realOutcome!!.score)
    }

    @Test fun `reglas, version y modo incorrectos`() {
        val r = legit()
        assertEquals(Rejection.RULES_VERSION, (verifyChallengeResult(r.copy(challenge = r.challenge.copy(rules = 2)), meta) as Verdict.Rejected).reason)
        assertEquals(Rejection.LEVEL_VERSION, (verifyChallengeResult(r.copy(levelVersion = 2), meta) as Verdict.Rejected).reason)
        assertEquals(Rejection.MODE_MISMATCH, (verifyChallengeResult(r.copy(challenge = r.challenge.copy(mode = 'I')), meta) as Verdict.Rejected).reason)
        assertEquals(Rejection.MODE_MISMATCH, (verifyChallengeResult(r, infinito) as Verdict.Rejected).reason)
    }

    @Test fun `giros extra, opuestos o semilla cambiada no son validos`() {
        val r = legit()
        val ultimo = r.inputs.lastOrNull()?.tick ?: 0L
        assertTrue(verifyChallengeResult(r.copy(inputs = r.inputs + TurnInput(ultimo + 500, Direction.UP)), meta) is Verdict.Rejected)
        assertTrue(verifyChallengeResult(r.copy(inputs = listOf(TurnInput(0, Direction.LEFT)) + r.inputs), meta) is Verdict.Rejected)
        assertTrue(verifyChallengeResult(r.copy(challenge = r.challenge.copy(seed = seed + 1)), meta) !is Verdict.Valid)
    }

    @Test fun `comparar corridas`() {
        fun o(score: Int, won: Boolean, ms: Long) = RunOutcome(score, score / 10, won, 100, ms)
        assertTrue(compareRuns('M', o(120, true, 9_000), o(100, false, 5_000)) > 0)
        assertTrue(compareRuns('M', o(120, true, 9_000), o(120, true, 8_000)) < 0)
        assertTrue(compareRuns('M', o(50, false, 1), o(40, false, 1)) > 0)
        assertTrue(compareRuns('I', o(200, false, 1), o(190, false, 99)) > 0)
        assertEquals(0, compareRuns('I', o(200, false, 1), o(200, false, 99)))
        assertFalse(compareRuns('I', o(10, false, 1), o(20, false, 1)) > 0)
    }

    @Test fun `fecha UTC del reto diario`() {
        assertEquals("1970-01-01", formatUtcDay(utcDay(0)))
        assertEquals("2024-02-29", formatUtcDay(utcDay(1_709_164_800_000L)))
        assertEquals("2027-01-15", formatUtcDay(utcDay(1_800_000_000_000L)))
        assertEquals(utcDay(1_800_000_000_000L), utcDay(1_800_000_000_000L + 3_600_000L))
    }
}

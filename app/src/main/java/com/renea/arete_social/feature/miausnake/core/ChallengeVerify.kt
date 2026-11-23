package com.renea.arete_social.feature.miausnake.core

/** Lo que de verdad ocurrió al re-simular una partida (no lo que alguien declara). */
data class RunOutcome(
    val score: Int,
    val bites: Int,
    val won: Boolean,
    val ticks: Long,
    /** Tiempo VIRTUAL (suma de ticks): idéntico en cualquier teléfono. */
    val elapsedMs: Long
)

enum class Rejection { RULES_VERSION, LEVEL_VERSION, MODE_MISMATCH, REPLAY_INVALID, SCORE_MISMATCH }

sealed interface Verdict {
    data class Valid(val outcome: RunOutcome) : Verdict

    /** [realOutcome] solo viene si la repetición era legal pero la puntuación declarada no coincide. */
    data class Rejected(val reason: Rejection, val realOutcome: RunOutcome? = null) : Verdict
}

/**
 * Verifica un resultado de reto contra el mapa que le corresponde. Es CPU (hasta [ReplayCodec.MAX_TICK]
 * ticks): llamarla fuera del hilo principal. [level] debe estar construido para el modo del reto
 * (modo 'M' con meta, modo 'I' sin meta).
 *
 * Límite conocido (plan V9): prueba que la repetición es legal y el puntaje coherente, no que la
 * jugó una persona. Los retos son entre amigos, no un ranking anti-trampas.
 */
fun verifyChallengeResult(r: ChallengeResult, level: Level): Verdict {
    val c = r.challenge
    if (c.rules != RULES_VERSION) return Verdict.Rejected(Rejection.RULES_VERSION)
    if (r.levelVersion != level.version) return Verdict.Rejected(Rejection.LEVEL_VERSION)
    if ((c.mode == 'M') != (level.goalBites != null)) return Verdict.Rejected(Rejection.MODE_MISMATCH)
    val replay = Replay(level.id, level.version, RULES_VERSION, c.seed, r.inputs)
    val fin = resimulate(replay, level) ?: return Verdict.Rejected(Rejection.REPLAY_INVALID)
    val out = RunOutcome(fin.score, fin.foodEaten, fin.status == GameStatus.Won, fin.tick, fin.elapsedMs)
    return if (fin.score == r.claimedScore) Verdict.Valid(out) else Verdict.Rejected(Rejection.SCORE_MISMATCH, out)
}

/**
 * Compara dos partidas del mismo reto. Positivo si [a] es mejor, negativo si lo es [b], 0 empate.
 * Meta: ganar vence a perder; entre ganadoras, menos tiempo virtual; entre perdedoras, más puntos.
 * Infinito: más puntos.
 */
fun compareRuns(mode: Char, a: RunOutcome, b: RunOutcome): Int =
    if (mode == 'M') {
        when {
            a.won != b.won -> if (a.won) 1 else -1
            a.won -> b.elapsedMs.compareTo(a.elapsedMs)
            else -> a.score.compareTo(b.score)
        }
    } else {
        a.score.compareTo(b.score)
    }

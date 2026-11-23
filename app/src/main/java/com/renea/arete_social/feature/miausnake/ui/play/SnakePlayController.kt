package com.renea.arete_social.feature.miausnake.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.renea.arete_social.feature.miausnake.core.Cell
import com.renea.arete_social.feature.miausnake.core.Direction
import com.renea.arete_social.feature.miausnake.core.GameSession
import com.renea.arete_social.feature.miausnake.core.GameStatus
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.ui.loop.FixedStepLoop
import com.renea.arete_social.feature.miausnake.ui.loop.FrameStats
import com.renea.arete_social.feature.miausnake.ui.render.Mood

/** Fase de la pantalla de partida (UI). El estado de las reglas vive en [GameSession]. */
enum class PlayPhase { Countdown, Playing, Paused, Finished }

enum class PlayEvent { Bite, Crash, Won }

/**
 * Orquesta una partida: cuenta atrás, loop de paso fijo, pausa y fin. Sin lógica de reglas
 * (eso es del core) y sin dibujo (eso es del renderer). Un objeto por partida: reiniciar crea otro.
 *
 * Se mueve con [onFrame] (un frame de pantalla). Pausa y segundo plano: la pantalla llama [pause];
 * reanudar es una acción explícita del jugador ([requestResume]) seguida de una cuenta atrás.
 */
class SnakePlayController(
    val level: Level,
    seed: Long,
    private val countdownStepNs: Long = COUNTDOWN_STEP_NS,
    private val eatingMoodNs: Long = EATING_MOOD_NS
) {
    val session = GameSession(level, seed)

    var phase by mutableStateOf(PlayPhase.Countdown)
        private set

    /** 3, 2, 1 durante la cuenta atrás. */
    var countdown by mutableIntStateOf(COUNTDOWN_FROM)
        private set

    /** Valores del HUD: solo cambian al tick (no al frame). */
    var score by mutableIntStateOf(0)
        private set
    var length by mutableIntStateOf(session.state.snake.size)
        private set
    var bites by mutableIntStateOf(0)
        private set

    /** Se lee DENTRO del dibujo para invalidar solo el Canvas, sin recomponer. */
    var frame by mutableLongStateOf(0L)
        private set

    var mood: Mood = Mood.NORMAL
        private set

    /** Cola antes del último tick, para que el render retraiga la cola. */
    var prevTail: Cell? = null
        private set

    val frameStats = FrameStats()
    var onEvent: (PlayEvent) -> Unit = {}

    /**
     * true desde el inicio o una reanudación hasta el primer tick siguiente: el render muestra la
     * pose completa (sin interpolar) para que la serpiente no «salte» hacia atrás al reanudar.
     */
    var snapPose: Boolean = true
        private set

    private var dirty = true
    private var countdownAccNs = 0L
    private var eatingUntilNs = 0L
    private var nowNs = 0L

    private val loop = FixedStepLoop(
        tickNs = { level.speed.tickMs(session.state.foodEaten) * 1_000_000L },
        onTick = ::onTick,
        isActive = { phase == PlayPhase.Playing }
    )

    val alpha: Float get() = if (phase == PlayPhase.Playing || phase == PlayPhase.Paused) loop.alpha else 1f

    /** Un frame de pantalla. [dtNs] es el tiempo desde el frame anterior. */
    fun onFrame(nowNs: Long, dtNs: Long) {
        this.nowNs = nowNs
        frameStats.record(dtNs)
        when (phase) {
            PlayPhase.Countdown -> advanceCountdown(dtNs)
            PlayPhase.Playing -> loop.advance(dtNs)
            else -> Unit
        }
        mood = when {
            session.state.status == GameStatus.GameOver -> Mood.DEAD
            nowNs < eatingUntilNs -> Mood.EATING
            else -> Mood.NORMAL
        }
        // En pausa o al terminar no se redibuja cada frame: solo cuando algo cambió.
        if (dirty || phase == PlayPhase.Countdown || phase == PlayPhase.Playing) {
            frame = nowNs
            dirty = false
        }
    }

    /** Un giro del jugador (swipe o cruceta). Se ignora en pausa y al terminar. */
    fun turn(d: Direction) {
        if (phase == PlayPhase.Paused || phase == PlayPhase.Finished) return
        session.turn(d)
    }

    /** Pausa (botón, segundo plano o pérdida de foco). No reanuda sola. */
    fun pause() {
        when (phase) {
            PlayPhase.Playing -> {
                session.pause()
                phase = PlayPhase.Paused
            }
            PlayPhase.Countdown -> phase = PlayPhase.Paused
            else -> return
        }
        dirty = true
        // El loop NO se reinicia: así [alpha] queda congelado y la pose no cambia al pausar.
    }

    /** El jugador pide seguir: cuenta atrás y luego se reanuda. */
    fun requestResume() {
        if (phase != PlayPhase.Paused) return
        beginCountdown()
    }

    private fun beginCountdown() {
        countdown = COUNTDOWN_FROM
        countdownAccNs = 0L
        loop.reset()
        snapPose = true
        phase = PlayPhase.Countdown
    }

    private fun advanceCountdown(dtNs: Long) {
        countdownAccNs += minOf(dtNs, MAX_COUNTDOWN_DT_NS)
        while (countdownAccNs >= countdownStepNs) {
            countdownAccNs -= countdownStepNs
            countdown -= 1
            if (countdown <= 0) {
                session.resume() // no hace nada si la partida no estaba en pausa
                loop.reset()
                phase = PlayPhase.Playing
                return
            }
        }
    }

    private fun onTick() {
        snapPose = false
        val before = session.state
        prevTail = before.snake.last()
        session.tick()
        val after = session.state
        score = after.score
        length = after.snake.size
        bites = after.foodEaten
        if (after.foodEaten > before.foodEaten) {
            eatingUntilNs = nowNs + eatingMoodNs
            if (after.status != GameStatus.Won) onEvent(PlayEvent.Bite)
        }
        when (after.status) {
            GameStatus.Won -> finish(PlayEvent.Won)
            GameStatus.GameOver -> finish(PlayEvent.Crash)
            else -> Unit
        }
    }

    private fun finish(event: PlayEvent) {
        phase = PlayPhase.Finished
        dirty = true
        onEvent(event)
    }

    companion object {
        const val COUNTDOWN_FROM = 3
        const val COUNTDOWN_STEP_NS = 700_000_000L
        const val EATING_MOOD_NS = 220_000_000L
        private const val MAX_COUNTDOWN_DT_NS = 250_000_000L
    }
}

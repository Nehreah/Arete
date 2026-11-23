package com.renea.arete_social.feature.miausnake.ui.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.renea.arete_social.feature.miausnake.core.GhostTrack
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.Replay
import com.renea.arete_social.feature.miausnake.ui.input.DirectionPad
import com.renea.arete_social.feature.miausnake.ui.input.swipeControls
import com.renea.arete_social.feature.miausnake.ui.render.BoardLayout
import com.renea.arete_social.feature.miausnake.ui.render.GhostDraw
import com.renea.arete_social.feature.miausnake.ui.render.RenderModel
import com.renea.arete_social.feature.miausnake.ui.render.drawGame
import com.renea.arete_social.feature.miausnake.ui.theme.GameTheme
import com.renea.arete_social.feature.miausnake.ui.theme.ProgrammaticTheme
import kotlinx.coroutines.delay

/**
 * Pantalla de partida. No sabe nada de Michi: quien la aloja (Fase 3, `MiauSnakeEnMichi`) recibe el
 * resultado por [onResult] y decide qué hacer con él a través del puente.
 *
 * Zonas: HUD arriba, tablero centrado, controles abajo; los overlays (cuenta atrás, pausa,
 * resultado) se dibujan encima sin recolocar el tablero. Segundo plano: al salir de RESUMED se
 * pausa y se detiene el loop; al volver hay que pulsar «Continuar» (cuenta atrás de 3 s).
 *
 * @param onFinished se llama una vez por partida con (puntos, ¿victoria?).
 * @param onResult se llama una vez por partida con el resultado completo (incluye la repetición).
 * @param onRestart si no es null, «Reiniciar» y «Jugar otra vez» lo llaman en lugar de reiniciar aquí
 *   (el anfitrión decide si la partida nueva abre otra sesión).
 * @param resultFooter contenido extra bajo el resultado (p. ej. lo que ganó Michi).
 * @param bestScore mejor puntuación de esta sesión para este mapa (solo informativa).
 * @param seed semilla fija (retos). null = aleatoria. En modo Meta el mapa ya trae su semilla fija.
 * @param ghost recorrido de un rival, dibujado como sombra (retos). No cambia ninguna regla.
 */
@Composable
fun MiauSnakePlayScreen(
    level: Level,
    settings: PlaySettings,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    bestScore: Int = 0,
    onFinished: (score: Int, won: Boolean) -> Unit = { _, _ -> },
    onResult: (PlayResult) -> Unit = {},
    onRestart: (() -> Unit)? = null,
    resultFooter: (@Composable ColumnScope.() -> Unit)? = null,
    seed: Long? = null,
    ghost: GhostTrack? = null,
    theme: GameTheme = remember { ProgrammaticTheme() }
) {
    val palette = theme.palette
    var runId by remember { mutableIntStateOf(0) }
    // Retos: la semilla viene del reto (la misma para todos); en juego normal es aleatoria.
    val controller = remember(level, runId, seed) { SnakePlayController(level, seed ?: newSeed()) }
    val ghostDraw = remember(ghost, level) {
        ghost?.let { GhostDraw(it, palette.ghost, IntArray(level.cols * level.rows)) }
    }
    val model = remember(level) { RenderModel(level) }
    val bestAtStart = remember(controller) { bestScore }

    // --- Vibración y fin de partida ---
    val haptic = LocalHapticFeedback.current
    val vibrate by rememberUpdatedState(settings.vibration)
    val finished by rememberUpdatedState(onFinished)
    val result by rememberUpdatedState(onResult)
    val restartHost by rememberUpdatedState(onRestart)
    val restart: () -> Unit = {
        val host = restartHost
        if (host != null) host() else runId++
    }
    SideEffect {
        controller.onEvent = { e ->
            when (e) {
                PlayEvent.Bite -> if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PlayEvent.Crash -> {
                    if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    finished(controller.score, false)
                    result(controller.toResult(won = false))
                }
                PlayEvent.Won -> {
                    if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    finished(controller.score, true)
                    result(controller.toResult(won = true))
                }
            }
        }
    }

    // --- Ciclo de vida: el loop solo corre en RESUMED; al salir se pausa ---
    var resumed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(controller) {
        resumed = true
        onPauseOrDispose {
            resumed = false
            controller.pause()
        }
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // Solo se pausa al PERDER el foco (no si la ventana aún no lo ha recibido al abrir la pantalla).
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(controller, windowFocused) {
        if (windowFocused) hadFocus = true else if (hadFocus) controller.pause()
    }
    LaunchedEffect(controller, resumed) {
        if (!resumed) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0L else now - last
                last = now
                controller.onFrame(now, dt)
            }
        }
    }
    BackHandler(enabled = controller.phase == PlayPhase.Playing || controller.phase == PlayPhase.Countdown) {
        controller.pause()
    }

    // --- Panel de depuración del spike (FPS, tirones, tamaño real de celda) ---
    val density = LocalDensity.current.density
    val lastCellPx = remember { IntArray(1) }
    var debugText by remember { mutableStateOf("") }
    LaunchedEffect(controller, settings.showDebug) {
        while (settings.showDebug) {
            delay(500)
            val s = controller.frameStats.summary()
            val cellDp = lastCellPx[0] / density
            debugText = "FPS %.1f · medio %.1f ms · peor %.0f ms · tirones %d/%d %s\n".format(
                s.avgFps, s.avgMs, s.worstMs, s.jankFrames, s.samples, if (s.isStable60) "OK" else "…"
            ) + "celda %d px = %.1f dp · rejilla %d×%d".format(lastCellPx[0], cellDp, level.cols, level.rows)
        }
    }

    val swipeThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val onDirection = remember(controller) { { d: com.renea.arete_social.feature.miausnake.core.Direction -> controller.turn(d) } }

    Column(
        modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding()
            .swipeControls(swipeThreshold, enabled = settings.control == ControlMode.SWIPE, onDirection = onDirection)
    ) {
        Hud(controller, level, settings, palette)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxSize().semantics {
                    // Fase 7: el tablero no es solo un dibujo mudo; informa del estado de la partida.
                    contentDescription = "Tablero de Miau Snake"
                    stateDescription = "${controller.score} puntos, largo ${controller.length}"
                }
            ) {
                // Leer el contador aquí invalida solo el dibujo, no recompone.
                if (controller.frame < 0L) return@Canvas
                val layout = BoardLayout.compute(size.width, size.height, level.cols, level.rows)
                lastCellPx[0] = layout.cellPx
                ghostDraw?.let { gd ->
                    // La sombra va por TIEMPO VIRTUAL: el de quien juega (ticks ya jugados + avance dentro del tick).
                    val st = controller.session.state
                    val inTick = if (controller.phase == PlayPhase.Playing || controller.phase == PlayPhase.Paused) {
                        (controller.alpha * level.speed.tickMs(st.foodEaten)).toLong()
                    } else {
                        0L
                    }
                    gd.tick = gd.track.tickAt(st.elapsedMs + inTick)
                }
                drawGame(
                    model = model, state = controller.session.state, layout = layout, theme = theme,
                    alpha = controller.alpha, prevTail = controller.prevTail, mood = controller.mood,
                    interpolate = settings.interpolate && !controller.snapPose,
                    ghost = ghostDraw
                )
            }

            if (settings.showDebug && debugText.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xAA000000)).padding(4.dp)) {
                    SnakeText(debugText, palette, size = 11)
                }
            }

            when (controller.phase) {
                PlayPhase.Countdown -> BasicText(
                    controller.countdown.toString(),
                    Modifier.align(Alignment.Center),
                    style = TextStyle(color = palette.food, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                )
                PlayPhase.Paused -> Scrim {
                    SnakeText("PAUSA", palette, size = 26, bold = true)
                    Spacer(Modifier.height(16.dp))
                    SnakeButton("Continuar", { controller.requestResume() }, palette, primary = true)
                    Spacer(Modifier.height(8.dp))
                    SnakeButton("Reiniciar", restart, palette)
                    Spacer(Modifier.height(8.dp))
                    SnakeButton("Salir", onExit, palette)
                }
                PlayPhase.Finished -> {
                    val won = controller.session.state.status == com.renea.arete_social.feature.miausnake.core.GameStatus.Won
                    Scrim {
                        SnakeText(if (won) "¡META LOGRADA!" else "FIN DE LA PARTIDA", palette, size = 24, bold = true)
                        Spacer(Modifier.height(8.dp))
                        SnakeText("${controller.score}", palette, size = 56, bold = true)
                        SnakeText("puntos · ${controller.bites} bocados · largo ${controller.length}", palette, size = 14)
                        SnakeText(
                            if (controller.score > bestAtStart) "¡Nuevo récord de la sesión!" else "Récord de la sesión: $bestAtStart",
                            palette, size = 13, dim = true
                        )
                        resultFooter?.let {
                            Spacer(Modifier.height(12.dp))
                            it()
                        }
                        Spacer(Modifier.height(16.dp))
                        SnakeButton("Jugar otra vez", restart, palette, primary = true)
                        Spacer(Modifier.height(8.dp))
                        SnakeButton("Salir", onExit, palette)
                    }
                }
                PlayPhase.Playing -> Unit
            }
        }

        Controls(settings, palette, onDirection)
    }
}

@Composable
private fun Hud(controller: SnakePlayController, level: Level, settings: PlaySettings, palette: com.renea.arete_social.feature.miausnake.ui.theme.GamePalette) {
    val goal = level.goalBites
    val pause: @Composable () -> Unit = {
        PauseButton(palette) { controller.pause() }
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (settings.leftHanded) pause()
        Column(Modifier.weight(1f)) {
            SnakeText("PUNTOS ${controller.score}", palette, size = 16, bold = true)
            SnakeText(
                (if (goal != null) "BOCADOS ${controller.bites}/$goal" else "BOCADOS ${controller.bites}") + " · LARGO ${controller.length}",
                palette, size = 12, dim = true
            )
        }
        if (!settings.leftHanded) pause()
    }
}

@Composable
private fun PauseButton(palette: com.renea.arete_social.feature.miausnake.ui.theme.GamePalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.panel)
            .clickable(onClickLabel = "Pausar", onClick = onClick)
            .semantics { contentDescription = "Pausa" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width * 0.32f
            drawRect(palette.head, Offset(size.width * 0.12f, 0f), Size(w, size.height))
            drawRect(palette.head, Offset(size.width * 0.56f, 0f), Size(w, size.height))
        }
    }
}

@Composable
private fun Controls(
    settings: PlaySettings,
    palette: com.renea.arete_social.feature.miausnake.ui.theme.GamePalette,
    onDirection: (com.renea.arete_social.feature.miausnake.core.Direction) -> Unit
) {
    if (settings.control == ControlMode.DPAD) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            DirectionPad(
                onDirection = onDirection, palette = palette,
                modifier = Modifier.align(if (settings.leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
            )
        }
    } else {
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            SnakeText("Desliza para girar", palette, size = 12, dim = true)
        }
    }
}

@Composable
private fun Scrim(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        // Fase 7: al aparecer pausa / resultado, el lector de pantalla lo lee entero y sin cortar al jugador.
        Modifier.fillMaxSize().background(Color(0xCC171B14))
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

private fun newSeed(): Long = System.nanoTime() and 0xFFFFFFL

/** Resultado completo de una partida terminada. La repetición permite verificarla re-simulando. */
class PlayResult(val score: Int, val bites: Int, val won: Boolean, val replay: Replay)

private fun SnakePlayController.toResult(won: Boolean) =
    PlayResult(score = score, bites = bites, won = won, replay = session.replay())

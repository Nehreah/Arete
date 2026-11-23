package com.renea.arete_social.feature.miausnake.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.RULES_VERSION
import com.renea.arete_social.feature.miausnake.match.CloseReason
import com.renea.arete_social.feature.miausnake.match.MatchCoordinator
import com.renea.arete_social.feature.miausnake.match.MatchPhase
import com.renea.arete_social.feature.miausnake.match.MatchRole
import com.renea.arete_social.feature.miausnake.match.MatchView
import com.renea.arete_social.feature.miausnake.match.MatchVerdict
import com.renea.arete_social.feature.miausnake.match.NearbyPeer
import com.renea.arete_social.feature.miausnake.match.SpikeState
import com.renea.arete_social.feature.miausnake.social.LevelResolver
import com.renea.arete_social.feature.miausnake.ui.challenge.ChallengeText
import com.renea.arete_social.feature.miausnake.ui.play.MenuSection
import com.renea.arete_social.feature.miausnake.ui.play.MiauSnakePlayScreen
import com.renea.arete_social.feature.miausnake.ui.play.PlaySettings
import com.renea.arete_social.feature.miausnake.ui.play.SnakeButton
import com.renea.arete_social.feature.miausnake.ui.play.SnakeText
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette
import com.renea.arete_social.feature.miausnake.ui.theme.GameTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Partida cercana (Fase 6): un duelo por resultados con un teléfono que esté cerca. Cada uno juega SU tablero
 * con el mismo mapa, modo y semilla; los resultados se comparan por re-simulación. No hay recompensa para Michi
 * ni se tocan los récords del mapa. Nada empieza sin una acción explícita, y mientras esta pantalla está cerrada
 * el teléfono no escucha invitaciones ni busca gente.
 */
@Composable
fun MatchHub(
    palette: GamePalette,
    theme: GameTheme,
    settings: PlaySettings,
    coordinator: MatchCoordinator,
    nearby: Flow<List<NearbyPeer>>,
    resolver: LevelResolver,
    selectedMapNo: Int,
    selectedMode: Char,
    onClose: () -> Unit
) {
    DisposableEffect(coordinator) {
        coordinator.start()
        onDispose { coordinator.stop() }
    }
    val view by coordinator.session.collectAsState()
    val linked by coordinator.linkedPeers.collectAsState()
    val spike by coordinator.spike.collectAsState()
    val notice by coordinator.notice.collectAsState()
    val ledger by coordinator.ledger.collectAsState()
    val buscando = view == null && spike !is SpikeState.Running
    val fuente = remember(buscando) { if (buscando) nearby else emptyFlow() }
    val peers by fuente.collectAsState(initial = emptyList())

    val v = view
    BackHandler(enabled = true) {
        when {
            v == null -> if (spike is SpikeState.Done) coordinator.closeSpike() else onClose()
            v.phase == MatchPhase.INVITED -> coordinator.decline()
            v.phase == MatchPhase.CLOSED || v.phase == MatchPhase.FINISHED -> coordinator.dismiss()
            else -> coordinator.leave()
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        val level = if (v != null) remember(v.challenge) { runCatching { resolver.resolve(v.challenge.mapNo, v.challenge.mode) }.getOrNull() } else null
        if (v != null && v.phase == MatchPhase.PLAYING && v.myRun == null && level != null) {
            key(v.sessionId) {
                MiauSnakePlayScreen(
                    level = level,
                    settings = settings,
                    onExit = { coordinator.leave() },
                    seed = v.challenge.seed,
                    onResult = { r -> coordinator.playerFinished(r.score, r.replay.inputs) },
                    resultFooter = { SnakeText("Enviando tu resultado a ${v.peerName}…", palette, size = 13, dim = true) },
                    theme = theme
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                notice?.let { Caja(it, palette) }
                when {
                    v == null -> Inicio(
                        palette, coordinator, peers, linked, spike, ledger.entries.size,
                        ledger.wins(), ledger.losses(), ledger.ties(), selectedMapNo, selectedMode, onClose
                    )
                    else -> Sesion(palette, v, coordinator)
                }
            }
        }
    }
}

@Composable
private fun Caja(texto: String, palette: GamePalette) {
    Box(Modifier.fillMaxWidth().background(palette.panel).padding(12.dp), contentAlignment = Alignment.CenterStart) {
        SnakeText(texto, palette, size = 13)
    }
}

@Composable
private fun Inicio(
    palette: GamePalette,
    coordinator: MatchCoordinator,
    peers: List<NearbyPeer>,
    linked: Int,
    spike: SpikeState,
    jugados: Int,
    ganados: Int,
    perdidos: Int,
    empates: Int,
    mapNo: Int,
    mode: Char,
    onClose: () -> Unit
) {
    val habilitada = coordinator.gateOpen()
    SnakeText("PARTIDA CERCANA", palette, size = 24, bold = true)
    SnakeText(
        "Juega el mismo mapa y la misma semilla que un amigo que esté cerca, a la vez. Cada uno juega su tablero y " +
            "gana quien lo haga mejor: se comparan las repeticiones, no el reloj ni la conexión.",
        palette, size = 13, dim = true
    )
    SnakeText(
        if (linked > 0) "Teléfonos conectados ahora: $linked." else "Ningún teléfono conectado por Bluetooth o Wi‑Fi todavía.",
        palette, size = 13
    )
    if (jugados > 0) SnakeText("Tu historial: $ganados ganadas · $perdidos perdidas · $empates empates", palette, size = 12, dim = true)

    if (spike is SpikeState.Running) {
        MenuSection("Probando la conexión", palette)
        SnakeText("Enviando prueba ${spike.sent} de ${spike.total}… no cierres esta pantalla.", palette, size = 13)
    } else {
        if (spike is SpikeState.Done) {
            MenuSection("Resultado de la prueba", palette)
            Caja(spike.report.summary(), palette)
            SnakeButton("Cerrar resultado", { coordinator.closeSpike() }, palette, Modifier.fillMaxWidth())
        } else {
            coordinator.lastSpike()?.let {
                SnakeText("Última prueba: " + (if (it.passed) "apta" else "no apta") + " (${it.deliveryPercent} % de entrega, RTT mediana ${it.medianMs} ms).", palette, size = 12, dim = true)
            }
        }

        MenuSection("Gente cerca", palette)
        if (peers.isEmpty()) {
            SnakeText("No se ve a nadie todavía. La otra persona debe tener esta pantalla abierta.", palette, size = 13, dim = true)
        }
        if (!habilitada) {
            Caja(
                "Antes de invitar hay que comprobar que la conexión entre los dos teléfonos aguanta una partida. " +
                    "La otra persona debe estar en esta pantalla. La prueba dura unos 15 segundos.",
                palette
            )
        } else {
            SnakeText("Mapa $mapNo · ${ChallengeText.modeName(mode)} (se eligen en el menú anterior).", palette, size = 12, dim = true)
        }
        for (p in peers) {
            if (habilitada) {
                SnakeButton("Invitar a ${p.displayName}", {
                    val semilla = (System.nanoTime() xor System.currentTimeMillis()) and 0xFFFFFFL
                    coordinator.invite(p, Challenge(RULES_VERSION, mapNo, mode, semilla))
                }, palette, Modifier.fillMaxWidth(), primary = true)
            } else {
                SnakeButton("Probar la conexión con ${p.displayName}", { coordinator.runSpike(p) }, palette, Modifier.fillMaxWidth(), primary = true)
            }
        }
        if (habilitada && peers.isNotEmpty()) {
            SnakeButton("Volver a probar la conexión (${peers.first().displayName})", { coordinator.runSpike(peers.first()) }, palette, Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(8.dp))
    SnakeButton("Volver", onClose, palette, Modifier.fillMaxWidth())
}

@Composable
private fun Sesion(palette: GamePalette, v: MatchView, c: MatchCoordinator) {
    val rival = v.peerName
    val reto = "Mapa ${v.challenge.mapNo} · ${ChallengeText.modeName(v.challenge.mode)}"
    when (v.phase) {
        MatchPhase.INVITING -> {
            SnakeText("INVITACIÓN ENVIADA", palette, size = 22, bold = true)
            SnakeText("Esperando a que $rival acepte · $reto", palette, size = 14)
            SnakeText("Si no responde en un minuto, la invitación se cancela sola.", palette, size = 12, dim = true)
            SnakeButton("Cancelar", { c.leave() }, palette, Modifier.fillMaxWidth())
        }
        MatchPhase.INVITED -> {
            SnakeText("TE INVITAN A JUGAR", palette, size = 22, bold = true)
            SnakeText("$rival te invita · $reto", palette, size = 14)
            SnakeText("Cada uno juega su tablero con la misma semilla. No gasta nada de Michi ni cambia tus récords.", palette, size = 12, dim = true)
            SnakeButton("Aceptar", { c.accept() }, palette, Modifier.fillMaxWidth(), primary = true)
            SnakeButton("Rechazar", { c.decline() }, palette, Modifier.fillMaxWidth())
        }
        MatchPhase.LOBBY -> {
            SnakeText("SALA · $reto", palette, size = 22, bold = true)
            if (v.role == MatchRole.GUEST) {
                SnakeText(if (v.myReady) "Listo. Esperando a que $rival empiece…" else "$rival te espera.", palette, size = 14)
                if (!v.myReady) SnakeButton("Estoy listo", { c.ready() }, palette, Modifier.fillMaxWidth(), primary = true)
            } else {
                SnakeText(if (v.peerReady) "$rival está listo." else "Esperando a que $rival esté listo…", palette, size = 14)
                SnakeButton(
                    "Empezar", { if (v.canStart) c.ready() }, palette, Modifier.fillMaxWidth(), primary = v.canStart
                )
            }
            SnakeButton("Salir", { c.leave() }, palette, Modifier.fillMaxWidth())
        }
        MatchPhase.COUNTDOWN -> {
            val s = ((v.countdownLeftMs + 999) / 1000).coerceAtLeast(1)
            SnakeText("¡PREPÁRATE!", palette, size = 22, bold = true)
            SnakeText("$s", palette, size = 64, bold = true)
            SnakeText("$reto · contra $rival", palette, size = 13, dim = true)
        }
        MatchPhase.PLAYING -> {
            SnakeText("Preparando la partida…", palette, size = 14, dim = true)
            SnakeButton("Salir", { c.leave() }, palette, Modifier.fillMaxWidth())
        }
        MatchPhase.WAITING_PEER -> {
            val mine = v.myRun
            SnakeText("TERMINASTE", palette, size = 22, bold = true)
            if (mine != null) SnakeText(ChallengeText.summary(v.challenge, mine.outcome), palette, size = 14)
            SnakeText(
                when {
                    v.resultTooLong -> "Tu partida es demasiado larga para enviarla por cercanía. Queda guardada en Retos para compartirla por texto o QR."
                    v.peerAcceptedMine == true -> "$rival recibió tu resultado. Esperando el suyo…"
                    else -> "Esperando el resultado de $rival…"
                },
                palette, size = 13
            )
            SnakeText("Tu resultado se envía solo y se repite hasta que llegue. Si sales, queda guardado en Retos.", palette, size = 12, dim = true)
            SnakeButton("Salir", { c.leave() }, palette, Modifier.fillMaxWidth())
        }
        MatchPhase.FINISHED -> Final(palette, v, c)
        MatchPhase.CLOSED -> {
            SnakeText("SE CERRÓ LA SALA", palette, size = 22, bold = true)
            SnakeText(MatchTexts.closed(v.closeReason, rival), palette, size = 14)
            v.myRun?.let { SnakeText("Tu resultado (${it.outcome.score} pts) quedó guardado en Retos.", palette, size = 13) }
            SnakeButton("Volver", { c.dismiss() }, palette, Modifier.fillMaxWidth(), primary = true)
        }
    }
}

@Composable
private fun Final(palette: GamePalette, v: MatchView, c: MatchCoordinator) {
    val mine = v.myRun
    val theirs = v.peerRun
    SnakeText(
        when (v.verdict) {
            MatchVerdict.WON -> "¡GANASTE!"
            MatchVerdict.LOST -> "PERDISTE"
            MatchVerdict.TIE -> "EMPATE"
            MatchVerdict.PEER_INVALID -> "RESULTADO RECHAZADO"
            MatchVerdict.PENDING -> "TERMINÓ"
        },
        palette, size = 26, bold = true
    )
    if (mine != null) SnakeText("Tú: " + ChallengeText.summary(v.challenge, mine.outcome), palette, size = 14)
    if (theirs != null) SnakeText("${v.peerName}: " + ChallengeText.summary(v.challenge, theirs.outcome), palette, size = 14)
    if (v.verdict == MatchVerdict.PEER_INVALID) {
        SnakeText("El resultado de ${v.peerName} no pasó la re-simulación en tu teléfono (o era de otro reto): no cuenta.", palette, size = 13)
    } else {
        SnakeText("Tu teléfono repitió la partida de ${v.peerName} paso a paso: no se fía de lo que dice el mensaje. (Prueba que la repetición es legal, no que la jugó una persona.)", palette, size = 12, dim = true)
    }
    if (v.peerAcceptedMine == null) {
        SnakeText("${v.peerName} aún no confirmó haber recibido tu resultado; se sigue enviando mientras no salgas.", palette, size = 12, dim = true)
    } else if (v.peerAcceptedMine == false) {
        SnakeText("${v.peerName} rechazó tu resultado al re-simularlo.", palette, size = 12)
    }
    SnakeText("Sin recompensa para Michi ni cambios en los récords.", palette, size = 12, dim = true)
    SnakeButton("Volver", { c.dismiss() }, palette, Modifier.fillMaxWidth(), primary = true)
}

internal object MatchTexts {
    fun closed(why: CloseReason?, rival: String): String = when (why) {
        CloseReason.DECLINED_BY_ME -> "Rechazaste la invitación."
        CloseReason.DECLINED_BY_PEER -> "$rival no quiso jugar ahora."
        CloseReason.CANCELLED_BY_ME -> "Saliste de la sala."
        CloseReason.CANCELLED_BY_PEER -> "$rival salió de la sala."
        CloseReason.BUSY -> "$rival ya está en otra partida."
        CloseReason.TIMEOUT -> "Nadie respondió a tiempo (o $rival no tiene abierta esta pantalla)."
        CloseReason.PROTOCOL -> "Algo no cuadró entre los dos teléfonos (¿versiones distintas?). Inténtalo de nuevo."
        null -> "La sala terminó."
    }
}

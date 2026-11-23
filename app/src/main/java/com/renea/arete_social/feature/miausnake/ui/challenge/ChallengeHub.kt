package com.renea.arete_social.feature.miausnake.ui.challenge

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.feature.miausnake.core.Challenge
import com.renea.arete_social.feature.miausnake.core.ChallengePacket
import com.renea.arete_social.feature.miausnake.core.ChallengeResult
import com.renea.arete_social.feature.miausnake.core.GhostTrack
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.PacketDecode
import com.renea.arete_social.feature.miausnake.core.RULES_VERSION
import com.renea.arete_social.feature.miausnake.core.Replay
import com.renea.arete_social.feature.miausnake.core.RunOutcome
import com.renea.arete_social.feature.miausnake.core.Verdict
import com.renea.arete_social.feature.miausnake.core.buildGhost
import com.renea.arete_social.feature.miausnake.core.compareRuns
import com.renea.arete_social.feature.miausnake.core.dailyChallengeFor
import com.renea.arete_social.feature.miausnake.core.formatUtcDay
import com.renea.arete_social.feature.miausnake.core.utcDay
import com.renea.arete_social.feature.miausnake.core.verifyChallengeResult
import com.renea.arete_social.feature.miausnake.data.SocialStore
import com.renea.arete_social.feature.miausnake.social.ChallengeBook
import com.renea.arete_social.feature.miausnake.social.ChallengeImporter
import com.renea.arete_social.feature.miausnake.social.ChallengeRole
import com.renea.arete_social.feature.miausnake.social.ImportOutcome
import com.renea.arete_social.feature.miausnake.social.LevelResolver
import com.renea.arete_social.feature.miausnake.ui.play.MenuSection
import com.renea.arete_social.feature.miausnake.ui.play.MiauSnakePlayScreen
import com.renea.arete_social.feature.miausnake.ui.play.PlayResult
import com.renea.arete_social.feature.miausnake.ui.play.PlaySettings
import com.renea.arete_social.feature.miausnake.ui.play.SnakeButton
import com.renea.arete_social.feature.miausnake.ui.play.SnakeText
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette
import com.renea.arete_social.feature.miausnake.ui.theme.GameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FIELD_MAX_CHARS = 24_000

private sealed interface Vista {
    data object Inicio : Vista
    data object Trabajando : Vista
    data class Recibido(val salida: ImportOutcome) : Vista
    data class Jugando(val plan: Plan) : Vista
    data class Resultado(val plan: Plan) : Vista
}

/** Lo que se va a jugar: el reto, su mapa y, si hay, la sombra del rival. */
private class Plan(val challenge: Challenge, val level: Level, val diario: Boolean, val rival: Rival?)

private class Rival(val name: String, val outcome: RunOutcome, val ghost: GhostTrack, val entryId: String)

/** Una partida propia YA verificada por re-simulación, con su paquete listo para compartir. */
private class Intento(val outcome: RunOutcome, val result: ChallengeResult, val packet: String)

/**
 * Retos asíncronos (Fase 5). Todo ocurre aquí, aparte de la sesión con Michi: **jugar un reto nunca
 * da recompensa a Michi ni toca los récords del mapa** (los retos usan semillas ajenas; tienen su
 * propio libro). Lo recibido jamás se cree: se re-simula en segundo plano antes de enseñarlo.
 *
 * Entradas de un reto: pegar texto, abrir un archivo o leer un QR. Salidas: texto, archivo o QR.
 * El envío diferido por la malla de Aréte va por [com.renea.arete_social.feature.miausnake.social.ChallengeTransport].
 */
@Composable
fun ChallengeHub(
    palette: GamePalette,
    theme: GameTheme,
    settings: PlaySettings,
    resolver: LevelResolver,
    store: SocialStore,
    playerName: String,
    selectedMapNo: Int,
    selectedMode: Char,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vista by remember { mutableStateOf<Vista>(Vista.Inicio) }
    var libro by remember { mutableStateOf(store.loadChallenges()) }
    var diario by remember { mutableStateOf(store.loadDaily()) }
    var mejor by remember { mutableStateOf<Intento?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var texto by remember { mutableStateOf("") }
    var generacion by remember { mutableIntStateOf(0) } // invalida trabajos en segundo plano al salir
    val nombre = remember(playerName) { ChallengeText.playerName(playerName).ifBlank { "Jugador" } }

    fun hoy(): Long = utcDay(System.currentTimeMillis())

    fun jugar(plan: Plan, conservarMejor: Boolean) {
        if (!conservarMejor) mejor = null
        aviso = null
        vista = Vista.Jugando(plan)
    }

    fun jugarReto(c: Challenge, esDiario: Boolean) {
        val lv = resolver.resolve(c.mapNo, c.mode)
        if (lv == null) aviso = "Ese mapa no está disponible en este teléfono." else jugar(Plan(c, lv, esDiario, null), false)
    }

    fun importar(t: String) {
        if (t.isBlank()) {
            aviso = "No hay nada que importar."
            return
        }
        aviso = null
        vista = Vista.Trabajando
        val mia = ++generacion
        scope.launch {
            val salida = withContext(Dispatchers.Default) { ChallengeImporter.import(t, resolver) }
            if (mia == generacion) vista = Vista.Recibido(salida)
        }
    }

    fun guardarEnLibro(role: ChallengeRole, result: ChallengeResult, myScore: Int?) {
        val packet = runCatching { ChallengePacket.encode(result) }.getOrNull() ?: return
        if (!ChallengeBook.canStore(packet)) {
            aviso = "Es demasiado largo para guardarlo, pero puedes jugarlo ahora."
            return
        }
        libro = libro.add(role, packet, myScore, hoy())
        store.saveChallenges(libro)
    }

    fun contraSombra(v: ImportOutcome.Verified) {
        vista = Vista.Trabajando
        val mia = ++generacion
        scope.launch {
            val rep = Replay(v.level.id, v.level.version, RULES_VERSION, v.result.challenge.seed, v.result.inputs)
            val sombra = withContext(Dispatchers.Default) { buildGhost(rep, v.level) }
            if (mia != generacion) return@launch
            if (sombra == null) {
                aviso = "No se pudo preparar la sombra."
                vista = Vista.Recibido(v)
                return@launch
            }
            guardarEnLibro(ChallengeRole.RECEIVED, v.result, null)
            val packet = runCatching { ChallengePacket.encode(v.result) }.getOrNull().orEmpty()
            val rival = Rival(v.result.player.ifBlank { "Rival" }, v.outcome, sombra, ChallengeBook.idOf(packet))
            jugar(Plan(v.result.challenge, v.level, false, rival), false)
        }
    }

    fun resultadoPropio(plan: Plan, r: PlayResult) {
        scope.launch {
            val res = ChallengeResult(plan.challenge, plan.level.version, nombre, r.score, r.replay.inputs)
            val veredicto = withContext(Dispatchers.Default) { verifyChallengeResult(res, plan.level) }
            if (veredicto !is Verdict.Valid) return@launch // no debería pasar: la partida salió del propio motor
            val packet = runCatching { ChallengePacket.encode(res) }.getOrNull() ?: return@launch
            val intento = Intento(veredicto.outcome, res, packet)
            val previo = mejor
            if (previo == null || compareRuns(plan.challenge.mode, intento.outcome, previo.outcome) > 0) mejor = intento
            if (plan.diario) {
                diario = diario.record(utcDay(System.currentTimeMillis()), veredicto.outcome.score)
                store.saveDaily(diario)
            }
            plan.rival?.let {
                libro = libro.withMyScore(it.entryId, veredicto.outcome.score)
                store.saveChallenges(libro)
            }
        }
    }

    val abrirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = readTextBounded(context, uri, ChallengePacket.MAX_INPUT_CHARS)
            if (t == null) aviso = "No se pudo leer el archivo o es demasiado grande." else importar(t)
        }
    }

    BackHandler(enabled = true) {
        generacion++
        when (val v = vista) {
            Vista.Inicio -> onClose()
            is Vista.Jugando -> vista = if (mejor != null) Vista.Resultado(v.plan) else Vista.Inicio
            else -> vista = Vista.Inicio
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        when (val v = vista) {
            is Vista.Jugando -> MiauSnakePlayScreen(
                level = v.plan.level,
                settings = settings,
                onExit = { vista = if (mejor != null) Vista.Resultado(v.plan) else Vista.Inicio },
                seed = v.plan.challenge.seed,
                ghost = v.plan.rival?.ghost,
                onResult = { r -> resultadoPropio(v.plan, r) },
                resultFooter = {
                    val m = mejor
                    if (m == null) {
                        SnakeText("Verificando tu partida…", palette, size = 13, dim = true)
                    } else {
                        SnakeText("Verificada ✔ · tu mejor intento: ${m.outcome.score} pts", palette, size = 13, bold = true)
                        v.plan.rival?.let { SnakeText(comparacion(v.plan.challenge.mode, m.outcome, it), palette, size = 13) }
                        Spacer(Modifier.height(8.dp))
                        SnakeButton("Compartir mi resultado", { vista = Vista.Resultado(v.plan) }, palette)
                    }
                },
                theme = theme
            )

            else -> Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                aviso?.let { Caja(it, palette) }
                when (val p = vista) {
                    Vista.Inicio -> Inicio(
                        palette = palette, libro = libro, hoy = hoy(), mejorHoy = diario.best(hoy()),
                        diario = dailyChallengeFor(System.currentTimeMillis()),
                        selectedMapNo = selectedMapNo, selectedMode = selectedMode,
                        texto = texto, onTexto = { if (it.length <= FIELD_MAX_CHARS) texto = it },
                        onDiario = { jugarReto(dailyChallengeFor(System.currentTimeMillis()), true) },
                        onCrear = {
                            val semilla = (System.nanoTime() xor System.currentTimeMillis()) and 0xFFFFFFL
                            jugarReto(Challenge(RULES_VERSION, selectedMapNo, selectedMode, semilla), false)
                        },
                        onImportarTexto = { importar(texto) },
                        onArchivo = { abrirArchivo.launch(arrayOf("text/*", "application/octet-stream")) },
                        onQr = { scanQr(context, { t -> if (t != null) importar(t) }, { e -> aviso = e }) },
                        onAbrirGuardado = { importar(it) },
                        onVolver = onClose
                    )
                    Vista.Trabajando -> SnakeText("Comprobando el reto en tu teléfono…", palette, size = 14, dim = true)
                    is Vista.Recibido -> Recibido(
                        palette = palette, salida = p.salida,
                        onSombra = { contraSombra(it) },
                        onJugar = { c, lv -> jugar(Plan(c, lv, false, null), false) },
                        onAviso = { aviso = it },
                        onVolver = { vista = Vista.Inicio }
                    )
                    is Vista.Resultado -> ResultadoPropio(
                        palette = palette, plan = p.plan, intento = mejor,
                        onCompartido = { i -> guardarEnLibro(ChallengeRole.SENT, i.result, i.outcome.score) },
                        onAviso = { aviso = it },
                        onReintentar = { jugar(p.plan, true) },
                        onVolver = { vista = Vista.Inicio }
                    )
                    is Vista.Jugando -> Unit
                }
            }
        }
    }
}

private fun comparacion(mode: Char, mine: RunOutcome, rival: Rival): String {
    val c = compareRuns(mode, mine, rival.outcome)
    val quien = when {
        c > 0 -> "Superas a ${rival.name}."
        c < 0 -> "${rival.name} te supera."
        else -> "Empate con ${rival.name}."
    }
    return "$quien (${rival.name}: ${rival.outcome.score} pts)"
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
    libro: ChallengeBook,
    hoy: Long,
    mejorHoy: Int?,
    diario: Challenge,
    selectedMapNo: Int,
    selectedMode: Char,
    texto: String,
    onTexto: (String) -> Unit,
    onDiario: () -> Unit,
    onCrear: () -> Unit,
    onImportarTexto: () -> Unit,
    onArchivo: () -> Unit,
    onQr: () -> Unit,
    onAbrirGuardado: (String) -> Unit,
    onVolver: () -> Unit
) {
    SnakeText("RETOS", palette, size = 24, bold = true)
    SnakeText(
        "Juega el mismo mapa y la misma semilla que un amigo, cada uno cuando pueda. Se comparan repeticiones: no hace falta conexión a la vez.",
        palette, size = 13, dim = true
    )

    MenuSection("Reto del día", palette)
    SnakeText(
        "${formatUtcDay(hoy)} (UTC) · Mapa ${diario.mapNo} · Infinito" +
            (mejorHoy?.let { " · tu mejor de hoy: $it pts" } ?: ""),
        palette, size = 13
    )
    SnakeButton("Jugar el reto del día", onDiario, palette, Modifier.fillMaxWidth(), primary = true)

    MenuSection("Crear un reto", palette)
    SnakeText(
        "Mapa $selectedMapNo · ${ChallengeText.modeName(selectedMode)} (se eligen en el menú anterior). Juegas tú primero y compartes tu resultado.",
        palette, size = 13
    )
    SnakeButton("Crear reto y jugar", onCrear, palette, Modifier.fillMaxWidth())

    MenuSection("Recibir un reto", palette)
    BasicTextField(
        value = texto,
        onValueChange = onTexto,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 160.dp)
            .background(palette.panel)
            .padding(12.dp)
            .semantics { contentDescription = "Pega aquí un reto o un resultado" },
        textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
        cursorBrush = SolidColor(palette.text),
        decorationBox = { campo ->
            if (texto.isEmpty()) SnakeText("Pega aquí un código MIAU… o un resultado MIAUR1:…", palette, size = 13, dim = true)
            campo()
        }
    )
    SnakeButton("Comprobar el texto", onImportarTexto, palette, Modifier.fillMaxWidth(), primary = true)
    SnakeButton("Abrir un archivo", onArchivo, palette, Modifier.fillMaxWidth())
    SnakeButton("Leer un QR", onQr, palette, Modifier.fillMaxWidth())

    if (libro.entries.isNotEmpty()) {
        MenuSection("Mis retos", palette)
        val resumenes = remember(libro) {
            libro.entries.map { e -> e to (ChallengePacket.decode(e.packet) as? PacketDecode.Ok)?.result }
        }
        for ((e, r) in resumenes) {
            val titulo = if (r == null) "Reto guardado" else
                "${if (e.role == ChallengeRole.SENT) "Enviado" else "De ${r.player.ifBlank { "Rival" }}"} · Mapa ${r.challenge.mapNo} · " +
                    "${ChallengeText.modeName(r.challenge.mode)} · ${r.claimedScore} pts"
            val tuyo = e.myScore?.let { " · tú: $it" } ?: ""
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(palette.panel)
                    .clickable(role = Role.Button, onClickLabel = "Abrir reto") { onAbrirGuardado(e.packet) }
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) { SnakeText(titulo + tuyo, palette, size = 13) }
        }
    }

    Spacer(Modifier.height(8.dp))
    SnakeButton("Volver", onVolver, palette, Modifier.fillMaxWidth())
}

@Composable
private fun Recibido(
    palette: GamePalette,
    salida: ImportOutcome,
    onSombra: (ImportOutcome.Verified) -> Unit,
    onJugar: (Challenge, Level) -> Unit,
    onAviso: (String) -> Unit,
    onVolver: () -> Unit
) {
    when (salida) {
        is ImportOutcome.Verified -> {
            val r = salida.result
            SnakeText("RESULTADO VERIFICADO ✔", palette, size = 20, bold = true)
            SnakeText("${r.player.ifBlank { "Rival" }} · ${ChallengeText.summary(r.challenge, salida.outcome)}", palette, size = 14)
            SnakeText(
                "Tu teléfono repitió la partida paso a paso: esta puntuación no es solo lo que dice el mensaje. " +
                    "(Prueba que la repetición es legal, no que la jugó una persona.)",
                palette, size = 12, dim = true
            )
            SnakeButton("Jugar contra su sombra", { onSombra(salida) }, palette, Modifier.fillMaxWidth(), primary = true)
            val packet = runCatching { ChallengePacket.encode(r) }.getOrNull()
            if (packet != null) {
                CompartirBotones(palette, packet, ChallengeText.shareResult(r, salida.outcome, packet), onCompartido = {}, onAviso = onAviso)
            }
        }
        is ImportOutcome.Invitation -> {
            SnakeText("RETO", palette, size = 20, bold = true)
            SnakeText("Mapa ${salida.challenge.mapNo} · ${ChallengeText.modeName(salida.challenge.mode)}", palette, size = 14)
            SnakeText("Código: ${salida.challenge.toCode()}", palette, size = 12, dim = true)
            SnakeButton("Jugar este reto", { onJugar(salida.challenge, salida.level) }, palette, Modifier.fillMaxWidth(), primary = true)
        }
        is ImportOutcome.Invalid -> {
            SnakeText("RESULTADO RECHAZADO", palette, size = 20, bold = true)
            SnakeText(ChallengeText.rejection(salida.reason), palette, size = 14)
        }
        is ImportOutcome.Unreadable -> {
            SnakeText("NO SE PUDO LEER", palette, size = 20, bold = true)
            SnakeText(ChallengeText.packetError(salida.error), palette, size = 14)
        }
        is ImportOutcome.UnsupportedRules -> {
            SnakeText("OTRA VERSIÓN", palette, size = 20, bold = true)
            SnakeText("Este reto usa las reglas ${salida.rules}. Actualiza la app para jugarlo.", palette, size = 14)
        }
        is ImportOutcome.UnknownMap -> {
            SnakeText("MAPA NO DISPONIBLE", palette, size = 20, bold = true)
            SnakeText("El mapa ${salida.mapNo} no está en este teléfono.", palette, size = 14)
        }
        ImportOutcome.NothingFound -> {
            SnakeText("SIN RETO", palette, size = 20, bold = true)
            SnakeText("No encontré ningún código de reto (MIAU…) ni resultado (MIAUR1:…) en ese texto.", palette, size = 14)
        }
    }
    Spacer(Modifier.height(8.dp))
    SnakeButton("Volver", onVolver, palette, Modifier.fillMaxWidth())
}

@Composable
private fun ResultadoPropio(
    palette: GamePalette,
    plan: Plan,
    intento: Intento?,
    onCompartido: (Intento) -> Unit,
    onAviso: (String) -> Unit,
    onReintentar: () -> Unit,
    onVolver: () -> Unit
) {
    SnakeText("TU RESULTADO", palette, size = 20, bold = true)
    if (intento == null) {
        SnakeText("Todavía no hay una partida terminada y verificada.", palette, size = 14)
    } else {
        SnakeText(ChallengeText.summary(plan.challenge, intento.outcome), palette, size = 14)
        plan.rival?.let { SnakeText(comparacion(plan.challenge.mode, intento.outcome, it), palette, size = 14, bold = true) }
        SnakeText("Verificado por re-simulación en tu teléfono.", palette, size = 12, dim = true)
        CompartirBotones(
            palette = palette,
            packet = intento.packet,
            textoCompartir = ChallengeText.shareResult(intento.result, intento.outcome, intento.packet),
            onCompartido = { onCompartido(intento) },
            onAviso = onAviso
        )
        if (plan.rival == null) {
            SnakeText("¿Solo quieres retar sin resultado? Comparte el código:", palette, size = 12, dim = true)
            val ctx = LocalContext.current
            SnakeButton("Compartir solo el código del reto", {
                if (!shareText(ctx, "Compartir reto", ChallengeText.shareInvitation(plan.challenge))) onAviso("No se pudo abrir el selector de compartir.")
            }, palette, Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(8.dp))
    SnakeButton("Reintentar el reto", onReintentar, palette, Modifier.fillMaxWidth())
    SnakeButton("Volver a Retos", onVolver, palette, Modifier.fillMaxWidth())
}

/** Texto, archivo y QR: tres salidas para el mismo paquete (el QR solo si cabe). */
@Composable
private fun CompartirBotones(
    palette: GamePalette,
    packet: String,
    textoCompartir: String,
    onCompartido: () -> Unit,
    onAviso: (String) -> Unit
) {
    val context = LocalContext.current
    var verQr by remember(packet) { mutableStateOf(false) }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            if (writeText(context, uri, textoCompartir)) {
                onCompartido()
                onAviso("Archivo guardado.")
            } else {
                onAviso("No se pudo guardar el archivo.")
            }
        }
    }
    SnakeButton("Compartir por texto", {
        if (shareText(context, "Compartir resultado", textoCompartir)) onCompartido()
        else onAviso("No se pudo abrir el selector de compartir.")
    }, palette, Modifier.fillMaxWidth(), primary = true)
    SnakeButton("Guardar como archivo", { guardar.launch("reto_miausnake.txt") }, palette, Modifier.fillMaxWidth())
    if (QrSupport.fits(packet)) {
        SnakeButton(if (verQr) "Ocultar QR" else "Mostrar QR", {
            verQr = !verQr
            if (verQr) onCompartido()
        }, palette, Modifier.fillMaxWidth())
        if (verQr) {
            QrImage(packet, "Código QR del resultado", Modifier.fillMaxWidth().widthIn(max = 320.dp))
            SnakeText("Quien lo lea desde Retos → Leer un QR recibe el resultado.", palette, size = 12, dim = true)
        }
    } else {
        SnakeText("Tu partida es demasiado larga para un QR legible: usa el texto o el archivo.", palette, size = 12, dim = true)
    }
}

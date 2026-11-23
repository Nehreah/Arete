package com.renea.arete_social.feature.miausnake.ui.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renea.arete_social.BuildConfig
import com.renea.arete_social.AreteApp
import com.renea.arete_social.domain.model.FeatureFlags
import com.renea.arete_social.feature.miausnake.integration.mesh.AreteMatchTransport
import com.renea.arete_social.feature.miausnake.integration.mesh.AreteNearby
import com.renea.arete_social.feature.miausnake.match.MatchCoordinator
import com.renea.arete_social.feature.miausnake.ui.match.MatchHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.resimulate
import com.renea.arete_social.feature.miausnake.core.verify
import com.renea.arete_social.feature.miausnake.data.SharedPrefsSnakeStore
import com.renea.arete_social.feature.miausnake.data.SharedPrefsSocialStore
import com.renea.arete_social.feature.miausnake.data.SnakePrefs
import com.renea.arete_social.feature.miausnake.integration.CierreSnake
import com.renea.arete_social.feature.miausnake.integration.ResultadoSnake
import com.renea.arete_social.feature.miausnake.integration.SnakeSesionUi
import com.renea.arete_social.feature.miausnake.integration.SnakeSessionCoordinator
import com.renea.arete_social.feature.miausnake.integration.texto
import com.renea.arete_social.feature.miausnake.levels.AssetLevels
import com.renea.arete_social.feature.miausnake.levels.GameMode
import com.renea.arete_social.feature.miausnake.levels.LevelCatalog
import com.renea.arete_social.feature.miausnake.levels.LevelSpec
import com.renea.arete_social.feature.miausnake.levels.ProgressBook
import com.renea.arete_social.feature.miausnake.levels.RecordUpdate
import com.renea.arete_social.feature.miausnake.levels.summary
import com.renea.arete_social.feature.miausnake.social.LevelResolver
import com.renea.arete_social.feature.miausnake.ui.challenge.ChallengeHub
import com.renea.arete_social.feature.miausnake.ui.maps.MapSelector
import com.renea.arete_social.feature.miausnake.ui.maps.detail
import com.renea.arete_social.feature.miausnake.ui.play.ControlMode
import com.renea.arete_social.feature.miausnake.ui.play.MenuChoice
import com.renea.arete_social.feature.miausnake.ui.play.MenuSection
import com.renea.arete_social.feature.miausnake.ui.play.MenuToggle
import com.renea.arete_social.feature.miausnake.ui.play.MiauSnakePlayScreen
import com.renea.arete_social.feature.miausnake.ui.play.PlaySettings
import com.renea.arete_social.feature.miausnake.ui.play.SnakeButton
import com.renea.arete_social.feature.miausnake.ui.play.SnakeText
import com.renea.arete_social.feature.miausnake.ui.spike.CellSizeSpike
import com.renea.arete_social.feature.miausnake.ui.theme.GamePalette
import com.renea.arete_social.feature.miausnake.ui.theme.ProgrammaticTheme

/**
 * Miau Snake dentro de Michi: pantalla completa abierta desde JUGAR → «Miau Snake».
 *
 *  - Menú previo: elegir modo (Meta / Infinito), mapa de los 20 y controles. Los récords, medallas y
 *    ajustes se guardan en el teléfono (Fase 4). Salir de aquí NO consume nada ni crea ninguna sesión.
 *  - «Jugar con Michi»: abre una sesión (si Michi puede jugar) y al terminar la partida el puente
 *    aplica como mucho una recompensa. Si Michi no puede jugar se explica el motivo y se ofrece
 *    la práctica. Un error de carga muestra un aviso con «Volver»: jamás se ejecuta «Automático».
 *  - «Jugar sin recompensa»: práctica; no toca a Michi.
 *  - «Partida cercana» (Fase 6, tras el flag MIAUSNAKE_CERCANIA): duelo por resultados con un teléfono cercano; ver [MatchHub].
 *  - «Retos» (Fase 5): retos por código/QR/archivo y reto del día. Nunca dan recompensa a Michi ni tocan
 *    los récords del mapa; ver [ChallengeHub].
 *
 * @param onCerrar se llama al salir. Trae el último resultado guardado (si lo hubo) para que Michi lo celebre.
 */
@Composable
fun MiauSnakeEnMichi(
    coordinator: SnakeSessionCoordinator,
    nombreMascota: String,
    onCerrar: (CierreSnake?) -> Unit
) {
    val estado by coordinator.estado.collectAsState()
    val contexto = LocalContext.current
    val store = remember { SharedPrefsSnakeStore(contexto) }
    val socialStore = remember { SharedPrefsSocialStore(contexto) }
    val prefsIniciales = remember { store.loadPrefs() }
    val catalogo: Result<LevelCatalog> = remember { runCatching { AssetLevels.load(contexto) } }

    var ajustes by remember { mutableStateOf(prefsIniciales.settings) }
    var modo by remember { mutableStateOf(prefsIniciales.mode) }
    var mapaId by remember { mutableStateOf(prefsIniciales.lastMapId) }
    var libro by remember { mutableStateOf(store.loadProgress()) }
    var ultima by remember { mutableStateOf<RecordUpdate?>(null) }
    var conMichi by remember { mutableStateOf(true) }
    var enRetos by remember { mutableStateOf(false) }
    var enCercania by remember { mutableStateOf(false) }

    // Retos (Fase 5): número de mapa + modo ('I'/'M') → Level. Siempre a ritmo normal (el relajado es solo práctica).
    val resolver = remember(catalogo) {
        LevelResolver { mapNo, modeChar ->
            val cat = catalogo.getOrNull() ?: return@LevelResolver null
            val spec = cat.byId("maze_%02d".format(java.util.Locale.ROOT, mapNo)) ?: return@LevelResolver null
            val gm = GameMode.fromCode(modeChar) ?: return@LevelResolver null
            runCatching { spec.toLevel(gm, false) }.getOrNull()
        }
    }

    // Fase 6 (partida cercana): solo existe con el flag encendido y con la malla de Aréte disponible.
    // Se arma aquí, pero no escucha nada hasta que se abre la pantalla (MatchHub.start/stop).
    val cercania = remember(resolver) {
        val c = (contexto.applicationContext as? AreteApp)?.contenedor
        if (!FeatureFlags.MIAUSNAKE_CERCANIA || c == null) {
            null
        } else {
            val yo: () -> String? = { runCatching { c.usuarioIdActual() }.getOrNull() }
            Pair(
                MatchCoordinator(
                    transport = AreteMatchTransport(c.messageRouter, yo),
                    meProvider = yo,
                    nameProvider = { nombreMascota },
                    resolver = resolver,
                    store = socialStore,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                ),
                AreteNearby(c.detectorProximidad, yo)
            )
        }
    }

    val palette = if (ajustes.highContrast) GamePalette.HighContrast else GamePalette.Olive
    val tema = remember(ajustes.highContrast) { ProgrammaticTheme(palette) }
    val specSel: LevelSpec? = catalogo.getOrNull()?.let { it.byId(mapaId) ?: it.levels.firstOrNull() }
    val nivel: Result<Level> = remember(specSel, modo, ajustes.relaxed) {
        if (specSel == null) {
            Result.failure(catalogo.exceptionOrNull() ?: IllegalStateException("No hay mapas disponibles"))
        } else {
            runCatching { specSel.toLevel(modo, ajustes.relaxed) }
        }
    }

    // Los ajustes y la última elección sobreviven al cierre de la app.
    LaunchedEffect(ajustes, modo, specSel?.id) {
        store.savePrefs(SnakePrefs(ajustes, modo, specSel?.id))
    }

    // Entrar siempre con la máquina limpia (p. ej. si quedó un resultado viejo de otra vez).
    LaunchedEffect(Unit) { coordinator.volver() }

    fun salir() {
        val cierre = (coordinator.estado.value as? SnakeSesionUi.Terminada)?.cierre
        coordinator.volver() // jugando → abandona sin recompensa; en el menú no hace nada a Michi
        onCerrar(cierre)
    }

    Dialog(
        onDismissRequest = { salir() },
        properties = DialogProperties(
            dismissOnBackPress = false, // «atrás» lo gestiona BackHandler (la partida lo pausa antes de salir)
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler(enabled = true) { salir() }
        val vista = LocalView.current
        DisposableEffect(vista) {
            vista.keepScreenOn = true
            onDispose { vista.keepScreenOn = false }
        }

        Box(Modifier.fillMaxSize().background(palette.background)) {
            val jugando = estado is SnakeSesionUi.Jugando || estado is SnakeSesionUi.Cerrando || estado is SnakeSesionUi.Terminada
            val level = nivel.getOrNull()
            if (jugando && level != null) {
                val numPartida = when (val e = estado) {
                    is SnakeSesionUi.Jugando -> e.partida
                    is SnakeSesionUi.Cerrando -> e.partida
                    is SnakeSesionUi.Terminada -> e.partida
                    else -> 0
                }
                androidx.compose.runtime.key(numPartida) {
                    MiauSnakePlayScreen(
                        level = level,
                        settings = ajustes,
                        bestScore = specSel?.let { libro.get(it, modo)?.bestScore } ?: 0,
                        onResult = { r ->
                            // Récords y medallas: solo con ritmo normal y repetición verificada.
                            val fin = if (specSel != null && !ajustes.relaxed && verify(r.replay, level, r.score)) {
                                resimulate(r.replay, level)
                            } else {
                                null
                            }
                            if (specSel != null && fin != null) {
                                val (nuevo, cambio) = libro.record(specSel, modo, r.score, r.won, fin.elapsedMs)
                                libro = nuevo
                                store.saveProgress(nuevo)
                                ultima = cambio
                            }
                            coordinator.partidaTerminada(
                                ResultadoSnake.Terminada(level, r.replay, r.score, r.bites, r.won)
                            )
                        },
                        onRestart = {
                            ultima = null
                            coordinator.reiniciar(conMichi)
                        },
                        resultFooter = {
                            PieDeResultado(
                                estado, nombreMascota, palette, coordinator,
                                resumen = ultima?.summary(modo, specSel?.parMs ?: 0L),
                                relajado = ajustes.relaxed
                            )
                        },
                        onExit = { salir() },
                        theme = tema
                    )
                }
            } else if (enCercania && cercania != null) {
                MatchHub(
                    palette = palette, theme = tema, settings = ajustes, coordinator = cercania.first,
                    nearby = cercania.second.peers(), resolver = resolver,
                    selectedMapNo = specSel?.id?.substringAfterLast('_')?.toIntOrNull() ?: 1,
                    selectedMode = modo.code.toString().first(),
                    onClose = { enCercania = false }
                )
            } else if (enRetos) {
                ChallengeHub(
                    palette = palette, theme = tema, settings = ajustes, resolver = resolver, store = socialStore,
                    playerName = nombreMascota,
                    selectedMapNo = specSel?.id?.substringAfterLast('_')?.toIntOrNull() ?: 1,
                    selectedMode = modo.code.toString().first(),
                    onClose = { enRetos = false }
                )
            } else {
                MenuSnake(
                    estado = estado,
                    nombre = nombreMascota,
                    palette = palette,
                    catalogo = catalogo,
                    libro = libro,
                    modo = modo, onModo = { modo = it },
                    specSel = specSel, onMapa = { mapaId = it.id },
                    ajustes = ajustes, onAjustes = { ajustes = it },
                    onJugarConMichi = {
                        conMichi = true
                        ultima = null
                        nivel.fold(
                            onSuccess = { coordinator.jugarConMichi() },
                            onFailure = { coordinator.errorDeCarga(it) }
                        )
                    },
                    onPractica = {
                        conMichi = false
                        ultima = null
                        nivel.fold(
                            onSuccess = { coordinator.jugarPractica() },
                            onFailure = { coordinator.errorDeCarga(it) }
                        )
                    },
                    onRetos = { enRetos = true },
                    onCercania = if (cercania != null) ({ enCercania = true }) else null,
                    onVolver = { salir() }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PieDeResultado(
    estado: SnakeSesionUi,
    nombre: String,
    palette: GamePalette,
    coordinator: SnakeSessionCoordinator,
    resumen: String?,
    relajado: Boolean
) {
    if (resumen != null) {
        SnakeText(resumen, palette, size = 13, bold = true)
        Spacer(Modifier.height(6.dp))
    } else if (relajado && estado is SnakeSesionUi.Terminada) {
        SnakeText("Ritmo relajado: no cuenta para récords ni medallas.", palette, size = 13, dim = true)
        Spacer(Modifier.height(6.dp))
    }
    when (estado) {
        is SnakeSesionUi.Cerrando -> SnakeText("Guardando el resultado…", palette, size = 13, dim = true)
        is SnakeSesionUi.Terminada -> {
            val cierre = estado.cierre
            if (cierre == null) {
                SnakeText("Partida de práctica: sin recompensa.", palette, size = 13, dim = true)
            } else {
                SnakeText(cierre.texto(nombre), palette, size = 13, bold = cierre.recompensado)
                if (cierre is CierreSnake.Error) {
                    Spacer(Modifier.height(8.dp))
                    SnakeButton("Reintentar", { coordinator.reintentarCierre() }, palette)
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun MenuSnake(
    estado: SnakeSesionUi,
    nombre: String,
    palette: GamePalette,
    catalogo: Result<LevelCatalog>,
    libro: ProgressBook,
    modo: GameMode, onModo: (GameMode) -> Unit,
    specSel: LevelSpec?, onMapa: (LevelSpec) -> Unit,
    ajustes: PlaySettings, onAjustes: (PlaySettings) -> Unit,
    onJugarConMichi: () -> Unit,
    onPractica: () -> Unit,
    onRetos: () -> Unit,
    onCercania: (() -> Unit)?,
    onVolver: () -> Unit
) {
    val abriendo = estado is SnakeSesionUi.Abriendo
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SnakeText("MIAU SNAKE", palette, size = 24, bold = true)
        SnakeText("Juega tú. Si la partida es de verdad, cuenta como jugar con $nombre.", palette, size = 13, dim = true)

        when (estado) {
            is SnakeSesionUi.NoDisponible -> Aviso(
                estado.motivo.texto(nombre) + " Puedes jugar sin recompensa: esa partida no cuenta como jugar.", palette
            )
            is SnakeSesionUi.ErrorCarga -> Aviso(
                "No se pudo abrir Miau Snake. Inténtalo de nuevo o vuelve a $nombre." +
                    (estado.causa.message?.takeIf { it.isNotBlank() }?.let { "\n($it)" } ?: ""),
                palette
            )
            is SnakeSesionUi.Abriendo -> SnakeText("Preparando la partida…", palette, size = 13, dim = true)
            else -> Unit
        }

        MenuSection("Modo", palette)
        MenuChoice(GameMode.entries.toList(), modo, { it.title }, palette, onModo)
        SnakeText(
            if (modo == GameMode.META) "Meta de bocados, medallas y mejor tiempo." else "Sin meta: gana la puntuación.",
            palette, size = 12, dim = true
        )

        MenuSection("Mapa", palette)
        val cat = catalogo.getOrNull()
        if (cat == null) {
            Aviso(
                "No se pudieron cargar los mapas." + (catalogo.exceptionOrNull()?.message?.let { "\n($it)" } ?: ""),
                palette
            )
        } else {
            MapSelector(cat, libro, modo, specSel?.id, palette, onMapa)
            specSel?.let { SnakeText(it.name + " — " + it.detail(modo), palette, size = 12) }
        }

        MenuSection("Control", palette)
        MenuChoice(
            ControlMode.entries.toList(), ajustes.control,
            { if (it == ControlMode.SWIPE) "Swipe" else "Cruceta" }, palette
        ) { onAjustes(ajustes.copy(control = it)) }
        MenuToggle("Modo zurdo", ajustes.leftHanded, palette) { onAjustes(ajustes.copy(leftHanded = it)) }
        MenuToggle("Vibración", ajustes.vibration, palette) { onAjustes(ajustes.copy(vibration = it)) }

        MenuSection("Accesibilidad", palette)
        MenuToggle("Contraste alto", ajustes.highContrast, palette) { onAjustes(ajustes.copy(highContrast = it)) }
        MenuToggle("Ritmo relajado (sin récords)", ajustes.relaxed, palette) { onAjustes(ajustes.copy(relaxed = it)) }

        if (BuildConfig.DEBUG) {
            // Herramientas de depuración: interpolación, FPS y la tabla del spike 16 vs 24 px (Fase 2).
            MenuSection("Prueba (solo debug)", palette)
            MenuToggle("Interpolación entre ticks", ajustes.interpolate, palette) { onAjustes(ajustes.copy(interpolate = it)) }
            MenuToggle("Panel de FPS y tamaño de celda", ajustes.showDebug, palette) { onAjustes(ajustes.copy(showDebug = it)) }
            for (r in CellSizeSpike.table().filter { it.device.name.startsWith("Típico") }) {
                SnakeText(
                    "${r.spec.referencePx} px: celda ${"%.1f".format(r.cellDp)} dp · tablero ${"%.0f".format(r.boardWidthDp)}×${"%.0f".format(r.boardHeightDp)} dp",
                    palette, size = 12, dim = true
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        SnakeButton(
            if (abriendo) "Preparando…" else "JUGAR CON ${nombre.uppercase()}",
            { if (!abriendo) onJugarConMichi() }, palette, Modifier.fillMaxWidth(), primary = true
        )
        SnakeButton("Jugar sin recompensa", { if (!abriendo) onPractica() }, palette, Modifier.fillMaxWidth())
        SnakeButton("Retos con amigos y reto del día", { if (!abriendo) onRetos() }, palette, Modifier.fillMaxWidth())
        onCercania?.let { SnakeButton("Partida cercana (duelo con un amigo)", { if (!abriendo) it() }, palette, Modifier.fillMaxWidth()) }
        SnakeButton("Volver", onVolver, palette, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Aviso(texto: String, palette: GamePalette) {
    Box(
        Modifier.fillMaxWidth().background(palette.panel).padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SnakeText(texto, palette, size = 13)
    }
}

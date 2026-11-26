package com.renea.arete_social.feature.miausnake.ui.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renea.arete_social.feature.miausnake.levels.Medal
import com.renea.arete_social.feature.miausnake.ui.maps.LevelThumbnail
import com.renea.arete_social.feature.miausnake.ui.play.SnakeBadge
import com.renea.arete_social.feature.miausnake.ui.play.SnakeCard
import com.renea.arete_social.feature.miausnake.ui.play.SnakeIconButton
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

private enum class SnakeNavTab(val label: String, val icon: String) {
    JUGAR("Jugar", "🎮"),
    MAPAS("Mapas", "🗺️"),
    RETOS("Retos", "🏆"),
    AJUSTES("Ajustes", "⚙️")
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
    var tabActual by remember { mutableStateOf(SnakeNavTab.JUGAR) }
    val abriendo = estado is SnakeSesionUi.Abriendo
    val cat = catalogo.getOrNull()
    val totalMapas = cat?.levels?.size ?: 20
    val totalOros = cat?.levels?.count { libro.medal(it) == Medal.GOLD } ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(palette.background)
    ) {
        // Barra superior moderna
        SnakeTopBar(
            nombre = nombre,
            palette = palette,
            modo = modo,
            oros = totalOros,
            total = totalMapas,
            onVolver = onVolver
        )

        // Avisos de estado si la sesión tiene alguna alerta
        when (estado) {
            is SnakeSesionUi.NoDisponible -> Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Aviso(
                    estado.motivo.texto(nombre) + " Puedes jugar sin recompensa: esa partida no cuenta como jugar.",
                    palette
                )
            }
            is SnakeSesionUi.ErrorCarga -> Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Aviso(
                    "No se pudo abrir Miau Snake. Inténtalo de nuevo o vuelve a $nombre." +
                        (estado.causa.message?.takeIf { it.isNotBlank() }?.let { "\n($it)" } ?: ""),
                    palette
                )
            }
            is SnakeSesionUi.Abriendo -> Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SnakeText("Preparando la partida…", palette, size = 13, dim = true)
            }
            else -> Unit
        }

        // Vista de la pestaña activa
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tabActual) {
                SnakeNavTab.JUGAR -> TabJugar(
                    nombre = nombre,
                    palette = palette,
                    modo = modo,
                    onModo = onModo,
                    specSel = specSel,
                    libro = libro,
                    abriendo = abriendo,
                    totalOros = totalOros,
                    totalMapas = totalMapas,
                    onJugarConMichi = onJugarConMichi,
                    onPractica = onPractica,
                    onIrAMapas = { tabActual = SnakeNavTab.MAPAS }
                )
                SnakeNavTab.MAPAS -> TabMapas(
                    catalogo = catalogo,
                    libro = libro,
                    modo = modo,
                    specSel = specSel,
                    palette = palette,
                    onMapa = onMapa,
                    onJugarConMichi = onJugarConMichi,
                    abriendo = abriendo
                )
                SnakeNavTab.RETOS -> TabRetos(
                    nombre = nombre,
                    palette = palette,
                    abriendo = abriendo,
                    onRetos = onRetos,
                    onCercania = onCercania
                )
                SnakeNavTab.AJUSTES -> TabAjustes(
                    ajustes = ajustes,
                    palette = palette,
                    onAjustes = onAjustes
                )
            }
        }

        // Barra inferior de navegación
        SnakeBottomNav(
            tabActual = tabActual,
            palette = palette,
            onTabSelect = { tabActual = it }
        )
    }
}

@Composable
private fun SnakeTopBar(
    nombre: String,
    palette: GamePalette,
    modo: GameMode,
    oros: Int,
    total: Int,
    onVolver: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SnakeIconButton(
                onClick = onVolver,
                label = "Volver a $nombre",
                palette = palette
            ) {
                BasicText(
                    "✕",
                    style = TextStyle(color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
            Column {
                SnakeText("MIAU SNAKE", palette, size = 18, bold = true)
                SnakeText("Compañero: $nombre", palette, size = 12, dim = true)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SnakeBadge(if (modo == GameMode.META) "META" else "INFINITO", palette, highlight = true)
            if (oros > 0) {
                SnakeBadge("★ $oros/$total", palette)
            }
        }
    }
}

@Composable
private fun TabJugar(
    nombre: String,
    palette: GamePalette,
    modo: GameMode,
    onModo: (GameMode) -> Unit,
    specSel: LevelSpec?,
    libro: ProgressBook,
    abriendo: Boolean,
    totalOros: Int,
    totalMapas: Int,
    onJugarConMichi: () -> Unit,
    onPractica: () -> Unit,
    onIrAMapas: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tarjeta Hero del Mapa
        if (specSel != null) {
            val record = libro.get(specSel, modo)
            val medal = libro.medal(specSel)
            SnakeCard(palette = palette, highlight = true, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SnakeBadge("MAPA ACTUAL", palette, highlight = true)
                        SnakeText("★".repeat(specSel.difficulty), palette, size = 13, bold = true)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, palette.wallEdge.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            LevelThumbnail(specSel, palette, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SnakeText("${specSel.number.toString().padStart(2, '0')}. ${specSel.name}", palette, size = 18, bold = true)
                            SnakeText(specSel.detail(modo), palette, size = 12, dim = true)
                            if (modo == GameMode.META) {
                                val statusText = if (medal == Medal.NONE) "Sin medalla" else "Medalla: ${medal.label}"
                                SnakeText(statusText, palette, size = 12, bold = medal != Medal.NONE)
                            } else {
                                SnakeText("Récord: ${record?.bestScore ?: 0} pts", palette, size = 12, bold = true)
                            }
                        }
                    }

                    MenuSection("Modo de Juego", palette)
                    MenuChoice(GameMode.entries.toList(), modo, { it.title }, palette, onModo)

                    SnakeButton(
                        "Explorar otros mapas →",
                        onIrAMapas,
                        palette,
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Tarjeta de acompañamiento con Michi
        SnakeCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.background)
                        .border(1.dp, palette.wallEdge.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("🐱", style = TextStyle(fontSize = 22.sp))
                }
                Column(Modifier.weight(1f)) {
                    SnakeText("$nombre está listo", palette, size = 14, bold = true)
                    SnakeText("Si juegas una partida real, Michi ganará afecto y celebrará tus victorias.", palette, size = 12, dim = true)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Botones de acción principales
        SnakeButton(
            if (abriendo) "Preparando…" else "JUGAR CON ${nombre.uppercase()}",
            { if (!abriendo) onJugarConMichi() },
            palette,
            Modifier.fillMaxWidth(),
            primary = true
        )
        SnakeButton(
            "Jugar sin recompensa (práctica)",
            { if (!abriendo) onPractica() },
            palette,
            Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TabMapas(
    catalogo: Result<LevelCatalog>,
    libro: ProgressBook,
    modo: GameMode,
    specSel: LevelSpec?,
    palette: GamePalette,
    onMapa: (LevelSpec) -> Unit,
    onJugarConMichi: () -> Unit,
    abriendo: Boolean
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                SnakeText("EXPLORADOR DE MAPAS", palette, size = 16, bold = true)
                SnakeText("20 mapas oficiales agrupados por paquetes", palette, size = 12, dim = true)
            }
            specSel?.let {
                SnakeBadge("Mapa ${it.number}", palette, highlight = true)
            }
        }

        val cat = catalogo.getOrNull()
        if (cat == null) {
            Aviso(
                "No se pudieron cargar los mapas." + (catalogo.exceptionOrNull()?.message?.let { "\n($it)" } ?: ""),
                palette
            )
        } else {
            MapSelector(cat, libro, modo, specSel?.id, palette, onMapa)
        }

        if (specSel != null) {
            Spacer(Modifier.height(4.dp))
            SnakeButton(
                if (abriendo) "Preparando…" else "Jugar ${specSel.name}",
                { if (!abriendo) onJugarConMichi() },
                palette,
                Modifier.fillMaxWidth(),
                primary = true
            )
        }
    }
}

@Composable
private fun TabRetos(
    nombre: String,
    palette: GamePalette,
    abriendo: Boolean,
    onRetos: () -> Unit,
    onCercania: (() -> Unit)?
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SnakeText("RETOS Y COMUNIDAD", palette, size = 16, bold = true)
        SnakeText("Desafía a otros jugadores y compite con fantasmas de repetición", palette, size = 12, dim = true)

        SnakeCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText("⭐", style = TextStyle(fontSize = 18.sp))
                    SnakeText("RETO DEL DÍA & RETOS QR", palette, size = 15, bold = true)
                }
                SnakeText(
                    "Juega el mapa diario con la misma semilla global para competir en igualdad, o escanea un código QR para jugar contra la sombra de un amigo.",
                    palette,
                    size = 12,
                    dim = true
                )
                Spacer(Modifier.height(4.dp))
                SnakeButton(
                    "Abrir Retos y Códigos QR",
                    { if (!abriendo) onRetos() },
                    palette,
                    Modifier.fillMaxWidth(),
                    primary = true
                )
            }
        }

        if (onCercania != null) {
            SnakeCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText("📡", style = TextStyle(fontSize = 18.sp))
                        SnakeText("DUELO CERCANO POR RED MESH", palette, size = 15, bold = true)
                    }
                    SnakeText(
                        "Duelo simultáneo con un teléfono cercano usando la red local de Aréte sin internet.",
                        palette,
                        size = 12,
                        dim = true
                    )
                    Spacer(Modifier.height(4.dp))
                    SnakeButton(
                        "Buscar duelo cercano",
                        { if (!abriendo) onCercania() },
                        palette,
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TabAjustes(
    ajustes: PlaySettings,
    palette: GamePalette,
    onAjustes: (PlaySettings) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SnakeText("AJUSTES Y PREFERENCIAS", palette, size = 16, bold = true)

        MenuSection("Modo de Control", palette)
        MenuChoice(
            ControlMode.entries.toList(), ajustes.control,
            { if (it == ControlMode.SWIPE) "Swipe (Deslizar)" else "Cruceta táctil" }, palette
        ) { onAjustes(ajustes.copy(control = it)) }
        MenuToggle("Modo zurdo", ajustes.leftHanded, palette) { onAjustes(ajustes.copy(leftHanded = it)) }
        MenuToggle("Vibración háptica", ajustes.vibration, palette) { onAjustes(ajustes.copy(vibration = it)) }

        MenuSection("Accesibilidad y Ritmo", palette)
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
    }
}

@Composable
private fun SnakeBottomNav(
    tabActual: SnakeNavTab,
    palette: GamePalette,
    onTabSelect: (SnakeNavTab) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.panel)
            .border(1.dp, palette.wallEdge.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (tab in SnakeNavTab.entries) {
            val activa = tab == tabActual
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(if (activa) palette.food else Color.Transparent)
                    .clickable(role = Role.Tab, onClickLabel = tab.label) { onTabSelect(tab) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BasicText(
                        tab.icon,
                        style = TextStyle(fontSize = 14.sp)
                    )
                    BasicText(
                        tab.label,
                        style = TextStyle(
                            color = if (activa) palette.background else palette.textDim,
                            fontSize = 13.sp,
                            fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String, palette: GamePalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, palette.wallEdge.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(palette.panel)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SnakeText(texto, palette, size = 13)
    }
}

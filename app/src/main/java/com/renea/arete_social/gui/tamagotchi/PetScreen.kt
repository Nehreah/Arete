package com.renea.arete_social.gui.tamagotchi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.renea.arete_social.data.local.ajustes.AjustesParche
import com.renea.arete_social.domain.proximity.DetectorProximidad
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.feature.miausnake.integration.CierreSnake
import com.renea.arete_social.feature.miausnake.integration.MichiPlayBridgeLocal
import com.renea.arete_social.feature.miausnake.integration.MiauSnakeSesionViewModel
import com.renea.arete_social.feature.miausnake.integration.MiauSnakeSesionViewModelFactory
import com.renea.arete_social.feature.miausnake.integration.SnakeSessionCoordinator
import com.renea.arete_social.feature.miausnake.integration.texto
import com.renea.arete_social.feature.miausnake.ui.host.MiauSnakeEnMichi
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.canPlay
import com.renea.arete_social.feature.tamagotchi.logic.rasgos
import com.renea.arete_social.feature.tamagotchi.logic.resumenAtencionHoy
import com.renea.arete_social.feature.tamagotchi.logic.xpParaSubir
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetMood
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.viewmodel.PetUiState
import com.renea.arete_social.feature.tamagotchi.viewmodel.PetViewModel
import com.renea.arete_social.feature.tamagotchi.viewmodel.PetViewModelFactory
import com.renea.arete_social.gui.tamagotchi.theme.DangerRed
import com.renea.arete_social.gui.tamagotchi.theme.PetGameTheme
import com.renea.arete_social.gui.tamagotchi.theme.PetPalette
import com.renea.arete_social.gui.tamagotchi.theme.SuccessGreen
import com.renea.arete_social.gui.theme.AreteDimens
import com.renea.arete_social.gui.theme.RadarCard
import com.renea.arete_social.gui.personajes.MundoSocialScreen
import com.renea.arete_social.gui.personajes.CasaScreen
import com.renea.arete_social.gui.personajes.VisitasScreen
import com.renea.arete_social.gui.personajes.AmbienteScreen
import com.renea.arete_social.feature.personajes.data.RepositorioCasaLocal
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajesLocal
import com.renea.arete_social.feature.personajes.logic.MotorAmbiente
import com.renea.arete_social.feature.personajes.model.ambiente.CatalogoAmbientes
import com.renea.arete_social.feature.personajes.model.ambiente.PersonajeEnAmbiente
import com.renea.arete_social.feature.personajes.model.escena.CoordenadaTile

/**
 * Pantalla de la mascota de Aréte. De arriba abajo, sin nada superpuesto:
 *  1. Cabecera compacta (nombre editable, Estado y botón Parche: verde si está activo, rojo si no).
 *  2. Escenario con todo lo de la mascota DENTRO: arriba el ánimo, en el centro
 *    la mascota, y a la izquierda un solo botón
 *     «Cuidar» que despliega hacia abajo Alimentar, Jugar, Bañar, Dormir y Curar.
 *  3. Lectura del estado desplegable (con rasgos y Reiniciar), que se abre con el botón de
 *     estado de arriba a la izquierda del escenario.
 * Las chispas y la medicina NO se envían desde aquí: se mandan desde el mapa (gatos cercanos).
 * El XP doble llega al despertar tras dormir un rato.
 * Si quien mira no es el dueño, es una visita de solo lectura.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PetScreen(
    repository: PetRepository,
    ownerId: String,
    viewerId: String,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    ownerNombre: String = "Usuario",
    detectorProximidad: DetectorProximidad? = null,
    repositorioEventos: RepositorioEventos? = null,
    repositorioUsuarios: RepositorioUsuarios? = null,
    repositorioLibreria: com.renea.arete_social.domain.repository.RepositorioLibreria? = null,
    onEstadoCambiado: ((String?, Long?) -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val factory = remember(repository, ownerId, viewerId, detectorProximidad, repositorioEventos, repositorioLibreria, context) {
        PetViewModelFactory(repository, ownerId, ownerNombre, viewerId, detectorProximidad, repositorioEventos, repositorioLibreria, context)
    }
    val vm: PetViewModel = viewModel(key = "pet_$ownerId", factory = factory)
    val uiState by vm.uiState.collectAsState()
    // Miau Snake (Fase 3): su sesión vive aquí, aparte de la simulación de la mascota.
    val snakeFactory = remember(repository, ownerId) {
        MiauSnakeSesionViewModelFactory(MichiPlayBridgeLocal(repository, ownerId))
    }
    val snakeVm: MiauSnakeSesionViewModel = viewModel(key = "snake_$ownerId", factory = snakeFactory)
    var refrescando by remember { mutableStateOf(false) }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = {
            refrescando = true
            vm.recargar()
            refrescando = false
        },
        modifier = modifier
    ) {
        PetGameTheme {
            PetScreenContent(
                uiState, onAtras, vm, Modifier.fillMaxSize(), ownerId, repositorioUsuarios,
                coordinadorSnake = snakeVm.coordinator, onEstadoCambiado = onEstadoCambiado
            )
        }
    }
}

@Composable
private fun PetScreenContent(
    ui: PetUiState,
    onAtras: () -> Unit,
    vm: PetViewModel,
    modifier: Modifier,
    ownerId: String,
    repositorioUsuarios: RepositorioUsuarios?,
    coordinadorSnake: SnakeSessionCoordinator,
    onEstadoCambiado: ((String?, Long?) -> Unit)? = null
) {
    val scopeParche = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val pet = ui.pet
    var dialogoComida by remember { mutableStateOf(false) }
    var dialogoNombre by remember { mutableStateOf(false) }
    var estadoAbierto by remember { mutableStateOf(false) }
    var cuidadosAbiertos by remember { mutableStateOf(false) }
    var dialogoEstadoParche by remember { mutableStateOf(false) }
    var estadoParche by remember { mutableStateOf(AjustesParche.estado(context) ?: "") }
    var parcheActivo by remember { mutableStateOf(AjustesParche.activado(context)) }
    var revisionParche by remember { mutableIntStateOf(0) }
    var chatMichiAbierto by remember { mutableStateOf(false) }
    var mundoSocialAbierto by remember { mutableStateOf(false) }
    var vistaMundoSocial by remember { mutableStateOf<String?>(null) }
    val mensajesMichi by vm.historialMensajes.collectAsState()
    // JUGAR abre un selector: «Automático» sigue siendo exactamente vm.play; «Miau Snake» abre el juego.
    var snakeAbierto by remember { mutableStateOf(false) }
    val selectorJugar = remember(vm) { SelectorJugar(automatico = vm::play, miauSnake = { snakeAbierto = true }) }

    val radarReport = ui.radarReport
    if (radarReport != null) {
        PetReportDialog(radarReport, vm::dismissRadarReport)
    }
    if (mundoSocialAbierto && pet != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { mundoSocialAbierto = false; vistaMundoSocial = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            when (vistaMundoSocial) {
                "CASA" -> {
                    val repoCasa = remember(context) { RepositorioCasaLocal(context) }
                    val casaSnapshot = remember(ownerId, pet.personajeId) {
                        repoCasa.obtenerCasa(ownerId, pet.personajeId)
                    }
                    CasaScreen(
                        pet = pet,
                        casaInicial = casaSnapshot,
                        onGuardarCasa = { repoCasa.guardarCasa(it) },
                        onActualizarPet = { vm.actualizarPet(it) },
                        onRegresar = { vistaMundoSocial = null }
                    )
                }
                "VISITAS" -> {
                    val repoPersonajes = remember(context) { RepositorioPersonajesLocal(context) }
                    val relaciones = remember { repoPersonajes.obtenerRelaciones() }
                    val recuerdos = remember { repoPersonajes.obtenerRecuerdos() }
                    val amigosIds = remember(relaciones) { relaciones.map { it.otroOwnerId } }
                    VisitasScreen(
                        amigosIds = amigosIds,
                        relaciones = relaciones,
                        recuerdos = recuerdos,
                        onSolicitarVisita = { /* Solicitud de visita mesh */ },
                        onRegresar = { vistaMundoSocial = null }
                    )
                }
                "AMBIENTE" -> {
                    val motor = remember(ownerId, pet.personajeId) {
                        MotorAmbiente(
                            definicion = CatalogoAmbientes.PARQUE,
                            instanciaId = "parque-local",
                            miPersonaje = PersonajeEnAmbiente(
                                ownerId = ownerId,
                                personajeId = pet.personajeId,
                                nombre = pet.name,
                                temperamento = pet.temperamento,
                                posicion = CoordenadaTile(0, 0)
                            )
                        )
                    }
                    AmbienteScreen(
                        motor = motor,
                        onVolver = { vistaMundoSocial = null }
                    )
                }
                else -> {
                    MundoSocialScreen(
                        onVolver = { mundoSocialAbierto = false },
                        onAbrirCasa = { vistaMundoSocial = "CASA" },
                        onAbrirVisitas = { vistaMundoSocial = "VISITAS" },
                        onAbrirAmbiente = { vistaMundoSocial = "AMBIENTE" }
                    )
                }
            }
        }
    }
    if (chatMichiAbierto && pet != null) {
        DialogoChatMichi(
            nombreMascota = pet.name,
            mensajes = mensajesMichi,
            onCerrar = { chatMichiAbierto = false }
        )
    }
    if (dialogoComida && pet != null) {
        FoodSelectorDialog(
            chispasDisponibles = pet.chispas,
            onSelectFood = { dialogoComida = false; vm.feed(it) },
            onDismiss = { dialogoComida = false }
        )
    }
    if (dialogoNombre && pet != null) {
        RenombrarMascotaDialog(
            nombreActual = pet.name,
            onGuardar = { nuevo -> vm.renombrar(nuevo) { guardado -> if (guardado) dialogoNombre = false } },
            onDismiss = { dialogoNombre = false }
        )
    }

    if (pet != null) ModalidadJugarDialog(selector = selectorJugar, nombreMascota = pet.name)
    if (snakeAbierto && pet != null) {
        MiauSnakeEnMichi(
            coordinator = coordinadorSnake,
            nombreMascota = pet.name,
            onCerrar = { cierre ->
                snakeAbierto = false
                // Michi solo celebra: la recompensa ya la aplicó el puente dentro de su transacción.
                if (cierre is CierreSnake.Recompensado) vm.celebrarJuegoExterno(cierre.texto(pet.name))
            }
        )
    }

    // Los cuidados solo existen para el dueño, con la mascota ya nacida y en línea.
    val mostrarCuidados = pet != null && ui.isOwner && !pet.isCorrupted && pet.stage != PetStage.HUEVO

    // OJO: el inset superior (barra de estado) ya lo aplica MainActivity a toda la navegación.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        revisionParche
        PetHeader(
            pet, ui, onAtras,
            onEditarNombre = { dialogoNombre = true },
            onEstado = { dialogoEstadoParche = true },
            mostrarEstado = AjustesParche.estado(context) != null,
            onMensajes = { chatMichiAbierto = true },
            numMensajesMichi = mensajesMichi.size,
            onMundoSocial = { mundoSocialAbierto = true },
            parcheActivo = parcheActivo,
            onParche = {
                parcheActivo = !parcheActivo
                AjustesParche.definirActivado(context, parcheActivo)
                scopeParche.launch { runCatching { repositorioUsuarios?.actualizarParcheMichi(ownerId, parcheActivo) } }
                revisionParche++
            }
        )

        if (dialogoEstadoParche) {
            AlertDialog(
                onDismissRequest = { dialogoEstadoParche = false },
                title = { Text("Estado de Michi") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = estadoParche,
                            onValueChange = { estadoParche = it.take(60) },
                            label = { Text("¿Qué quieres contar?") },
                            supportingText = { Text("${estadoParche.length}/60 · se elimina en 24 horas") },
                            singleLine = false,
                            maxLines = 3
                        )
                        Text("Visible en el mapa durante 24 horas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        AjustesParche.guardarEstado(context, estadoParche)
                        val texto = AjustesParche.estado(context)
                        val vence = AjustesParche.venceEstado(context).takeIf { texto != null }
                        scopeParche.launch { runCatching { repositorioUsuarios?.actualizarEstadoMichi(ownerId, texto, vence) } }
                        onEstadoCambiado?.invoke(texto, vence)
                        revisionParche++
                        dialogoEstadoParche = false
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        estadoParche = ""
                        AjustesParche.guardarEstado(context, "")
                        scopeParche.launch { runCatching { repositorioUsuarios?.actualizarEstadoMichi(ownerId, null, null) } }
                        onEstadoCambiado?.invoke(null, null)
                        revisionParche++
                        dialogoEstadoParche = false
                    }) { Text("Borrar") }
                }
            )
        }

        if (pet == null) {
            PetCargando()
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AreteDimens.space4, vertical = AreteDimens.space3),
                verticalArrangement = Arrangement.spacedBy(AreteDimens.space3)
            ) {
                // 1) Escenario: botones de cuidado (izquierda), mascota y necesidades (derecha)
                PetScene(
                    pet = pet,
                    mood = ui.mood,
                    mensaje = ui.alertMessage,
                    activeAnimation = ui.activeAnimation,
                    onAnimationEnd = vm::clearAnimation,
                    onCuddle = vm::cuddle,
                    modifier = Modifier.fillMaxWidth().height(ALTO_ESCENARIO),
                    conCuidados = mostrarCuidados,
                    accionEstado = {
                        PetStatusButton(ui.mood, pet.isCorrupted, estadoAbierto, { estadoAbierto = !estadoAbierto })
                    },
                    barraNivel = { BarraNivel(pet, ui) },
                    accionesLaterales = {
                        ColumnaCuidados(
                            pet, ui, vm,
                            abierto = cuidadosAbiertos,
                            onToggle = { cuidadosAbiertos = !cuidadosAbiertos },
                            onComer = { dialogoComida = true },
                            onJugar = selectorJugar::abrir
                        )
                    }
                )

                // 2) Lectura del estado (se despliega con el icono de estado)
                EstadoMascota(pet, ui, vm, estadoAbierto)

                // 2b) Camino al Súper: qué falta para despertar los súper poderes y a qué hora despierta Michi.
                if (mostrarCuidados) PetSuperPanel(pet)

                // 3) Mensajes de contexto
                when {
                    !ui.isOwner -> RadarCard {
                        Text(
                            "Estás de visita. Puedes mirarla, pero solo su dueño puede cuidarla.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp
                        )
                    }
                    mostrarCuidados && parcheActivo -> Text(
                        "Parche activo: ${pet.name} brilla para indicar que compartes automáticamente con quien quiera sincronizar contigo. Sus necesidades bajan más rápido mientras Parche esté activo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    mostrarCuidados -> Text(
                        "Toca a ${pet.name} para hacerle mimos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    pet.stage == PetStage.HUEVO -> Text(
                        "Los cuidados se activan cuando nazca.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(AreteDimens.space2))
            }
        }
    }
}

// ── Cabecera ─────────────────────────────────────────────────────────────────

/**
 * Cabecera compacta: atrás, nombre editable + Estado y, a la derecha, el botón Parche (solo el dueño).
 * Ya no muestra «Tu mascota» ni la etapa («Adulto»).
 */
@Composable
private fun PetHeader(
    pet: PetEntity?, ui: PetUiState, onAtras: () -> Unit, onEditarNombre: () -> Unit,
    onEstado: () -> Unit, mostrarEstado: Boolean,
    onMensajes: () -> Unit, numMensajesMichi: Int,
    onMundoSocial: () -> Unit = {},
    parcheActivo: Boolean, onParche: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .border(AreteDimens.borderHairline, scheme.outline.copy(alpha = 0.18f))
            .padding(horizontal = AreteDimens.space3, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.12f))
                .border(1.dp, scheme.primary.copy(alpha = 0.3f), CircleShape)
                .clickable(onClick = onAtras),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = scheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(AreteDimens.space2))

        // El dueño toca el nombre (o el lápiz) para cambiarlo.
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = pet != null && ui.isOwner, onClick = onEditarNombre)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pet?.name ?: "Mascota",
                    color = scheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (pet != null && ui.isOwner) {
                    Spacer(Modifier.width(6.dp))
                    Text("✎", color = scheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    SurfaceEstadoMichi(mostrarEstado = mostrarEstado, onClick = onEstado)
                    Spacer(Modifier.width(6.dp))
                    SurfaceBotonMensajesMichi(numMensajes = numMensajesMichi, onClick = onMensajes)
                    Spacer(Modifier.width(6.dp))
                    SurfaceBotonMundoSocial(onClick = onMundoSocial)
                }
            }
            // Solo las visitas ven de quién es; el dueño ya lo sabe.
            if (!ui.isOwner) {
                Text(
                    "Mascota de ${ui.ownerNombre}",
                    color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (pet != null && ui.isOwner) {
            Spacer(Modifier.width(AreteDimens.space2))
            BotonParche(activo = parcheActivo, onClick = onParche)
        }
    }
}

/** Botón «Parche»: resplandor verde (que late) cuando está activo y rojo fijo cuando no. */
@Composable
private fun BotonParche(activo: Boolean, onClick: () -> Unit) {
    val color = if (activo) SuccessGreen else DangerRed
    val latido = if (activo) {
        val transicion = rememberInfiniteTransition(label = "parche")
        val alfa by transicion.animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "parche-alfa"
        )
        alfa
    } else 0.85f
    val forma = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .padding(2.dp)
            .drawBehind {
                // Resplandor: tres halos concéntricos cada vez más tenues.
                for ((extra, alfa) in listOf(6.dp to 0.10f, 4.dp to 0.16f, 2.dp to 0.24f)) {
                    val e = extra.toPx()
                    drawRoundRect(
                        color = color.copy(alpha = alfa * latido),
                        topLeft = Offset(-e, -e),
                        size = Size(size.width + 2 * e, size.height + 2 * e),
                        cornerRadius = CornerRadius((size.height + 2 * e) / 2f)
                    )
                }
            }
            .clip(forma)
            .background(color.copy(alpha = 0.18f))
            .border(1.5.dp, color.copy(alpha = latido), forma)
            .semantics {
                contentDescription = "Parche"
                stateDescription = if (activo) "Activo" else "Inactivo"
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Parche", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SurfaceEstadoMichi(mostrarEstado: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp
    ) {
        Text(
            if (mostrarEstado) "Estado ·" else "Estado",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SurfaceBotonMensajesMichi(numMensajes: Int, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 1.dp
    ) {
        Text(
            if (numMensajes > 0) "💬 $numMensajes" else "💬",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SurfaceBotonMundoSocial(onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp
    ) {
        Text(
            "🌍 Mundo",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PetCargando() {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ── Avisos y cuidados ────────────────────────────────────────────────────────

/** Acción de cuidado lista para pintarse en la cuadrícula. */
private class AccionCuidado(
    val label: String,
    val hint: String?,
    val disponible: Boolean,
    val destacado: Boolean = false,
    val acentoBase: Color = PetPalette.NeonCyan,
    val onClick: () -> Unit,
    val icono: @Composable (Color) -> Unit
)

/** Cuántos iconos caben por fila al desplegar los cuidados. */
private const val CUIDADOS_POR_FILA = 5

/**
 * Un solo botón a la izquierda («Cuidar») que despliega hacia la DERECHA los cuidados
 * (Alimentar, Jugar, Bañar, Dormir y Curar) en filas de cinco iconos: si hubiera un sexto,
 * pasaría a una segunda fila, bajo el primer icono.
 */
@Composable
private fun ColumnaCuidados(
    pet: PetEntity, ui: PetUiState, vm: PetViewModel,
    abierto: Boolean, onToggle: () -> Unit, onComer: () -> Unit, onJugar: () -> Unit
) {
    val dormida = pet.isSleeping
    val urgente = PetBalance.URGENT_THRESHOLD
    fun espera(s: Int) = if (s > 0) formatearEspera(s.toLong()) else null

    // Hay algo que atender ahora: el botón único late para avisarlo aunque esté cerrado.
    val atencion = pet.isSick || pet.hunger <= urgente || pet.hygiene <= urgente || pet.energy <= urgente

    val acciones = listOf(
        AccionCuidado(
            label = "Alimentar",
            hint = espera(ui.cooldownAlimentarSecs)
                ?: if (pet.hunger >= PetBalance.FULL_THRESHOLD) "Llena" else if (pet.hunger <= urgente) "¡Hambre!" else null,
            disponible = !dormida && ui.cooldownAlimentarSecs == 0 && pet.hunger < PetBalance.FULL_THRESHOLD,
            destacado = pet.hunger <= urgente,
            onClick = onComer
        ) { PetIcons.Feed(Modifier.size(20.dp), it) },

        AccionCuidado(
            label = "Jugar",
            hint = espera(ui.cooldownJugarSecs) ?: if (dormida) "Durmiendo" else if (!pet.canPlay()) "Sin fuerzas" else null,
            disponible = pet.canPlay() && ui.cooldownJugarSecs == 0,
            onClick = onJugar
        ) { PetIcons.Play(Modifier.size(20.dp), it) },

        AccionCuidado(
            label = "Bañar",
            hint = espera(ui.cooldownLimpiarSecs)
                ?: if (pet.hygiene >= 95) "Limpia" else if (pet.hygiene <= urgente) "¡Sucia!" else null,
            disponible = !dormida && ui.cooldownLimpiarSecs == 0 && pet.hygiene < 95,
            destacado = pet.hygiene <= urgente,
            onClick = vm::clean
        ) { PetIcons.Clean(Modifier.size(20.dp), it) },

        AccionCuidado(
            label = if (dormida) "Despertar" else "Dormir",
            hint = if (!dormida && pet.energy <= urgente) "¡Sueño!" else null,
            disponible = dormida || pet.energy < PetBalance.NOT_SLEEPY_THRESHOLD,
            destacado = !dormida && pet.energy <= urgente,
            onClick = vm::toggleSleep
        ) { if (dormida) PetIcons.Wake(Modifier.size(20.dp), it) else PetIcons.Sleep(Modifier.size(20.dp), it) },

        // Curar: siempre está en el menú; solo se activa si está enferma.
        AccionCuidado(
            label = "Curar",
            hint = when {
                !pet.isSick -> "Sana"
                pet.medicinas > 0 -> "${pet.medicinas} med."
                else -> "Sin medicina"
            },
            disponible = pet.isSick,
            destacado = pet.isSick,
            acentoBase = DangerRed,
            onClick = vm::heal
        ) { PetIcons.Heal(Modifier.size(20.dp), it) }
    )

    // El botón «Cuidar» + cinco columnas deben caber en el ancho del escenario (con margen).
    BoxWithConstraints {
        val separacion = 4.dp
        val ancho = ((maxWidth - 20.dp - separacion * CUIDADOS_POR_FILA) / (CUIDADOS_POR_FILA + 1))
            .coerceIn(40.dp, 56.dp)

        Row(verticalAlignment = Alignment.Top) {
            PetCircleAction(
                modifier = Modifier.width(ancho),
                label = if (abierto) "Cerrar" else "Cuidar",
                hint = null,
                disponible = true,
                destacado = atencion && !abierto,
                onClick = onToggle
            ) {
                if (abierto) Text("✕", color = it, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                else PetIcons.Heart(Modifier.size(20.dp), it)
            }

            AnimatedVisibility(
                visible = abierto,
                enter = expandHorizontally(tween(250), expandFrom = Alignment.Start) + fadeIn(tween(250)),
                exit = shrinkHorizontally(tween(200), shrinkTowards = Alignment.Start) + fadeOut(tween(200))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = separacion)
                ) {
                    acciones.chunked(CUIDADOS_POR_FILA).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(separacion)) {
                            fila.forEach { a ->
                                PetCircleAction(
                                    modifier = Modifier.width(ancho),
                                    label = a.label,
                                    hint = a.hint,
                                    disponible = a.disponible,
                                    destacado = a.destacado,
                                    acentoBase = a.acentoBase,
                                    onClick = a.onClick,
                                    icon = a.icono
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Nivel y estado ───────────────────────────────────────────────────────────

/** Barra de nivel al pie del escenario. */
@Composable
private fun BarraNivel(pet: PetEntity, ui: PetUiState) {
    val huevo = pet.stage == PetStage.HUEVO
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (huevo) "Huevo" else "Nivel ${pet.nivel}",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!huevo) {
                    if (pet.modoTurbo) {
                        PetPill("Turbo", PetPalette.NeonGold) {
                            PetIcons.Train(Modifier.size(12.dp), PetPalette.NeonGold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "${pet.experiencia} / ${xpParaSubir(pet.nivel)} XP",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (huevo) {
                val total = PetStage.BEBE.minAgeMillis / 1000f
                val faltan = ui.segundosParaCrecer ?: 0L
                PetThinBar(1f - (faltan / total), PetPalette.NeonCyan)
            } else {
                PetThinBar(pet.experiencia / xpParaSubir(pet.nivel).toFloat(), PetPalette.NeonCyan)
            }
        }
    }
}

/** Lectura del estado (con rasgos y el botón Reiniciar si quedó fuera de línea). */
@Composable
private fun EstadoMascota(pet: PetEntity, ui: PetUiState, vm: PetViewModel, abierto: Boolean) {
    val mood = ui.mood
    val corrupta = pet.isCorrupted
    val (titulo, texto) = when {
        corrupta -> "Fuera de línea" to
            (if (ui.isOwner) "Quedó fuera de línea. Reiníciala para que vuelva."
            else "Quedó fuera de línea. Su dueño debe reiniciarla.")
        pet.stage == PetStage.HUEVO -> "Incubando" to
            "Está por nacer. Cuando nazca podrás cuidarla."
        pet.isSick -> "Enferma" to when {
            !ui.isOwner -> "Está enferma y necesita una medicina."
            pet.medicinas > 0 -> "Está enferma. Tienes ${pet.medicinas} medicina(s) guardada(s)."
            else -> "Está enferma y no tienes medicina. Pídele una a un amigo desde el mapa."
        }
        pet.isSleeping -> "Durmiendo" to
            "Descansa y recupera energía. Déjala dormir toda la noche (7-8 h): despierta sola, de buen humor y con XP doble."
        else -> mood.label to when (mood) {
            PetMood.HAMBRIENTO -> "Tiene hambre. Dale de comer."
            PetMood.SUCIO -> "Está sucia. Un baño le vendría bien."
            PetMood.CANSADO -> "Está cansada. Déjala dormir un rato."
            PetMood.TRISTE -> "Está triste. Juega con ella o rascala en la pantalla para darle mimos."
            PetMood.FELIZ -> "Está feliz y con ganas de jugar. Rascala en la pantalla para mimarla."
            else -> "Está tranquila y se encuentra bien."
        }
    }
    val accion = when {
        !ui.isOwner -> null
        corrupta -> "Reiniciar"
        else -> null
    }
    val rasgos = if (ui.isOwner && pet.stage != PetStage.HUEVO) {
        pet.rasgos().map { it.nombre to it.descripcion }
    } else emptyList()
    val sinRasgos = ui.isOwner && pet.stage != PetStage.HUEVO && rasgos.isEmpty()

    // Para quien la cuida: cuántas visitas lleva hoy y cómo va (el ideal son 4-5 repartidas en el día).
    val conVisitas = if (ui.isOwner && pet.stage != PetStage.HUEVO && !corrupta) {
        "$texto\n\n${pet.resumenAtencionHoy()}"
    } else texto

    PetEstadoPanel(
        visible = abierto,
        titulo = titulo,
        texto = if (sinRasgos) "$conVisitas\n\nTodavía no tiene rasgos. Se desbloquean solos si la cuidas cada día." else conVisitas,
        mood = mood,
        corrupta = corrupta,
        rasgos = rasgos,
        accion = accion,
        onAccion = vm::reboot
    )
}

package com.renea.arete_social.gui.mapa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.SesionEncuentro
import com.renea.arete_social.feature.mapa.mimis.SelectorMimisVisibles
import com.renea.arete_social.feature.mapa.mimis.SimulacionMimis
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajesLocal
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.gui.theme.LocalRadarColors
import com.renea.arete_social.gui.mapa.osm.EstadoCamaraOsm
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

private val AREA_TOQUE_MIMI = 44.dp
private val TAMANO_MIMI = 34.dp

// ~30 cuadros por segundo bastan para un paseo suave y gastan menos batería que 60.
private const val INTERVALO_MINIMO_SEGUNDOS = 1f / 30f - 0.002f

/**
 * Capa de Mimis sobre el mapa: cada persona cercana (hasta 14) aparece como un gatito diminuto
 * que deambula por la zona visible.
 *
 * PRIVACIDAD: los Mimis NO usan el GPS de nadie. Sus posiciones son ficticias, nacen al azar y se
 * mueven solos sobre la pantalla; ver un Mimi en un parque no significa que su dueño esté allí.
 * El listado completo sigue en el módulo Cercanos.
 *
 * Cada Mimi está ANCLADO AL MAPA (como un marcador): al arrastrar, girar o hacer zoom se mueve
 * junto con el suelo, sin deslizarse. Si queda fuera de pantalla, reaparece en la zona visible
 * actual. No intercepta toques fuera de los gatitos.
 */
@Composable
fun CapaMimisMapa(
    estadoCamara: EstadoCamaraOsm,
    personasCercanas: List<Dispositivo>,
    usuarioIdPropio: String,
    onVerPerfil: (usuarioId: String) -> Unit,
    onEscribir: (usuarioId: String, nombre: String) -> Unit,
    onEnviarChispa: (usuarioId: String, nombre: String) -> Unit,
    onEnviarMedicina: (usuarioId: String, nombre: String) -> Unit,
    onSolicitarSincronizacion: (usuarioId: String) -> Unit = {},
    /** Responde al estado de un Michi con un mensaje directo a su dueño. true = enviado. */
    onResponderEstado: suspend (usuarioId: String, estado: String, respuesta: String) -> Boolean = { _, _, _ -> false },
    /** Obras compartidas en el mapa indexadas por usuarioId del autor. */
    obrasPorAutor: Map<String, com.renea.arete_social.domain.model.ObraLibreria> = emptyMap(),
    onAbrirObra: (com.renea.arete_social.domain.model.ObraLibreria) -> Unit = {},
    /**
     * Sesión de encuentro activa con un amigo que ya aceptó compartir su ubicación.
     * Cuando no es nula y [SesionEncuentro.amigoUbicacion] tiene valor, se dibuja el
     * gatito del amigo anclado a su posición GPS real en lugar del pin azul de OSMDroid.
     */
    amigoEncuentro: SesionEncuentro? = null,
    modifier: Modifier = Modifier
) {
    val selector = remember { SelectorMimisVisibles() }
    val simulacion = remember { SimulacionMimis() }
    val entorno = remember { EntornoPaseoMapa() }
    val densidad = LocalDensity.current.density
    val context = LocalContext.current.applicationContext
    val repoPersonajes = remember(context) { RepositorioPersonajesLocal(context) }

    var visibles by remember { mutableStateOf<List<Dispositivo>>(emptyList()) }
    var seleccionadoId by remember { mutableStateOf<String?>(null) }
    var solicitarA by remember { mutableStateOf<Dispositivo?>(null) }
    var estadoAbierto by remember { mutableStateOf<Dispositivo?>(null) }
    var obraPergaminoAbierta by remember { mutableStateOf<com.renea.arete_social.domain.model.ObraLibreria?>(null) }
    var areaPx by remember { mutableStateOf(IntSize.Zero) }
    var cuadro by remember { mutableLongStateOf(0L) }

    // ID del amigo en encuentro activo con ubicación compartida (para que deje de deambular y sea solo marcador GPS)
    val amigoIdActivo = amigoEncuentro?.takeIf { it.amigoUbicacion != null }?.amigoId

    // Reparte los 14 cupos: conserva a los que siguen cerca y repone a los que se fueron,
    // excluyendo al amigo de encuentro activo para que no deambule de forma ficticia.
    LaunchedEffect(personasCercanas, usuarioIdPropio, amigoIdActivo) {
        val candidatos = if (amigoIdActivo != null) {
            personasCercanas.filterNot { it.id == amigoIdActivo || it.usuarioId == amigoIdActivo }
        } else {
            personasCercanas
        }
        val nuevos = selector.seleccionar(candidatos, visibles.map { it.id }, usuarioIdPropio)
        simulacion.sincronizar(nuevos.map { it.id })
        visibles = nuevos
        if (seleccionadoId != null && nuevos.none { it.id == seleccionadoId }) seleccionadoId = null
    }

    // Bucle de animación: corre mientras haya Mimis o un amigo con ubicación activa.
    val hayMimis = visibles.isNotEmpty() || (amigoEncuentro?.amigoUbicacion != null)
    LaunchedEffect(hayMimis) {
        if (!hayMimis) return@LaunchedEffect
        var ultimoNanos = 0L
        while (isActive) {
            val ahora = withFrameNanos { it }
            if (ultimoNanos == 0L) {
                ultimoNanos = ahora
                continue
            }
            val dt = (ahora - ultimoNanos) / 1_000_000_000f
            if (dt < INTERVALO_MINIMO_SEGUNDOS) continue
            ultimoNanos = ahora
            val proyeccion = estadoCamara.proyeccion
            if (proyeccion != null &&
                entorno.actualizar(proyeccion, areaPx.width, areaPx.height, densidad)
            ) {
                simulacion.avanzar(dt, entorno)
            }
            cuadro = ahora
        }
    }

    val areaToquePx = with(LocalDensity.current) { AREA_TOQUE_MIMI.toPx() }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { areaPx = it }) {
        visibles.forEach { persona ->
            key(persona.id) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            // Leer 'cuadro' y la cámara aquí hace que solo esta capa se actualice:
                            // el Mimi sigue al mapa en cada gesto, sin recomponer ni medir de nuevo.
                            cuadro
                            estadoCamara.version
                            val mimi = simulacion.mimi(persona.id)
                            val proyeccion = estadoCamara.proyeccion
                            val pos = if (mimi != null && proyeccion != null) {
                                entorno.aPantalla(proyeccion, mimi.x, mimi.y)
                            } else null
                            if (mimi == null || pos == null) {
                                alpha = 0f
                            } else {
                                translationX = pos.x - areaToquePx / 2f
                                translationY = pos.y - areaToquePx / 2f
                                alpha = (mimi.edadSegundos / 0.6f).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    val obraActiva = persona.usuarioId?.let { obrasPorAutor[it] }
                    val tieneEstado = !persona.estadoMichi.isNullOrBlank() && (persona.estadoMichiHasta ?: 0L) > System.currentTimeMillis()
                    val mimi = simulacion.mimi(persona.id)

                    // Distintivos superiores (libro 📖 y/o nube de estado •••)
                    if (obraActiva != null || tieneEstado) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-18).dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (obraActiva != null) {
                                Surface(
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        obraPergaminoAbierta = obraActiva
                                        onAbrirObra(obraActiva)
                                    },
                                    color = Color(0xFFFFF9E6),
                                    contentColor = Color(0xFF4A3525),
                                    shape = RoundedCornerShape(9.dp),
                                    shadowElevation = 5.dp,
                                    border = BorderStroke(1.dp, Color(0xFFD4B483))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text("📖", fontSize = 11.sp)
                                    }
                                }
                            }
                            if (tieneEstado) {
                                Surface(
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        estadoAbierto = persona
                                    },
                                    color = Color(0xFFF5F5F5),
                                    contentColor = Color(0xFF202020),
                                    shape = RoundedCornerShape(10.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    Text("•••", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // El cuerpo del gato con su propia área de toque para sus interacciones
                    Box(
                        modifier = Modifier
                            .size(AREA_TOQUE_MIMI)
                            .drawBehind {
                                if (persona.parcheActivo) {
                                    drawCircle(
                                        color = Color(0xFF38F5D0).copy(alpha = 0.48f),
                                        radius = size.maxDimension * 0.68f
                                    )
                                }
                            }
                            .semantics { contentDescription = "Michi de ${persona.nombre}" }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // El gato SIEMPRE mantiene sus interacciones propias (ficha, parche, etc.)
                                if (persona.parcheActivo) {
                                    solicitarA = persona
                                } else {
                                    seleccionadoId = persona.id
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        MimiMapaSprite(
                            semilla = persona.id,
                            tiempoSegundos = { if (obraActiva != null) 0f else segundosDeAnimacion(cuadro) },
                            caminando = { obraActiva == null && simulacion.mimi(persona.id)?.caminando == true },
                            modifier = Modifier
                                .size(TAMANO_MIMI)
                                .graphicsLayer {
                                    scaleX = if (mimi?.mirandoDerecha == true) 1f else -1f
                                }
                        )
                    }
                }
            }
        }

        // Gatito GPS del amigo: solo cuando hay encuentro ACTIVO con ubicación conocida.
        // Se ancla a las coordenadas reales del amigo, igual que un marcador del mapa.
        val amigoUbicacion = amigoEncuentro?.amigoUbicacion
        if (amigoEncuentro != null && amigoUbicacion != null) {
            MimiAmigoGps(
                semilla          = amigoEncuentro.amigoId,
                nombre           = amigoEncuentro.amigoNombre,
                latitud          = amigoUbicacion.latitud,
                longitud         = amigoUbicacion.longitud,
                estadoCamara     = estadoCamara,
                tiempoAnimacion  = cuadro,
                areaToquePx      = areaToquePx,
                onEscribir       = { onEscribir(amigoEncuentro.amigoId, amigoEncuentro.amigoNombre) }
            )
        }

        solicitarA?.let { persona ->
            AlertDialog(
                onDismissRequest = { solicitarA = null },
                title = { Text("Sincronización de Parche") },
                text = { Text("Sincronizarás los eventos descubiertos por ${persona.nombre}, sí o no") },
                // Sí o No: después se abre la ficha del Michi (perfil, escribir, chispa, medicina).
                confirmButton = {
                    TextButton(onClick = {
                        persona.usuarioId?.let(onSolicitarSincronizacion)
                        solicitarA = null
                        seleccionadoId = persona.id
                    }) { Text("Sí") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        solicitarA = null
                        seleccionadoId = persona.id
                    }) { Text("No") }
                }
            )
        }
        estadoAbierto?.let { persona ->
            DialogoEstadoMichi(
                persona = persona,
                onCerrar = { estadoAbierto = null },
                onResponder = { respuesta ->
                    val id = persona.usuarioId
                    id != null && onResponderEstado(id, persona.estadoMichi.orEmpty(), respuesta)
                }
            )
        }

        val personaSeleccionada = seleccionadoId?.let { id -> visibles.firstOrNull { it.id == id } }
        if (personaSeleccionada != null) {
            val estadoVigente = personaSeleccionada.estadoMichi
                ?.takeIf { it.isNotBlank() && (personaSeleccionada.estadoMichiHasta ?: 0L) > System.currentTimeMillis() }
            val relacionSocial = remember(personaSeleccionada.usuarioId) {
                personaSeleccionada.usuarioId?.let { repoPersonajes.obtenerRelacion(it) }
            }
            val obraActiva = personaSeleccionada.usuarioId?.let { obrasPorAutor[it] }
            DialogoMimi(
                persona = personaSeleccionada,
                estado = estadoVigente,
                relacionSocial = relacionSocial,
                obraActiva = obraActiva,
                onCerrar = { seleccionadoId = null },
                onVerPerfil = {
                    seleccionadoId = null
                    personaSeleccionada.usuarioId?.let(onVerPerfil)
                },
                onEscribir = {
                    seleccionadoId = null
                    personaSeleccionada.usuarioId?.let { onEscribir(it, personaSeleccionada.nombre) }
                },
                onVerEstado = {
                    seleccionadoId = null
                    estadoAbierto = personaSeleccionada
                },
                onAbrirObra = { obra ->
                    seleccionadoId = null
                    obraPergaminoAbierta = obra
                    onAbrirObra(obra)
                },
                // El diálogo se queda abierto: así se puede mandar una chispa y luego medicina.
                onEnviarChispa = {
                    personaSeleccionada.usuarioId?.let { onEnviarChispa(it, personaSeleccionada.nombre) }
                },
                onEnviarMedicina = {
                    personaSeleccionada.usuarioId?.let { onEnviarMedicina(it, personaSeleccionada.nombre) }
                }
            )
        }

        obraPergaminoAbierta?.let { obra ->
            DialogoPergaminoObra(
                obra = obra,
                usuarioIdActual = usuarioIdPropio,
                onCerrar = { obraPergaminoAbierta = null },
                onResponder = { autorId, autorNombre ->
                    obraPergaminoAbierta = null
                    onEscribir(autorId, autorNombre)
                }
            )
        }
    }
}

/** Nanosegundos del reloj de cuadros -> segundos manejables (el Float pierde precisión con valores enormes). */
private fun segundosDeAnimacion(nanos: Long): Float = ((nanos / 1_000_000L) % 100_000L) / 1000f

/**
 * Gatito del amigo anclado a su posición GPS real.
 *
 * A diferencia de los Mimis ficticios (que deambulan aleatoriamente), este Mimi:
 * - Siempre camina (animación continua, igual que un marcador vivo).
 * - Se posiciona en las coordenadas GPS reales del amigo usando [ProyeccionMapa].
 * - Muestra el nombre del amigo encima.
 * - Al tocarlo abre un diálogo con nombre, distancia aproximada y opción de escribir.
 *
 * PRIVACIDAD: aquí sí se usa la ubicación real porque el amigo la compartió voluntariamente
 * al aceptar el encuentro ([SesionEncuentro.estado] == ACTIVO).
 */
@Composable
private fun MimiAmigoGps(
    semilla: String,
    nombre: String,
    latitud: Double,
    longitud: Double,
    estadoCamara: EstadoCamaraOsm,
    tiempoAnimacion: Long,
    areaToquePx: Float,
    onEscribir: () -> Unit
) {
    val radarColors = LocalRadarColors.current
    var dialogoAbierto by remember { mutableStateOf(false) }
    val colorAmigo = Color(0xFF0A84FF) // ColorPin.AMIGO

    // Calcula posición en pantalla leyendo la proyección sin recomponer.
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(AREA_TOQUE_MIMI)
                .graphicsLayer {
                    // Leemos estadoCamara.version y tiempoAnimacion para que graphicsLayer se
                    // redibujesolo en cuadros de animación o cambios de cámara, sin recomponer.
                    tiempoAnimacion
                    estadoCamara.version
                    val proyeccion = estadoCamara.proyeccion
                    if (proyeccion == null) {
                        alpha = 0f
                    } else {
                        val pos = proyeccion.aPantalla(latitud, longitud)
                        translationX = pos.x - areaToquePx / 2f
                        translationY = pos.y - areaToquePx / 2f
                        alpha = 1f
                    }
                }
                .drawBehind {
                    // Halo azul del amigo: más grande que el turquesa de parche.
                    drawCircle(
                        color = colorAmigo.copy(alpha = 0.35f),
                        radius = size.maxDimension * 0.80f
                    )
                }
                .semantics { contentDescription = "Michi de $nombre (encuentro activo)" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { dialogoAbierto = true },
            contentAlignment = Alignment.Center
        ) {
            MimiMapaSprite(
                semilla = semilla,
                tiempoSegundos = { segundosDeAnimacion(tiempoAnimacion) },
                caminando = { true }, // siempre camina: está en movimiento real
                modifier = Modifier.size(TAMANO_MIMI)
            )
            // Nombre del amigo encima del gatito.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-16).dp),
                color = colorAmigo.copy(alpha = 0.90f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp
            ) {
                Text(
                    text = nombre.ifBlank { "Amigo" },
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }

    // Diálogo al tocar el gatito del amigo: nombre + botones de chat y de ver la ruta.
    if (dialogoAbierto) {
        AlertDialog(
            onDismissRequest = { dialogoAbierto = false },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MimiMapaSprite(
                        semilla = semilla,
                        tiempoSegundos = { 0f },
                        caminando = { false },
                        modifier = Modifier.size(32.dp)
                    )
                    Text(nombre.ifBlank { "Amigo" }, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Estás en un encuentro activo con esta persona.\nSu posición se actualiza en tiempo real.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogoAbierto = false
                    onEscribir()
                }) { Text("✉  Escribir", color = radarColors.verdeBrillante, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { dialogoAbierto = false }) { Text("Cerrar") }
            }
        )
    }
}


/**
 * Ficha de un Michi: se abre SOBREPUESTA y CENTRADA (igual que el estado) y ordena la información en
 * bloques: quién es, su estado (si tiene), lo que puedes hacer con la persona y lo que puedes mandar a su Michi.
 */
@Composable
private fun DialogoMimi(
    persona: Dispositivo,
    estado: String?,
    relacionSocial: Relacion? = null,
    obraActiva: com.renea.arete_social.domain.model.ObraLibreria? = null,
    onCerrar: () -> Unit,
    onVerPerfil: () -> Unit,
    onEscribir: () -> Unit,
    onVerEstado: () -> Unit,
    onAbrirObra: ((com.renea.arete_social.domain.model.ObraLibreria) -> Unit)? = null,
    onEnviarChispa: () -> Unit,
    onEnviarMedicina: () -> Unit
) {
    val radarColors = LocalRadarColors.current
    val tienePerfil = persona.usuarioId != null

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 340.dp).fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabecera: cerrar a la derecha, Michi grande al centro.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .semantics { contentDescription = "Cerrar" }
                            .clickable(onClick = onCerrar)
                            .padding(6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(84.dp)
                            .clip(RoundedCornerShape(42.dp))
                            .background(radarColors.verde.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Quieto y mirando al frente.
                        MimiMapaSprite(
                            semilla = persona.id,
                            tiempoSegundos = { 0f },
                            caminando = { false },
                            modifier = Modifier.size(62.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = persona.nombre.ifBlank { "Alguien cerca" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !tienePerfil -> "Dispositivo cercano"
                            persona.parcheActivo -> "Parche activo"
                            else -> "Cerca de ti"
                        },
                        color = if (persona.parcheActivo) radarColors.verdeBrillante else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    // Distintivo social de amigo si existe relación registrada
                    if (relacionSocial != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🐾", fontSize = 12.sp)
                                Text(
                                    text = "Mascota: ${relacionSocial.nombreOtro} (${relacionSocial.nivelVinculo.titulo} · ${relacionSocial.confianza} pts)",
                                    color = radarColors.verdeBrillante,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Estado vigente del Michi: toca para leerlo completo y responder.
                if (estado != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onVerEstado),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("💬", fontSize = 14.sp)
                            Text(
                                text = estado,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Responder", color = radarColors.verdeBrillante, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Obra literaria que está leyendo actualmente en el mapa
                if (obraActiva != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAbrirObra?.invoke(obraActiva)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF9E6),
                        border = BorderStroke(1.dp, Color(0xFFD4B483).copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📖", fontSize = 16.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Leyendo en el mapa",
                                    color = Color(0xFF7A6453),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = obraActiva.titulo,
                                    color = Color(0xFF332014),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "Leer",
                                color = Color(0xFF8B5A2B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (tienePerfil) {
                    SeccionAcciones(titulo = "Con ${persona.nombre.ifBlank { "esta persona" }}") {
                        AccionMimi(texto = "Ver perfil", onClick = onVerPerfil, modifier = Modifier.weight(1f))
                        AccionMimi(texto = "✉  Escribir", onClick = onEscribir, modifier = Modifier.weight(1f))
                    }
                    // Chispas y medicina para su mascota (el otro teléfono las recibe y las aplica).
                    SeccionAcciones(titulo = "Para su Michi") {
                        AccionMimi(texto = "✦  Chispa", onClick = onEnviarChispa, modifier = Modifier.weight(1f))
                        AccionMimi(texto = "💊  Medicina", onClick = onEnviarMedicina, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeccionAcciones(titulo: String, contenido: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = titulo.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
    }
}

@Composable
private fun AccionMimi(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val radarColors = LocalRadarColors.current
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(radarColors.verde.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            color = radarColors.verdeBrillante,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** Estado del Michi de otra persona, centrado y sobrepuesto, con opción de responderle a su dueño. */
@Composable
private fun DialogoEstadoMichi(
    persona: Dispositivo,
    onCerrar: () -> Unit,
    onResponder: suspend (respuesta: String) -> Boolean
) {
    val radarColors = LocalRadarColors.current
    val scope = rememberCoroutineScope()
    val puedeResponder = persona.usuarioId != null
    var respondiendo by remember { mutableStateOf(false) }
    var respuesta by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var enviado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!enviando) onCerrar() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 340.dp).fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, radarColors.verde.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MimiMapaSprite(
                        semilla = persona.id,
                        tiempoSegundos = { 0f },
                        caminando = { false },
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Estado de ${persona.nombre.ifBlank { "este Michi" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = persona.estadoMichi.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                when {
                    enviado -> Text(
                        text = "✓ Respuesta enviada a ${persona.nombre.ifBlank { "su dueño" }}",
                        color = radarColors.verdeBrillante,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    respondiendo -> {
                        OutlinedTextField(
                            value = respuesta,
                            onValueChange = { respuesta = it.take(300); error = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Escribe tu respuesta…") },
                            enabled = !enviando,
                            maxLines = 4,
                            isError = error,
                            supportingText = { if (error) Text("No se pudo enviar. Inténtalo de nuevo.") }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (respondiendo && !enviado) {
                        TextButton(enabled = !enviando, onClick = { respondiendo = false; error = false }) { Text("Cancelar") }
                        TextButton(
                            enabled = !enviando && respuesta.isNotBlank(),
                            onClick = {
                                enviando = true
                                error = false
                                scope.launch {
                                    val ok = runCatching { onResponder(respuesta.trim()) }.getOrDefault(false)
                                    enviando = false
                                    if (ok) { enviado = true; respondiendo = false } else error = true
                                }
                            }
                        ) { Text(if (enviando) "Enviando…" else "Enviar") }
                    } else {
                        TextButton(onClick = onCerrar) { Text("Cerrar") }
                        if (puedeResponder && !enviado) {
                            TextButton(onClick = { respondiendo = true }) { Text("Responder") }
                        }
                    }
                }
            }
        }
    }
}

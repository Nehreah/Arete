package com.renea.arete_social.gui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.feature.auth.GestorSesionLocal
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.model.PetType
import kotlinx.coroutines.launch

/**
 * Pantalla de bienvenida e inicialización de identidad soberana.
 * Sin servidores ni base de datos remota: todo se almacena en el dispositivo.
 *
 * Diseño minimalista: tipografía sans-serif de tamaño pequeño, campos con línea
 * inferior, fondo plano y un único acento (el rojo de marca) para la acción principal.
 */
@Composable
fun BienvenidaScreen(
    petRepository: PetRepository,
    usuarioId: String,
    onCompletado: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var alias by remember { mutableStateOf("") }
    var nombreMichi by remember { mutableStateOf("Michi") }
    var fotoUri by remember { mutableStateOf<Uri?>(null) }
    var esMayor18Aceptado by remember { mutableStateOf(false) }
    var mostrarDialogoEula by remember { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }

    val selectorFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            fotoUri = uri
        }
    }

    val abrirSelectorFoto: () -> Unit = {
        selectorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ─── CABECERA ────────────────────────────────────────
                Text(
                    text = "ARÉTE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 5.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Red local, soberana y P2P",
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Versión Alpha · No lanzada",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sin cuentas en la nube ni servidores centrales.\nTu identidad y tus datos viven solo en este dispositivo.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ─── 1. FOTO (opcional) ──────────────────────────────
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable(onClick = abrirSelectorFoto)
                        .semantics { contentDescription = "Elegir foto de perfil" },
                    contentAlignment = Alignment.Center
                ) {
                    if (fotoUri != null) {
                        AsyncImage(
                            model = fotoUri,
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "+",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (fotoUri == null) "Añadir foto" else "Cambiar foto",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = abrirSelectorFoto)
                            .padding(vertical = 8.dp)
                    )
                    if (fotoUri != null) {
                        Text(
                            text = "Quitar",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { fotoUri = null }
                                .padding(vertical = 8.dp)
                        )
                    }
                }

                Text(
                    text = "opcional",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ─── 2. ALIAS / NOMBRE PÚBLICO ───────────────────────
                CampoMinimal(
                    valor = alias,
                    onCambio = { alias = it },
                    etiqueta = "Tu identidad pública",
                    placeholder = "Alias o nombre · Ej. Caminante, Neo, Cero…",
                    maxCaracteres = 25,
                    imeAction = ImeAction.Next,
                    onAccion = { focusManager.moveFocus(FocusDirection.Next) }
                )

                Spacer(modifier = Modifier.height(22.dp))

                // ─── 3. NOMBRE DEL MICHI ─────────────────────────────
                CampoMinimal(
                    valor = nombreMichi,
                    onCambio = { nombreMichi = it },
                    etiqueta = "Tu compañero Michi",
                    placeholder = "Nombre · Ej. Michi, Kuro, Pelusa…",
                    maxCaracteres = 20,
                    imeAction = ImeAction.Done,
                    onAccion = { focusManager.clearFocus() }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ─── 4. TÉRMINOS, EULA Y MAYORÍA DE EDAD (+18) ───────
                CasillaMinimal(
                    marcada = esMayor18Aceptado,
                    onCambio = { esMayor18Aceptado = it },
                    texto = "Confirmo que soy mayor de 18 años y acepto el EULA, las Normas y la Política de Privacidad."
                )

                Text(
                    text = "Leer EULA y normas comunitarias",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 28.dp)
                        .clickable { mostrarDialogoEula = true }
                        .padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ─── BOTÓN PRINCIPAL: COMENZAR ───────────────────────
                val puedeComenzar = alias.trim().isNotBlank() && esMayor18Aceptado && !procesando

                Button(
                    onClick = {
                        if (!puedeComenzar) return@Button
                        procesando = true
                        scope.launch {
                            val nombreLimpio = alias.trim()
                            val michiLimpio = nombreMichi.trim().ifBlank { "Michi" }

                            // 1. Guardar nombre y michi en sesión local
                            GestorSesionLocal.guardarUltimoNombre(nombreLimpio)
                            GestorSesionLocal.guardarNombreMichi(michiLimpio)

                            // 2. Procesar foto si fue seleccionada
                            var fotoFinalRuta: String? = null
                            if (fotoUri != null) {
                                runCatching {
                                    val bytes = context.contentResolver.openInputStream(fotoUri!!)?.use { it.readBytes() }
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        val res = AlmacenMediaPerfil.guardarImagen(bytes, esAvatar = true, extension = "jpg")
                                        fotoFinalRuta = res.rutaMiniatura
                                    }
                                }
                            }
                            GestorSesionLocal.guardarUltimaFoto(fotoFinalRuta)

                            // 3. Crear o asegurar mascota en PetRepository
                            petRepository.ensurePetExists(usuarioId, michiLimpio, PetType.GATO)

                            // 4. Marcar onboarding como completado
                            GestorSesionLocal.guardarOnboardingCompletado(true)

                            procesando = false
                            onCompletado()
                        }
                    },
                    enabled = puedeComenzar,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = if (procesando) "Preparando nodo…" else "Comenzar en Aréte",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }

    if (mostrarDialogoEula) {
        DialogoEulaNormas(
            onDismiss = { mostrarDialogoEula = false },
            onAceptar = {
                esMayor18Aceptado = true
                mostrarDialogoEula = false
            }
        )
    }
}

/**
 * Campo de texto minimalista: etiqueta pequeña arriba, contador discreto a la derecha
 * y una línea inferior que se resalta con el acento de marca al recibir foco.
 */
@Composable
private fun CampoMinimal(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    placeholder: String,
    maxCaracteres: Int,
    imeAction: ImeAction,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaccion = remember { MutableInteractionSource() }
    val enFoco by interaccion.collectIsFocusedAsState()

    val colorLinea by animateColorAsState(
        targetValue = if (enFoco) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        label = "lineaCampo"
    )
    val colorEtiqueta by animateColorAsState(
        targetValue = if (enFoco) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "etiquetaCampo"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = etiqueta,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                color = colorEtiqueta
            )
            Text(
                text = "${valor.length}/$maxCaracteres",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }

        BasicTextField(
            value = valor,
            onValueChange = { if (it.length <= maxCaracteres) onCambio(it) },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onAccion() },
                onDone = { onAccion() }
            ),
            interactionSource = interaccion,
            decorationBox = { campoInterno ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (valor.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                    campoInterno()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (enFoco) 1.5.dp else 1.dp)
                .background(colorLinea)
        )
    }
}

/**
 * Casilla compacta (18 dp) con texto pequeño. Toda la fila es el área táctil
 * y se expone a accesibilidad con rol de casilla de verificación.
 */
@Composable
private fun CasillaMinimal(
    marcada: Boolean,
    onCambio: (Boolean) -> Unit,
    texto: String,
    modifier: Modifier = Modifier
) {
    val primario = MaterialTheme.colorScheme.primary
    val colorBorde by animateColorAsState(
        targetValue = if (marcada) primario
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        label = "bordeCasilla"
    )
    val colorFondo by animateColorAsState(
        targetValue = if (marcada) primario else Color.Transparent,
        label = "fondoCasilla"
    )
    val colorCheck = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = marcada,
                role = Role.Checkbox,
                onValueChange = onCambio
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colorFondo)
                .border(1.dp, colorBorde, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (marcada) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    val trazo = Path().apply {
                        moveTo(size.width * 0.02f, size.height * 0.55f)
                        lineTo(size.width * 0.38f, size.height * 0.92f)
                        lineTo(size.width * 0.98f, size.height * 0.10f)
                    }
                    drawPath(
                        path = trazo,
                        color = colorCheck,
                        style = Stroke(
                            width = 1.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(10.dp))

        Text(
            text = texto,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}

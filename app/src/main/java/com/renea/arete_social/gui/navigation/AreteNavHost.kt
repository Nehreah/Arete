package com.renea.arete_social.gui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.renea.arete_social.ContenedorApp
import com.renea.arete_social.feature.auth.GestorAmistades
import com.renea.arete_social.feature.auth.PerfilViewModel
import com.renea.arete_social.feature.cartas.CartasViewModel
import com.renea.arete_social.feature.libreria.LibreriaViewModel
import com.renea.arete_social.feature.auth.SesionUiState
import com.renea.arete_social.feature.auth.SesionViewModel
import com.renea.arete_social.feature.comunidades.ComunidadesViewModel
import com.renea.arete_social.feature.mapa.MapaViewModel
import com.renea.arete_social.feature.mensajeria.SalasViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.renea.arete_social.domain.model.TipoNotificacionTactica
import com.renea.arete_social.gui.mapa.PantallaSecundaria
import com.renea.arete_social.gui.theme.BannerNotificacionTactica
import com.renea.arete_social.gui.mapa.MapaScreen
import com.renea.arete_social.feature.identidad.IdentityViewModel
import com.renea.arete_social.gui.identidad.PantallaIdentidad

/**
 * Punto único de navegación de la app. Cada destino crea su ViewModel con
 * viewModelFactory { initializer { ... } } — no hay Hilt ni Koin, así que las
 * dependencias salen directo de [ContenedorApp] (creado una vez en MainActivity).
 *
 * El Mapa es el único destino "principal" de la app y actúa como contenedor
 * de toda la navegación posterior al login (perfil, comunidades, chats, etc.).
 */
@Composable
fun AreteNavHost(contenedor: ContenedorApp) {
    val navController = rememberNavController()

    val sesionViewModel: SesionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SesionViewModel(
                    repositorioAutenticacion = null,
                    repositorioUsuarios = contenedor.repositorioUsuarios,
                    cifradorE2ee = contenedor.cifradorE2ee,
                    gestorIdentidad = contenedor.gestorIdentidad
                )
            }
        }
    )

    val estadoSesion by sesionViewModel.estado.collectAsState()
    val onboardingCompletado = com.renea.arete_social.feature.auth.GestorSesionLocal.estaOnboardingCompletado()
    val inicio: Any = if (!onboardingCompletado) Bienvenida else Mapa

    LaunchedEffect(estadoSesion) {
        if (estadoSesion is SesionUiState.Autenticado) {
            contenedor.iniciarMesh()
        }
    }

    val notifActiva by contenedor.gestorLlamadas.notificacionActiva.collectAsState()
    val llamadaActual by contenedor.gestorLlamadas.llamadaActual.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = inicio) {

            composable<Bienvenida> {
                val usuarioId = (estadoSesion as? SesionUiState.Autenticado)?.usuarioId
                    ?: com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoUsuario()
                    ?: "invitado-local-${java.util.UUID.randomUUID()}"
                com.renea.arete_social.gui.auth.BienvenidaScreen(
                    petRepository = contenedor.petRepository,
                    usuarioId = usuarioId,
                    onCompletado = {
                        navController.navigate(Mapa) {
                            popUpTo(Bienvenida) { inclusive = true }
                        }
                    }
                )
            }

            composable<Login> {
                com.renea.arete_social.gui.auth.BienvenidaScreen(
                    petRepository = contenedor.petRepository,
                    usuarioId = (estadoSesion as? SesionUiState.Autenticado)?.usuarioId ?: "invitado-local",
                    onCompletado = {
                        navController.navigate(Mapa) { popUpTo(Login) { inclusive = true } }
                    }
                )
            }

            composable<Registro> {
                com.renea.arete_social.gui.auth.BienvenidaScreen(
                    petRepository = contenedor.petRepository,
                    usuarioId = (estadoSesion as? SesionUiState.Autenticado)?.usuarioId ?: "invitado-local",
                    onCompletado = {
                        navController.navigate(Mapa) { popUpTo(Registro) { inclusive = true } }
                    }
                )
            }

            composable<Identidad> {
                val identityVm: IdentityViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { IdentityViewModel(contenedor.identityManager) }
                    }
                )
                PantallaIdentidad(
                    viewModel = identityVm,
                    onAtras = { navController.popBackStack() }
                )
            }

            composable<Mapa> {
                // Se usa el usuarioId que ya decidió SesionViewModel (con fallback local
                // para cuando no hay wifi). Si el estado está en Cargando y sin sesión
                // local, esperamos con return@composable sin crashear.
                val usuarioId = (estadoSesion as? SesionUiState.Autenticado)?.usuarioId
                    ?: com.renea.arete_social.feature.auth.GestorSesionLocal.obtenerUltimoUsuario()
                    ?: return@composable

                // Las amistades viven en el servidor: se cargan al entrar y se refrescan cada 30 s
                // (y cada vez que se abre un perfil). Con sesión local (sin cuenta) no hay nada que sincronizar.
                val contextoApp = androidx.compose.ui.platform.LocalContext.current.applicationContext
                LaunchedEffect(usuarioId) {
                    GestorAmistades.inicializar(contextoApp, usuarioId)
                    if (!usuarioId.startsWith("invitado-local-")) {
                        while (true) {
                            GestorAmistades.sincronizar(contenedor.modulo.amistades, usuarioId)
                            // Espera 30 s o hasta que llegue un aviso en vivo (solicitud nueva, respuesta…).
                            kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                                GestorAmistades.sincronizacionPedida.first()
                            }
                        }
                    }
                }

                val vm: MapaViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            MapaViewModel(
                                contenedor.detectorProximidad,
                                contenedor.modulo.lugares,
                                contenedor.repositorioEventos,
                                contenedor.repositorioUsuarios,
                                usuarioId,
                                servicioDescubrimiento = contenedor.servicioDescubrimiento,
                                servicioRegionActual = contenedor.servicioRegionActual,
                                servicioModoSuper = contenedor.servicioModoSuper,
                                servicioVisionRegional = contenedor.servicioVisionRegional,
                                servicioPases = contenedor.servicioPases,
                                repositorioLibreria = contenedor.libreria,
                                petRepository = contenedor.petRepository,
                                context = contextoApp,
                                gestorAutoPausa = contenedor.gestorAutoPausaUbicacion
                            )
                        }
                    }
                )
                val comunidadesVm: ComunidadesViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { ComunidadesViewModel(contenedor.repositorioComunidades, contenedor.communityMeshSyncManager) }
                    }
                )
                val perfilVm: PerfilViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            PerfilViewModel(
                                contenedor.repositorioUsuarios,
                                usuarioId,

                                contenedor.mensajes,
                                contenedor.gestorLlamadas,
                                contenedor.gestorOutboxLocal,
                                esInvitado = (estadoSesion as? SesionUiState.Autenticado)?.let { it.esInvitado && !it.soloLocal } == true,
                                repositorioAmistades = contenedor.modulo.amistades,
                                repositorioPublicaciones = contenedor.modulo.publicacionesPerfil,
                                cachePerfil = contenedor.cachePerfil,
                                cifradorE2ee = contenedor.cifradorE2ee,
                                cacheUsuariosLocal = contenedor.cacheUsuarios,
                                onPerfilGuardado = { contenedor.gestorPerfilBle.emitirPerfilActualizado() }
                            )
                        }
                    }
                )

                val esInvitadoSesion = (estadoSesion as? SesionUiState.Autenticado)?.let { it.esInvitado && !it.soloLocal } == true
                val cartasVm: CartasViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            CartasViewModel(
                                repositorio = contenedor.cartas,
                                usuarioId = usuarioId,
                                esInvitado = esInvitadoSesion,
                                esAmigo = { id -> GestorAmistades.amigosIds.value.contains(id) },
                                cachePerfil = contenedor.cachePerfil
                            )
                        }
                    }
                )
                val libreriaVm: LibreriaViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            LibreriaViewModel(contenedor.libreria, usuarioId, esInvitadoSesion, contenedor.cachePerfil)
                        }
                    }
                )

                val salasVm: SalasViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            SalasViewModel(
                                repositorioSalas = contenedor.modulo.salas,
                                usuarioIdActual = usuarioId,
                                repositorioComunidades = contenedor.repositorioComunidades,
                                servicioDescubrimiento = contenedor.servicioDescubrimiento,
                                servicioRegionActual = contenedor.servicioRegionActual,
                                servicioModoSuper = contenedor.servicioModoSuper,
                                servicioVisionRegional = contenedor.servicioVisionRegional
                            )
                        }
                    }
                )

                val contextoActual = androidx.compose.ui.platform.LocalContext.current.applicationContext

                MapaScreen(
                    viewModel = vm,
                    comunidadesViewModel = comunidadesVm,
                    perfilViewModel = perfilVm,
                    cartasViewModel = cartasVm,
                    libreriaViewModel = libreriaVm,
                    salasViewModel = salasVm,
                    contenedor = contenedor,
                    usuarioIdActual = usuarioId,
                    onCerrarSesion = {
                        contenedor.detenerMesh()
                        com.renea.arete_social.feature.auth.GestorSesionLocal.limpiar()
                        navController.navigate(Bienvenida) { popUpTo(Mapa) { inclusive = true } }
                    },
                    onEliminarCuenta = {
                        contenedor.detenerMesh()
                        com.renea.arete_social.feature.auth.GestorSesionLocal.limpiar()
                        navController.navigate(Bienvenida) { popUpTo(Mapa) { inclusive = true } }
                    }
                )

            }
        }

        BannerNotificacionTactica(
            notif = notifActiva,
            onDescartar = { contenedor.gestorLlamadas.descartarNotificacion() },
            onAbrir = { notif ->
                when (notif.tipo) {
                    TipoNotificacionTactica.SOLICITUD_AMISTAD,
                    TipoNotificacionTactica.AMIGO_AGREGADO,
                    TipoNotificacionTactica.AMISTAD_ACEPTADA -> {
                        contenedor.solicitudNavegacionSecundaria.tryEmit(
                            PantallaSecundaria.VerPerfil(notif.remitenteId)
                        )
                    }
                    TipoNotificacionTactica.MENSAJE -> {
                        if (!notif.chatId.isNullOrBlank()) {
                            contenedor.solicitudNavegacionSecundaria.tryEmit(
                                PantallaSecundaria.AbrirChat(notif.chatId, "Chat: ${notif.remitenteNombre}")
                            )
                        } else {
                            contenedor.solicitudNavegacionSecundaria.tryEmit(
                                PantallaSecundaria.AbrirChatConUsuario(notif.remitenteId, "Chat: ${notif.remitenteNombre}")
                            )
                        }
                    }
                    TipoNotificacionTactica.LLAMADA -> {
                        // Las llamadas entrantes se gestionan con su propio diálogo
                    }
                    TipoNotificacionTactica.REGALO_MASCOTA -> {
                        // Es solo un aviso: el regalo ya se aplicó a la mascota.
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        val callEntrante = llamadaActual
        if (callEntrante?.estado == com.renea.arete_social.domain.model.EstadoLlamada.ENTRANTE) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { contenedor.gestorLlamadas.colgarLlamada() },
                title = {
                    androidx.compose.material3.Text(
                        "📞 LLAMADA ENTRANTE",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black
                    )
                },
                text = {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.Text(
                            text = callEntrante.remoto.nombre.ifBlank { "Contacto cercano" },
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Text(
                            text = "Llamada directa P2P sobre malla local",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            contenedor.gestorLlamadas.contestarLlamada()
                            contenedor.solicitudNavegacionSecundaria.tryEmit(
                                PantallaSecundaria.LlamadaVideo(
                                    contactoId = callEntrante.remoto.id,
                                    nombre = callEntrante.remoto.nombre,
                                    foto = callEntrante.remoto.foto
                                )
                            )
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF00E676),
                            contentColor = androidx.compose.ui.graphics.Color.Black
                        )
                    ) {
                        androidx.compose.material3.Text("ACEPTAR", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { contenedor.gestorLlamadas.colgarLlamada() },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = androidx.compose.ui.graphics.Color(0xFFFF5252)
                        )
                    ) {
                        androidx.compose.material3.Text("RECHAZAR", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            )
        }
    }
}
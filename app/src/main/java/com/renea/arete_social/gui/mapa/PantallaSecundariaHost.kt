package com.renea.arete_social.gui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.renea.arete_social.ContenedorApp
import com.renea.arete_social.feature.auth.PerfilPublicoViewModel
import com.renea.arete_social.feature.comunidades.ComunidadDetalleViewModel
import com.renea.arete_social.feature.comunidades.CrearComunidadViewModel
import com.renea.arete_social.feature.eventos.CrearEventoViewModel
import com.renea.arete_social.feature.eventos.EventoDetalleViewModel
import com.renea.arete_social.feature.lugares.CrearLugarViewModel
import com.renea.arete_social.feature.lugares.LugarDetalleViewModel
import com.renea.arete_social.feature.mensajeria.ChatViewModel
import com.renea.arete_social.gui.comunidad.ComunidadDetalleScreen
import com.renea.arete_social.gui.comunidad.nueva.CrearComunidadScreen
import com.renea.arete_social.gui.auth.perfil.PerfilPublicoScreen
import com.renea.arete_social.gui.evento.EventoDetalleScreen
import com.renea.arete_social.gui.evento.nuevo.CrearEventoScreen
import com.renea.arete_social.gui.lugar.LugarDetalleScreen
import com.renea.arete_social.gui.lugar.nuevo.CrearLugarScreen
import com.renea.arete_social.gui.mensajeria.ChatScreen
import com.renea.arete_social.gui.theme.RadarScaffold

@Composable
fun PantallaSecundariaHost(
    pantalla: PantallaSecundaria,
    contenedor: ContenedorApp,
    usuarioIdActual: String,
    onAtras: () -> Unit,
    onAbrir: (PantallaSecundaria) -> Unit,
    onReemplazar: (PantallaSecundaria) -> Unit,
    onIrAlMapa: () -> Unit = {},
    personasCercanas: List<com.renea.arete_social.domain.model.Dispositivo> = emptyList(),
    listaAmigos: List<com.renea.arete_social.domain.model.Usuario> = emptyList(),
    salas: List<com.renea.arete_social.domain.model.Sala> = emptyList(),
    regionDestacadaChatId: String? = null,
    visibilidadPorChat: Map<String, com.renea.arete_social.domain.model.VisibilidadMapa> = emptyMap(),
    /** Sesión de invitado: sin cuenta no hay servidor donde guardar cartas. */
    esInvitado: Boolean = false,
    onCentrarEnMapa: (Double, Double) -> Unit = { _, _ -> }
) {
    when (pantalla) {

        is PantallaSecundaria.CrearLugarEn -> {
            val vm: CrearLugarViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer { CrearLugarViewModel(contenedor.modulo.lugares, pantalla.ubicacion) }
                }
            )
            RadarScaffold(titulo = "Nuevo lugar", onAtras = onAtras) {
                CrearLugarScreen(
                    viewModel = vm,
                    onLugarCreado = { lugarId ->
                        onReemplazar(PantallaSecundaria.VerLugar(lugarId))
                    },
                    onCancelar = onAtras
                )
            }
        }

        is PantallaSecundaria.VerLugar -> {
            val vm: LugarDetalleViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer { LugarDetalleViewModel(contenedor.modulo.lugares, pantalla.lugarId) }
                }
            )
            RadarScaffold(titulo = "Lugar", onAtras = onAtras) {
                LugarDetalleScreen(
                    viewModel = vm,
                    onAbrirChat = { chatId -> onAbrir(PantallaSecundaria.AbrirChat(chatId, esSala = true)) },
                    onCrearEventoAqui = { lugarId -> onAbrir(PantallaSecundaria.CrearEventoEn(lugarId)) }
                )
            }
        }

        is PantallaSecundaria.CrearEventoEn -> {
            val vm: CrearEventoViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer {
                        CrearEventoViewModel(contenedor.repositorioEventos, pantalla.lugarId, usuarioIdActual)
                    }
                }
            )
            RadarScaffold(titulo = "Nuevo evento", onAtras = onAtras) {
                CrearEventoScreen(
                    viewModel = vm,
                    onEventoCreado = { eventoId ->
                        onReemplazar(PantallaSecundaria.VerEvento(eventoId))
                    },
                    onCancelar = onAtras
                )
            }
        }

        is PantallaSecundaria.CrearEvento -> {
            val vm: CrearEventoViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer {
                        CrearEventoViewModel(
                            repositorioEventos = contenedor.repositorioEventos,
                            lugarId = pantalla.lugarId,
                            creadorId = usuarioIdActual,
                            comunidadId = pantalla.comunidadId
                        )
                    }
                }
            )
            RadarScaffold(titulo = "Nuevo evento", onAtras = onAtras) {
                CrearEventoScreen(
                    viewModel = vm,
                    onEventoCreado = { eventoId ->
                        onReemplazar(PantallaSecundaria.VerEvento(eventoId))
                    },
                    onCancelar = onAtras,
                    ubicacionInicial = pantalla.ubicacionInicial
                )
            }
        }

        is PantallaSecundaria.VerEvento -> {
            val vm: EventoDetalleViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer {
                        EventoDetalleViewModel(
                            contenedor.repositorioEventos,
                            pantalla.eventoId,
                            usuarioIdActual,
                            contenedor.repositorioComunidades
                        )
                    }
                }
            )
            RadarScaffold(titulo = "Evento", onAtras = onAtras) {
                EventoDetalleScreen(
                    viewModel = vm,
                    onAbrirChat = { chatId ->
                        val nombre = (vm.estado.value as? com.renea.arete_social.feature.eventos.EventoDetalleUiState.Listo)?.evento?.nombre ?: "Evento"
                        onAbrir(PantallaSecundaria.AbrirChat(chatId, "Chat: $nombre", esSala = true))
                    },
                    onVerEnMapa = onCentrarEnMapa,
                    onAtras = onAtras
                )
            }
        }

        is PantallaSecundaria.CrearComunidadNueva -> {
            val vm: CrearComunidadViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer { CrearComunidadViewModel(contenedor.repositorioComunidades) }
                }
            )
            RadarScaffold(titulo = "Nueva comunidad", onAtras = onAtras) {
                CrearComunidadScreen(
                    viewModel = vm,
                    onComunidadCreada = { comunidadId ->
                        onReemplazar(PantallaSecundaria.VerComunidad(comunidadId))
                    },
                    onCancelar = onAtras
                )
            }
        }

        is PantallaSecundaria.VerComunidad -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: ComunidadDetalleViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer {
                        com.renea.arete_social.feature.comunidades.data.GestorMuroComunidades.inicializar(context)
                        ComunidadDetalleViewModel(
                            contenedor.repositorioComunidades,
                            contenedor.repositorioEventos,
                            contenedor.repositorioUsuarios,
                            pantalla.comunidadId,
                            usuarioIdActual,
                            contenedor.communityMeshSyncManager
                        )
                    }
                }
            )
            ComunidadDetalleScreen(
                viewModel = vm,
                onAbrirChat = { chatId ->
                    val nombre = (vm.estado.value as? com.renea.arete_social.feature.comunidades.ComunidadDetalleUiState.Listo)?.comunidad?.nombre ?: "Comunidad"
                    onAbrir(PantallaSecundaria.AbrirChat(chatId, "Chat: $nombre", esSala = true))
                },
                repositorioMurales = contenedor.repositorioMurales,
                repositorioModeracion = contenedor.modulo.moderacion,


                onAtras = onAtras,
                onVerPerfil = { usuarioId -> onAbrir(PantallaSecundaria.VerPerfil(usuarioId)) },
                onVerEventoEnMapa = { eventoComunidad ->
                    val evId = eventoComunidad.eventoReal?.id ?: eventoComunidad.id
                    onAbrir(PantallaSecundaria.VerEvento(evId))
                },
                onCrearEventoComunitario = {
                    // Primero se elige el lugar en el mapa; el formulario se abre después.
                    onAbrir(PantallaSecundaria.SeleccionarLugarEvento(comunidadId = pantalla.comunidadId))
                }
            )
        }

        is PantallaSecundaria.VerPerfil -> {
            val vm: PerfilPublicoViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer {
                        PerfilPublicoViewModel(
                            contenedor.repositorioUsuarios,
                            contenedor.mensajes,

                            pantalla.usuarioId,
                            usuarioIdActual,
                            contenedor.gestorLlamadas,
                            contenedor.cartas,
                            esInvitado,
                            repositorioAmistades = contenedor.modulo.amistades,
                            repositorioPublicaciones = contenedor.modulo.publicacionesPerfil,
                            repositorioLibreria = contenedor.libreria,
                            gestorPerfilBle = contenedor.gestorPerfilBle,
                            cachePerfil = contenedor.cachePerfil,
                            cacheUsuariosLocal = contenedor.cacheUsuarios,
                            repositorioModeracion = contenedor.modulo.moderacion
                        )
                    }
                }
            )
            RadarScaffold(titulo = "Perfil", onAtras = onAtras) {
                PerfilPublicoScreen(
                    viewModel = vm,
                    onAbrirChat = { chatId, nombre ->
                        onAbrir(PantallaSecundaria.AbrirChat(chatId, "Chat: $nombre", esSala = false))
                    },
                    onIniciarVideollamada = { contactoId, nombre, foto ->
                        // Antes este botón no hacía nada (lambda vacía): ahora inicia la llamada.
                        contenedor.gestorLlamadas.iniciarLlamadaVideo(
                            com.renea.arete_social.domain.model.ContactoLlamada(contactoId, nombre, foto)
                        )
                        onAbrir(PantallaSecundaria.LlamadaVideo(contactoId, nombre, foto))
                    },
                    onVisitarHabitacion = { usuarioId, nombre ->
                        onAbrir(PantallaSecundaria.Habitacion(usuarioId, nombre))
                    },
                    // Ya no se usa la ubicación del perfil: la posición de la otra persona solo llega
                    // si acepta la invitación (ver GestorEncuentros).
                    onEncontrarnos = { usuarioId, nombre, _ ->
                        com.renea.arete_social.feature.encuentros.GestorEncuentros.solicitarEncuentro(
                            amigoId = usuarioId,
                            amigoNombre = nombre
                        )
                        onIrAlMapa()
                    }
                )
            }
        }

        // Se resuelve a un chat real en MapaScreen.abrirPantalla; aquí nunca llega a apilarse.
        is PantallaSecundaria.AbrirChatConUsuario -> Unit

        // Se resuelve en MapaScreen.abrirPantalla (cambia al mapa y activa la mira); nunca se apila.
        is PantallaSecundaria.SeleccionarLugarEvento -> Unit

        is PantallaSecundaria.AbrirChat -> {
            val vm: ChatViewModel = viewModel(
                key = pantalla.toString(),
                factory = viewModelFactory {
                    initializer {
                        ChatViewModel(
                            contenedor.mensajes,
                            contenedor.repositorioUsuarios,
                            contenedor.cifradorE2ee,

                            pantalla.chatId,
                            usuarioIdActual,
                            contenedor.gestorLlamadas,
                            contenedor.messageRouter,
                            contenedor.modulo.moderacion,
                            contenedor.resolverPerfiles,
                            resolverIdentidad = contenedor.resolverIdentidad
                        )
                    }
                }
            )
            RadarScaffold(titulo = pantalla.titulo, onAtras = onAtras) {
                val comunVM: com.renea.arete_social.feature.comunidades.ComunidadesViewModel = viewModel(
                    key = "comunidades_chat_selector",
                    factory = viewModelFactory {
                        initializer { com.renea.arete_social.feature.comunidades.ComunidadesViewModel(contenedor.repositorioComunidades, contenedor.communityMeshSyncManager) }
                    }
                )
                val comState by comunVM.estado.collectAsState()

                ChatScreen(
                    viewModel = vm,
                    chatId = pantalla.chatId,
                    gestorLlamadas = contenedor.gestorLlamadas,
                    onIniciarVideollamada = {
                        val contactoId = pantalla.chatId.takeIf { it != com.renea.arete_social.domain.repository.ID_CHAT_GENERAL } ?: "peer"
                        val nombre = pantalla.titulo.removePrefix("Chat: ").removePrefix("Chat ")
                        contenedor.gestorLlamadas.iniciarLlamadaVideo(
                            com.renea.arete_social.domain.model.ContactoLlamada(contactoId, nombre, null)
                        )
                        onAbrir(PantallaSecundaria.LlamadaVideo(contactoId, nombre, null))
                    },
                    onVerPerfil = { usuarioId ->
                        onAbrir(PantallaSecundaria.VerPerfil(usuarioId))
                    },
                    onVerComunidad = { comId ->
                        onAbrir(PantallaSecundaria.VerComunidad(comId))
                    },
                    onVerEvento = { evId ->
                        onAbrir(PantallaSecundaria.VerEvento(evId))
                    },
                    listaComunidades = (comState as? com.renea.arete_social.feature.comunidades.ComunidadesUiState.Listo)?.comunidades ?: emptyList(),
                    personasCercanas = personasCercanas,
                    listaAmigos = listaAmigos,
                    salas = salas,
                    regionDestacadaChatId = regionDestacadaChatId,
                    visibilidadPorChat = visibilidadPorChat
                )
            }
        }

        is PantallaSecundaria.LlamadaVideo -> {
            com.renea.arete_social.gui.llamadas.LlamadaVideoScreen(
                gestorLlamadas = contenedor.gestorLlamadas,
                nombreContacto = pantalla.nombre,
                fotoContacto = pantalla.foto,
                onCerrar = onAtras
            )
        }

        is PantallaSecundaria.Habitacion -> {
            com.renea.arete_social.gui.tamagotchi.PetScreen(
                repository = contenedor.petRepository,
                ownerId = pantalla.ownerId,
                ownerNombre = pantalla.ownerNombre ?: "Usuario",
                viewerId = usuarioIdActual,
                detectorProximidad = contenedor.detectorProximidad,
                repositorioEventos = contenedor.repositorioEventos,
                repositorioLibreria = contenedor.libreria,
                onAtras = onAtras
            )
        }

        is PantallaSecundaria.GestionarIdentidad -> {
            val identityVm: com.renea.arete_social.feature.identidad.IdentityViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { com.renea.arete_social.feature.identidad.IdentityViewModel(contenedor.identityManager) }
                }
            )
            com.renea.arete_social.gui.identidad.PantallaIdentidad(
                viewModel = identityVm,
                onAtras = onAtras
            )
        }
    }
}

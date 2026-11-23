package com.renea.arete_social.feature.mensajeria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.repository.ID_SALA_GENERAL
import com.renea.arete_social.domain.model.ElementoVisible
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.domain.model.VisionRegional
import com.renea.arete_social.domain.repository.RepositorioComunidades
import com.renea.arete_social.domain.repository.RepositorioSalas
import com.renea.arete_social.feature.descubrimiento.ServicioDescubrimiento
import com.renea.arete_social.feature.descubrimiento.ServicioRegionActual
import com.renea.arete_social.feature.descubrimiento.ServicioVisionRegional
import com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel que gestiona las salas de mensajería (Fase 5B.2).
 *
 * Garantiza:
 * 1. Exactamente UN chat general ("Chat Aréte"), deduplicando copias
 *    remotas accidentales o duplicados en SQL.
 * 2. Todos los chats regionales (ciudades principales); el de la región más cercana se destaca.
 * 3. Inclusión dinámica de los chats de las comunidades a las que pertenece el usuario.
 * 4. Los chats de los lugares y eventos que se ven en el mapa (cerca, recientes, por pase o por
 *    el Modo Súper), solo mientras sean visibles.
 * 5. Dedupe defensivo por `chatId` para evitar duplicidad de canales en la UI.
 *
 * Los servicios de descubrimiento son opcionales: sin ellos la lista es la de siempre
 * (general + comunidades + salas remotas).
 */
class SalasViewModel(
    private val repositorioSalas: RepositorioSalas,
    private val usuarioIdActual: String,
    private val repositorioComunidades: RepositorioComunidades? = null,
    private val servicioDescubrimiento: ServicioDescubrimiento? = null,
    private val servicioRegionActual: ServicioRegionActual? = null,
    private val servicioModoSuper: ServicioModoSuper? = null,
    private val servicioVisionRegional: ServicioVisionRegional? = null
) : ViewModel() {

    private val _estado = MutableStateFlow<SalasUiState>(SalasUiState.Cargando)
    val estado: StateFlow<SalasUiState> = _estado.asStateFlow()

    init {
        iniciar()
    }

    /** Fuerza una recarga de las salas (llamado desde pull-to-refresh). */
    fun recargar() {
        _estado.value = SalasUiState.Cargando
        iniciar()
    }

    private fun iniciar() {
        viewModelScope.launch {
            // Las comunidades se piden al servidor solo cuando cambia la lista de salas, no cada vez que
            // cambia la región o lo descubierto (esos cambios son frecuentes y no necesitan red).
            val salasYComunidades: Flow<Pair<List<Sala>, List<Sala>>> = repositorioSalas.observarSalas()
                .map { remotas -> remotas to runCatching { cargarSalasDeMisComunidades() }.getOrDefault(emptyList()) }

            val visibles: Flow<List<ElementoVisible>> = servicioDescubrimiento?.visibles ?: flowOf(emptyList())
            val region: Flow<ServicioRegionActual.Estado?> = servicioRegionActual?.estado ?: flowOf(null)
            val vision: Flow<VisionRegional?> = servicioVisionRegional?.vision ?: flowOf(null)
            val modoSuper: Flow<ServicioModoSuper.Estado?> = servicioModoSuper?.estado ?: flowOf(null)

            combine(salasYComunidades, visibles, region, vision, modoSuper) { (remotas, comunidades), vis, reg, vision, sup ->
                val regionales = if (reg != null) SalasZonales.salasRegionales(reg.regiones, reg.region) else emptyList()
                val zona = SalasZonales.salasDeElementos(vis, vision, sup?.venceEn)
                val unificadas = unificarYFiltrarSalas(remotas, comunidades, regionales, zona.salas)
                Resumen(
                    salas = unificadas,
                    general = unificadas.firstOrNull { it.tipo == TipoSala.GENERAL || it.chatId == ID_CHAT_GENERAL },
                    regionDestacadaChatId = reg?.region?.chatId,
                    visibilidadPorChat = zona.visibilidadPorChat,
                    modoSuperActivo = sup?.activo == true
                )
            }
                .catch { error ->
                    _estado.value = SalasUiState.Error(error.message ?: "Error al cargar salas")
                }
                .collect { r ->
                    val actual = _estado.value
                    val base = actual as? SalasUiState.Listo ?: SalasUiState.Listo()
                    _estado.value = base.copy(
                        salas = r.salas,
                        salaGeneral = r.general,
                        regionDestacadaChatId = r.regionDestacadaChatId,
                        visibilidadPorChat = r.visibilidadPorChat,
                        modoSuperActivo = r.modoSuperActivo
                    )
                }
        }
    }

    private data class Resumen(
        val salas: List<Sala>,
        val general: Sala?,
        val regionDestacadaChatId: String?,
        val visibilidadPorChat: Map<String, com.renea.arete_social.domain.model.VisibilidadMapa>,
        val modoSuperActivo: Boolean
    )

    private suspend fun cargarSalasDeMisComunidades(): List<Sala> {
        val repoCom = repositorioComunidades ?: return emptyList()
        val todas = repoCom.listarComunidades()
        return todas.filter { comunidad ->
            repoCom.esMiembro(comunidad.id, usuarioIdActual)
        }.map { c ->
            Sala(
                id = "sala-comunidad-${c.id}",
                nombre = "# ${c.nombre}",
                descripcion = c.descripcion.ifBlank { "Canal de la comunidad" },
                chatId = c.chatId,
                creadorId = "comunidad",
                esPublica = true,
                clave = null,
                tipo = TipoSala.COMUNIDAD,
                entidadId = c.id,
                fechaCreacion = 0L
            )
        }
    }

    /**
     * Dedupe defensivo y ordenamiento (§5B.2):
     * 1. La Sala General oficial (ID_CHAT_GENERAL, «Chat Aréte») queda en el primer puesto.
     * 2. Se descarta cualquier sala remota que duplique el nombre o chatId del general.
     * 3. Siguen los chats regionales (el de la región más cercana, primero).
     * 4. Se incorporan las salas de comunidad del usuario.
     * 5. Luego los chats de los lugares y eventos que se ven ahora ([zona]).
     * 6. Deduplicación por `chatId`.
     */
    fun unificarYFiltrarSalas(
        remotas: List<Sala>,
        comunidades: List<Sala>,
        regionales: List<Sala> = emptyList(),
        zona: List<Sala> = emptyList()
    ): List<Sala> {
        val salaGeneralOficial = Sala(
            id = ID_SALA_GENERAL,
            nombre = NOMBRE_CHAT_GENERAL,
            descripcion = DESCRIPCION_CHAT_GENERAL,
            chatId = ID_CHAT_GENERAL,
            creadorId = "sistema",
            esPublica = true,
            clave = null,
            tipo = TipoSala.GENERAL,
            entidadId = null,
            fechaCreacion = 0L
        )

        // Filtrar cualquier duplicado del chat general en la lista remota
        val otrasRemotas = remotas.filter {
            it.id != ID_SALA_GENERAL &&
            it.chatId != ID_CHAT_GENERAL &&
            it.tipo != TipoSala.GENERAL &&
            !it.nombre.equals("General", ignoreCase = true) &&
            !it.nombre.equals("Sala General", ignoreCase = true)
        }

        val todas = (listOf(salaGeneralOficial) + regionales + comunidades + zona + otrasRemotas)
            .distinctBy { it.chatId }

        return todas
    }

    fun crearSala(
        nombre: String,
        descripcion: String,
        esPublica: Boolean,
        clave: String?,
        onCreada: (Sala) -> Unit
    ) {
        if (nombre.isBlank()) return
        val actual = _estado.value
        if (actual is SalasUiState.Listo) {
            _estado.value = actual.copy(creando = true)
        }

        viewModelScope.launch {
            runCatching {
                repositorioSalas.crearSala(
                    nombre = nombre.trim(),
                    descripcion = descripcion.trim(),
                    creadorId = usuarioIdActual,
                    esPublica = esPublica,
                    clave = if (esPublica) null else clave?.trim()
                )
            }.onSuccess { nuevaSala ->
                val actualPost = _estado.value
                if (actualPost is SalasUiState.Listo) {
                    _estado.value = actualPost.copy(creando = false)
                }
                onCreada(nuevaSala)
            }.onFailure { err ->
                val actualPost = _estado.value
                if (actualPost is SalasUiState.Listo) {
                    _estado.value = actualPost.copy(creando = false, mensajeError = err.message ?: "Error al crear sala")
                }
            }
        }
    }

    suspend fun validarClave(sala: Sala, claveIngresada: String): Boolean =
        if (sala.esPublica) true else repositorioSalas.unirseASala(sala.id, claveIngresada.trim())

    companion object {
        /** Nombre visible del chat general (antes «Chat General (500m)»). Los ids de chat no cambian. */
        const val NOMBRE_CHAT_GENERAL = "Chat Aréte"
        const val DESCRIPCION_CHAT_GENERAL = "El chat general: abierto a todos"
    }
}

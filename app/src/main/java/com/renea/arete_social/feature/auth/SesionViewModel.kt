package com.renea.arete_social.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renea.arete_social.domain.crypto.CifradorE2ee
import com.renea.arete_social.domain.repository.RepositorioAutenticacion
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Orquesta registro/login/invitado/logout usando solo RepositorioAutenticacion
 * y asegura la identidad criptográfica y claves para Cifrado E2EE.
 *
 * También recuerda, vía [GestorSesionLocal], cuál fue el último usuario con
 * sesión iniciada en este dispositivo — así, si se abre la app sin conexión,
 * entra directo con ese usuario en vez de mandar a Login. Si nunca se había abierto
 * la app antes en este dispositivo, se inicia como usuario local/invitado automáticamente.
 */
class SesionViewModel(
    private val repositorioAutenticacion: RepositorioAutenticacion? = null,
    private val repositorioUsuarios: RepositorioUsuarios? = null,
    private val cifradorE2ee: CifradorE2ee? = null,
    private val gestorIdentidad: com.renea.arete_social.data.crypto.GestorIdentidadEcdsa? = null
) : ViewModel() {


    private val _estado = MutableStateFlow(estadoInicial())
    val estado: StateFlow<SesionUiState> = _estado.asStateFlow()

    init {
        val actual = _estado.value
        if (actual is SesionUiState.Autenticado) {
            asegurarClaveE2ee(actual.usuarioId)
        }
    }

    /**
     * Estado inicial descentralizado: genera o carga la identidad criptográfica
     * local sin depender de servidores externos ni requerir inicio de sesión centralizado.
     */
    private fun estadoInicial(): SesionUiState {
        val ultimoLocal = GestorSesionLocal.obtenerUltimoUsuario()
        if (ultimoLocal != null) {
            val esLocal = ultimoLocal.startsWith("invitado-local-") || ultimoLocal.startsWith("usuario-local-")
            return SesionUiState.Autenticado(ultimoLocal, soloLocal = esLocal, esInvitado = false)
        }
        val nuevoLocal = "invitado-local-${java.util.UUID.randomUUID()}"
        GestorSesionLocal.guardarUltimoUsuario(nuevoLocal)
        GestorSesionLocal.guardarUltimoNombre("Invitado")
        return SesionUiState.Autenticado(nuevoLocal, soloLocal = true, esInvitado = false)
    }

    private val _avisoClaveRegenerada = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val avisoClaveRegenerada: kotlinx.coroutines.flow.SharedFlow<String> = _avisoClaveRegenerada.asSharedFlow()

    fun esSoloLocal(): Boolean = (_estado.value as? SesionUiState.Autenticado)?.soloLocal == true

    private fun asegurarClaveE2ee(usuarioId: String) {
        val repoUsuarios = repositorioUsuarios ?: return
        viewModelScope.launch {
            try {
                if (cifradorE2ee != null) {
                    val pubLocal = cifradorE2ee.obtenerClavePublicaPropia(usuarioId)
                    val usuarioRemoto = repoUsuarios.obtenerPorId(usuarioId)
                    if (usuarioRemoto != null && usuarioRemoto.clavePublicaE2ee != null && usuarioRemoto.clavePublicaE2ee != pubLocal) {
                        _avisoClaveRegenerada.emit("Identidad restaurada en este dispositivo: las claves previas no coincidían y fueron regeneradas.")
                    }
                    if (usuarioRemoto?.clavePublicaE2ee != pubLocal) {
                        repoUsuarios.actualizarClavePublicaE2ee(usuarioId, pubLocal)
                    }
                    cifradorE2ee.guardarClavePublicaConocida(usuarioId, pubLocal, "verificado")
                }
                if (gestorIdentidad != null) {
                    gestorIdentidad.cambiarUsuario(usuarioId)
                    val pubFirma = gestorIdentidad.obtenerClavePublicaBase64()
                    if (pubFirma != null) {
                        repoUsuarios.actualizarClavePublicaFirma(usuarioId, pubFirma)
                    }
                }
            } catch (e: Exception) {
                // Captura fallos de Keystore desincronizado por restauración de backup
                _avisoClaveRegenerada.emit("Se detectó un cambio en el almacén de seguridad del dispositivo. Claves regeneradas.")
            }
        }
    }

    /** Quita el aviso de error al empezar a corregir los datos. */
    fun limpiarError() {
        if (_estado.value is SesionUiState.Error) _estado.value = SesionUiState.Inactivo
    }

    fun registrar(usuario: String, password: String) {
        MensajesAuth.validar(usuario, password, esRegistro = true)?.let {
            _estado.value = SesionUiState.Error(it)
            return
        }
        _estado.value = SesionUiState.Cargando
        viewModelScope.launch {
            val repo = repositorioAutenticacion
            val resultado = if (repo != null) {
                runCatching { repo.registrar(usuario, password) }
            } else {
                runCatching {
                    val nuevoId = "usuario-local-${java.util.UUID.randomUUID()}"
                    nuevoId
                }
            }
            resultado
                .onSuccess { usuarioId ->
                    GestorSesionLocal.guardarUltimoUsuario(usuarioId)
                    GestorSesionLocal.guardarUltimoNombre(usuario)
                    _estado.value = SesionUiState.Autenticado(usuarioId)
                    asegurarClaveE2ee(usuarioId)
                }
                .onFailure { error ->
                    _estado.value = SesionUiState.Error(MensajesAuth.traducirError(error, esRegistro = true))
                }
        }
    }

    fun iniciarSesion(usuario: String, password: String) {
        MensajesAuth.validar(usuario, password, esRegistro = false)?.let {
            _estado.value = SesionUiState.Error(it)
            return
        }
        _estado.value = SesionUiState.Cargando
        viewModelScope.launch {
            val repo = repositorioAutenticacion
            val resultado = if (repo != null) {
                runCatching { repo.iniciarSesion(usuario, password) }
            } else {
                runCatching {
                    val idGuardado = GestorSesionLocal.obtenerUltimoUsuario() ?: "usuario-local-${java.util.UUID.randomUUID()}"
                    idGuardado
                }
            }
            resultado
                .onSuccess { usuarioId ->
                    GestorSesionLocal.guardarUltimoUsuario(usuarioId)
                    GestorSesionLocal.guardarUltimoNombre(usuario)
                    _estado.value = SesionUiState.Autenticado(usuarioId)
                    asegurarClaveE2ee(usuarioId)
                }
                .onFailure { error ->
                    _estado.value = SesionUiState.Error(MensajesAuth.traducirError(error, esRegistro = false))
                }
        }
    }

    fun entrarComoInvitado() {
        _estado.value = SesionUiState.Cargando
        viewModelScope.launch {
            val repo = repositorioAutenticacion
            val resultado = if (repo != null) {
                runCatching { repo.iniciarComoInvitado() }
            } else {
                Result.failure(Exception("Modo local-first"))
            }
            if (resultado.isSuccess) {
                val usuarioId = resultado.getOrThrow()
                GestorSesionLocal.guardarUltimoUsuario(usuarioId)
                GestorSesionLocal.guardarUltimoNombre("Invitado")
                _estado.value = SesionUiState.Autenticado(usuarioId, soloLocal = false, esInvitado = true)
                asegurarClaveE2ee(usuarioId)
            } else {
                // Sin conexión a internet o modo local-first: generar usuarioId local sintético para invitado offline
                val idLocal = GestorSesionLocal.obtenerUltimoUsuario() ?: "invitado-local-${java.util.UUID.randomUUID()}"
                GestorSesionLocal.guardarUltimoUsuario(idLocal)
                GestorSesionLocal.guardarUltimoNombre("Invitado")
                _estado.value = SesionUiState.Autenticado(idLocal, soloLocal = true, esInvitado = false)
                asegurarClaveE2ee(idLocal)

                // Intentar migración en segundo plano al recuperar red solo si hay repositorio configurado
                if (repo != null) {
                    viewModelScope.launch {
                        while (_estado.value is SesionUiState.Autenticado && (_estado.value as SesionUiState.Autenticado).soloLocal) {
                            kotlinx.coroutines.delay(10_000)
                            val resSupa = runCatching { repo?.iniciarComoInvitado() }.getOrNull()
                            if (resSupa != null) {
                                GestorSesionLocal.guardarUltimoUsuario(resSupa)
                                _estado.value = SesionUiState.Autenticado(resSupa, soloLocal = false, esInvitado = true)
                                asegurarClaveE2ee(resSupa)
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    fun cerrarSesion(
        gestorOutboxLocal: com.renea.arete_social.data.local.outbox.GestorOutboxLocal? = null,
        cacheUsuariosLocal: com.renea.arete_social.data.local.perfil.CacheUsuariosLocal? = null
    ) {
        viewModelScope.launch {
            repositorioAutenticacion?.let { runCatching { it.cerrarSesion() } }
            GestorSesionLocal.limpiar()
            GestorAmistades.limpiar()
            GestorPerfilEfemero.limpiar()
            GestorPerfilDatosTemporales.limpiar()
            cacheUsuariosLocal?.limpiar()
            gestorOutboxLocal?.purgarTodo()
            _estado.value = SesionUiState.Inactivo
        }
    }

    /**
     * Elimina permanentemente la cuenta y todos los datos del usuario localmente.
     */
    fun eliminarCuenta(
        contexto: android.content.Context? = null,
        gestorOutboxLocal: com.renea.arete_social.data.local.outbox.GestorOutboxLocal? = null,
        cacheUsuariosLocal: com.renea.arete_social.data.local.perfil.CacheUsuariosLocal? = null
    ) {
        viewModelScope.launch {
            _estado.value = SesionUiState.Cargando
            repositorioAutenticacion?.let { runCatching { it.eliminarCuenta() } }

            GestorSesionLocal.limpiar()
            GestorAmistades.limpiar()
            GestorPerfilEfemero.limpiar()
            GestorPerfilDatosTemporales.limpiar()
            cacheUsuariosLocal?.limpiar()
            gestorOutboxLocal?.purgarTodo()
            contexto?.let { ctx ->
                runCatching {
                    val sharedPrefsDir = java.io.File(ctx.applicationInfo.dataDir, "shared_prefs")
                    if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                        sharedPrefsDir.listFiles()?.forEach { archivo ->
                            archivo.delete()
                        }
                    }
                    ctx.cacheDir.deleteRecursively()
                }
            }
            _estado.value = SesionUiState.Inactivo
        }
    }
}
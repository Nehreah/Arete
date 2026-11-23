package com.renea.arete_social.data.local.auth

import com.renea.arete_social.domain.repository.RepositorioAutenticacion
import com.renea.arete_social.feature.auth.GestorSesionLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class RepositorioAutenticacionLocal : RepositorioAutenticacion {

    private val _sesion = MutableStateFlow(GestorSesionLocal.obtenerUltimoUsuario())

    override suspend fun registrar(usuario: String, password: String): String {
        val id = "usuario-local-${UUID.randomUUID()}"
        GestorSesionLocal.guardarUltimoUsuario(id)
        GestorSesionLocal.guardarUltimoNombre(usuario)
        _sesion.value = id
        return id
    }

    override suspend fun iniciarSesion(usuario: String, password: String): String {
        val id = GestorSesionLocal.obtenerUltimoUsuario() ?: "usuario-local-${UUID.randomUUID()}"
        GestorSesionLocal.guardarUltimoUsuario(id)
        GestorSesionLocal.guardarUltimoNombre(usuario)
        _sesion.value = id
        return id
    }

    override suspend fun iniciarComoInvitado(): String {
        val id = "invitado-local-${UUID.randomUUID()}"
        GestorSesionLocal.guardarUltimoUsuario(id)
        GestorSesionLocal.guardarUltimoNombre("Invitado")
        _sesion.value = id
        return id
    }

    override suspend fun cerrarSesion() {
        GestorSesionLocal.limpiar()
        _sesion.value = null
    }

    override suspend fun eliminarCuenta() {
        GestorSesionLocal.limpiar()
        _sesion.value = null
    }

    override fun usuarioIdActual(): String? = GestorSesionLocal.obtenerUltimoUsuario()

    override fun observarSesion(): Flow<String?> = _sesion.asStateFlow()
}

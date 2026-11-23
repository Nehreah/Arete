package com.renea.arete_social.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Autenticación de la app. Se mantiene separada de RepositorioUsuarios porque son
 * responsabilidades distintas: el perfil social (nombre, foto, visibilidad,
 * ubicación) vive en Usuario/RepositorioUsuarios; aquí solo se resuelve
 * "quién es el usuario autenticado ahora mismo".
 *
 * De cara a la persona, solo existe "usuario + contraseña" (o invitado) — nunca se
 * pide ni se muestra un correo.
 */
interface RepositorioAutenticacion {
    suspend fun registrar(usuario: String, password: String): String
    suspend fun iniciarSesion(usuario: String, password: String): String
    /** Sesión sin usuario ni contraseña (identidad de invitado). Sigue
     * siendo un usuarioId real y válido para todo lo demás de la app. */
    suspend fun iniciarComoInvitado(): String
    suspend fun cerrarSesion()
    /** Elimina permanentemente la cuenta del usuario actual y todos sus datos asociados. */
    suspend fun eliminarCuenta()
    fun usuarioIdActual(): String?
    fun observarSesion(): Flow<String?>
}

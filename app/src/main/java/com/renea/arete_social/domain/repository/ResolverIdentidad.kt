package com.renea.arete_social.domain.repository

/**
 * Servicio unificado de resolución de identidad para Aréte.
 * Traduce de forma bidireccional entre el ID de sesión del usuario local
 * y la clave pública criptográfica IK (ECDSA P-256 en formato hex sin comprimir).
 */
interface ResolverIdentidad {
    /** Devuelve el ID de sesión local actual si existe. */
    fun miUsuarioId(): String?

    /** Devuelve la clave pública IK local en formato hexadecimal (130 caracteres). */
    fun miIkHex(): String

    /** Comprueba si un identificador dado (sea ID de sesión o clave IK hex) pertenece a este dispositivo. */
    fun esMio(identificador: String?): Boolean

    /** Traduce un ID de sesión a su respectiva clave IK hex conocida. */
    fun ikDe(usuarioId: String): String?

    /** Traduce una clave IK hex al respectivo ID de sesión conocido. */
    fun usuarioDe(ikHex: String): String?

    /** Registra la asociación entre un ID de sesión y su clave IK hex. */
    fun registrarVinculo(usuarioId: String, ikHex: String)
}

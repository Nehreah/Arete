package com.renea.arete_social.feature.personajes.data

import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot

/**
 * Contrato para el repositorio remoto de personajes sociales.
 *
 * Este contrato permite inyectar implementaciones remotas o fakes
 * en los tests JVM de sincronización de personajes.
 */
interface RepositorioPersonajesRemoto {

    /**
     * Envía o actualiza la [relacion] local al servidor.
     * Solo se ejecuta si existe amistad ACEPTADA y sin bloqueo.
     */
    suspend fun sincronizarRelacion(relacion: Relacion): Result<Unit>

    /**
     * Registra un [recuerdo] en el servidor.
     * El recuerdo DEBE tener [Recuerdo.firmaMutua] no nula; si está vacía se devuelve error.
     */
    suspend fun sincronizarRecuerdo(recuerdo: Recuerdo): Result<Unit>

    /**
     * Publica el [snapshot] de casa del usuario al servidor.
     * La firma ECDSA del propietario es obligatoria.
     */
    suspend fun sincronizarCasaSnapshot(snapshot: CasaSnapshot): Result<Unit>

    /**
     * Descarga la casa publicada por [amigoOwnerId] si es amigo confirmado.
     * Devuelve `null` si no existe o no tiene acceso.
     */
    suspend fun obtenerCasaDeAmigo(amigoOwnerId: String): Result<CasaSnapshot?>

    /**
     * Descarga todas las relaciones del usuario autenticado almacenadas en el servidor.
     */
    suspend fun obtenerRelaciones(): Result<List<Relacion>>

    /**
     * Descarga todos los recuerdos en los que participa el usuario autenticado.
     */
    suspend fun obtenerRecuerdos(): Result<List<Recuerdo>>
}

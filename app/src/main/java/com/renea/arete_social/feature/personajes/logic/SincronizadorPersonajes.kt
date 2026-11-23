package com.renea.arete_social.feature.personajes.logic

import com.renea.arete_social.feature.personajes.data.AjustesPersonajesSociales
import com.renea.arete_social.feature.personajes.data.RepositorioCasa
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajesRemoto
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import kotlinx.coroutines.delay

/**
 * Resultado de una operación de sincronización.
 */
data class ResultadoSincronizacion(
    val relacionesEnviadas: Int = 0,
    val recuerdosEnviados: Int = 0,
    val casaEnviada: Boolean = false,
    val relacionesRestauradas: Int = 0,
    val recuerdosRestaurados: Int = 0,
    val errores: List<String> = emptyList()
)

/**
 * Orquesta la sincronización bajo demanda entre el repositorio local y el repositorio remoto.
 *
 * Principios:
 * - Local es siempre autoritativo; restaurar desde remoto nunca borra datos locales.
 * - Solo sincroniza si [esHabilitado] devuelve `true`.
 * - Escrituras con reintento exponencial (máx 3 intentos) ante errores de red.
 * - Recuerdos sin [Recuerdo.firmaMutua] se omiten silenciosamente.
 * - Snapshots de casa sin [CasaSnapshot.firmaPropietario] se omiten silenciosamente.
 *
 * @param esHabilitado  Lambda que indica si la funcionalidad social está activa.
 *                      En producción: `{ AjustesPersonajesSociales.estaHabilitado() }`.
 *                      En tests: `{ true }` o `{ false }`.
 * @param propietarioId ID del usuario dueño de la mascota (para localizar su casa).
 * @param personajeId   ID del personaje activo.
 */
class SincronizadorPersonajes(
    private val esHabilitado: () -> Boolean = { AjustesPersonajesSociales.estaHabilitado() },
    private val repoLocal: RepositorioPersonajes,
    private val repoCasa: RepositorioCasa,
    private val repoRemoto: RepositorioPersonajesRemoto,
    private val propietarioId: String = "",
    private val personajeId: String = ""
) {


    companion object {
        private const val MAX_REINTENTOS = 3
        private const val DELAY_BASE_MS = 500L
    }

    /**
     * Envía los datos locales (relaciones, recuerdos y snapshot de casa) al servidor.
     *
     * @return [ResultadoSincronizacion] con un resumen de lo procesado.
     */
    suspend fun sincronizarTodo(): ResultadoSincronizacion {
        if (!esHabilitado()) return ResultadoSincronizacion()

        val errores = mutableListOf<String>()
        var relacionesEnviadas = 0
        var recuerdosEnviados = 0
        var casaEnviada = false

        // 1. Enviar relaciones
        for (relacion in repoLocal.obtenerRelaciones()) {
            val exito = reintentarConBackoff(MAX_REINTENTOS) {
                repoRemoto.sincronizarRelacion(relacion)
            }
            if (exito) relacionesEnviadas++ else errores += "relacion:${relacion.otroOwnerId}"
        }

        // 2. Enviar recuerdos (solo los que tienen firma mutua)
        for (recuerdo in repoLocal.obtenerRecuerdos()) {
            if (recuerdo.firmaMutua.isNullOrBlank()) continue
            val exito = reintentarConBackoff(MAX_REINTENTOS) {
                repoRemoto.sincronizarRecuerdo(recuerdo)
            }
            if (exito) recuerdosEnviados++ else errores += "recuerdo:${recuerdo.id}"
        }

        // 3. Enviar snapshot de casa (solo si se proporcionaron IDs de mascota activa)
        if (propietarioId.isNotBlank() && personajeId.isNotBlank()) {
            val snapshot = repoCasa.obtenerCasa(propietarioId, personajeId)
            if (!snapshot.firmaPropietario.isNullOrBlank()) {
                val exito = reintentarConBackoff(MAX_REINTENTOS) {
                    repoRemoto.sincronizarCasaSnapshot(snapshot)
                }
                if (exito) casaEnviada = true else errores += "casa:${snapshot.propietarioId}"
            }
        }

        return ResultadoSincronizacion(
            relacionesEnviadas = relacionesEnviadas,
            recuerdosEnviados  = recuerdosEnviados,
            casaEnviada        = casaEnviada,
            errores            = errores
        )
    }

    /**
     * Descarga relaciones y recuerdos del servidor y los fusiona con el repositorio local.
     * **Nunca borra datos locales**: si un recuerdo ya existe localmente, se conserva el local.
     *
     * @return [ResultadoSincronizacion] con cuántos registros nuevos se restauraron.
     */
    suspend fun restaurarDesdeRemoto(): ResultadoSincronizacion {
        if (!esHabilitado()) return ResultadoSincronizacion()

        val errores = mutableListOf<String>()
        var relacionesRestauradas = 0
        var recuerdosRestaurados = 0

        // Restaurar relaciones
        repoRemoto.obtenerRelaciones()
            .onSuccess { remotas ->
                val localesIds = repoLocal.obtenerRelaciones().map { it.otroOwnerId }.toSet()
                for (relacion in remotas) {
                    if (relacion.otroOwnerId !in localesIds) {
                        repoLocal.guardarRelacion(relacion)
                        relacionesRestauradas++
                    }
                }
            }
            .onFailure { errores += "restaurar_relaciones:${it.message}" }

        // Restaurar recuerdos
        repoRemoto.obtenerRecuerdos()
            .onSuccess { remotos ->
                val localesIds = repoLocal.obtenerRecuerdos().map { it.id }.toSet()
                for (recuerdo in remotos) {
                    if (recuerdo.id !in localesIds) {
                        repoLocal.guardarRecuerdo(recuerdo)
                        recuerdosRestaurados++
                    }
                }
            }
            .onFailure { errores += "restaurar_recuerdos:${it.message}" }

        return ResultadoSincronizacion(
            relacionesRestauradas = relacionesRestauradas,
            recuerdosRestaurados  = recuerdosRestaurados,
            errores               = errores
        )
    }

    /**
     * Descarga la casa publicada por un amigo.
     * Solo tiene acceso si existe amistad ACEPTADA (el RLS lo garantiza en servidor).
     */
    suspend fun obtenerCasaDeAmigo(amigoOwnerId: String): CasaSnapshot? {
        if (!esHabilitado()) return null
        return repoRemoto.obtenerCasaDeAmigo(amigoOwnerId).getOrNull()
    }

    // ── Helper: exponential backoff ───────────────────────────────────────────

    /**
     * Ejecuta [bloque] hasta [maxIntentos] veces con espera exponencial entre reintentos.
     * @return `true` si el bloque tuvo éxito en algún intento, `false` si todos fallaron.
     */
    private suspend fun reintentarConBackoff(maxIntentos: Int, bloque: suspend () -> Result<Unit>): Boolean {
        repeat(maxIntentos) { intento ->
            if (bloque().isSuccess) return true
            if (intento < maxIntentos - 1) delay(DELAY_BASE_MS * (1L shl intento)) // 500ms, 1s, 2s
        }
        return false
    }
}

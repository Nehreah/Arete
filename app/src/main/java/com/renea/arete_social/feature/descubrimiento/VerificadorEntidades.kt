package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.repository.RepositorioEventos
import com.renea.arete_social.domain.repository.RepositorioLugares
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.delay

/**
 * Comprueba que un lugar o evento recibido por pase de verdad existe. La firma del pase prueba QUIÉN
 * lo envía, no que el lugar sea real; sin esta comprobación alguien podría inventar un pase que haga
 * seguir un chat arbitrario.
 */
fun interface VerificadorEntidades {
    /** true = existe y coincide; false = no existe o no coincide; null = no se pudo saber (sin internet). */
    suspend fun existe(snapshot: EntidadSnapshot): Boolean?
}

/** Verifica contra el servidor (los mismos repositorios que usa el mapa). */
class VerificadorEntidadesRemoto(
    private val repositorioLugares: RepositorioLugares,
    private val repositorioEventos: RepositorioEventos,
    private val hayInternet: () -> Boolean,
    private val toleranciaMetros: Double = 100.0,
    private val esperaEntreIntentosMs: Long = 1_500L
) : VerificadorEntidades {

    override suspend fun existe(snapshot: EntidadSnapshot): Boolean? {
        if (!hayInternet()) return null
        // Dos intentos: los repositorios devuelven null tanto si no existe como si falló la red un instante.
        repeat(2) { intento ->
            val coincide = consultar(snapshot)
            if (coincide != null) return coincide
            if (intento == 0) delay(esperaEntreIntentosMs)
        }
        return false
    }

    private suspend fun consultar(s: EntidadSnapshot): Boolean? {
        val punto = Ubicacion(s.latitud, s.longitud, 0L)
        return when (s.tipo) {
            TipoEntidadMapa.LUGAR -> runCatching { repositorioLugares.obtenerPorId(s.id) }.getOrNull()?.let {
                it.chatId == s.chatId && distanciaMetros(it.ubicacion, punto) <= toleranciaMetros
            }
            TipoEntidadMapa.EVENTO -> runCatching { repositorioEventos.obtenerPorId(s.id) }.getOrNull()?.let {
                it.chatId == s.chatId && distanciaMetros(it.ubicacion, punto) <= toleranciaMetros
            }
        }
    }
}

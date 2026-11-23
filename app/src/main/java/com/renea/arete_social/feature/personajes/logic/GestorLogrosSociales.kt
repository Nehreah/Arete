package com.renea.arete_social.feature.personajes.logic

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.model.NivelVinculo
import com.renea.arete_social.feature.personajes.model.TipoRecuerdo
import com.renea.arete_social.feature.personajes.model.logros.LogroSocial
import com.renea.arete_social.feature.personajes.model.logros.TipoLogroSocial

/**
 * Gestor y evaluador de logros sociales para las mascotas.
 */
class GestorLogrosSociales(
    private val context: Context? = null,
    private val prefsOverride: SharedPreferences? = null,
    private val repoPersonajes: RepositorioPersonajes
) {
    private val prefs: SharedPreferences? by lazy {
        prefsOverride ?: context?.getSharedPreferences("antired_logros_sociales", Context.MODE_PRIVATE)
    }

    /**
     * Evalúa el estado de todos los logros sociales a partir de las relaciones y recuerdos locales.
     * Si detecta nuevos logros alcanzados, los persiste automáticamente.
     */
    fun evaluarLogros(): List<LogroSocial> {
        val relaciones = repoPersonajes.obtenerRelaciones()
        val recuerdos = repoPersonajes.obtenerRecuerdos()

        val ahora = System.currentTimeMillis()

        return TipoLogroSocial.entries.map { tipo ->
            val yaDesbloqueado = prefs?.getBoolean("logro_${tipo.name}", false) ?: false
            val fechaGuardada = prefs?.getLong("fecha_${tipo.name}", 0L)?.takeIf { it > 0L }

            val cumpleRequisito = when (tipo) {
                TipoLogroSocial.PRIMER_VINCULO -> relaciones.isNotEmpty()
                TipoLogroSocial.VISITANTE_ACTIVO -> recuerdos.any { it.tipo == TipoRecuerdo.VISITA_CASA }
                TipoLogroSocial.VINCULO_INSEPARABLE -> relaciones.any { it.nivelVinculo == NivelVinculo.INSEPARABLE || it.confianza >= 90 }
                TipoLogroSocial.COLECCIONISTA_RECUERDOS -> recuerdos.size >= 5
                TipoLogroSocial.DEPORTISTA_FIT -> recuerdos.count { it.tipo == TipoRecuerdo.ENTRENAMIENTO_GIMNASIO } >= 1 ||
                        relaciones.any { it.actividadFavorita?.contains("Gimnasio", ignoreCase = true) == true }
                TipoLogroSocial.ANFITRION_POPULAR -> {
                    val visitantes = recuerdos.filter { it.tipo == TipoRecuerdo.VISITA_CASA }.map { it.conQuienId }.distinct()
                    visitantes.size >= 2
                }
            }

            val estaDesbloqueado = yaDesbloqueado || cumpleRequisito
            val fecha = if (yaDesbloqueado) fechaGuardada ?: ahora else if (cumpleRequisito) ahora else null

            if (cumpleRequisito && !yaDesbloqueado) {
                prefs?.edit()
                    ?.putBoolean("logro_${tipo.name}", true)
                    ?.putLong("fecha_${tipo.name}", ahora)
                    ?.apply()
            }

            LogroSocial(
                tipo = tipo,
                desbloqueado = estaDesbloqueado,
                fechaDesbloqueoMillis = fecha
            )
        }
    }
}

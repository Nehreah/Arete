package com.renea.arete_social.feature.personajes.data

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.feature.personajes.logic.MotorCasa
import com.renea.arete_social.feature.personajes.model.escena.CasaSnapshot
import com.renea.arete_social.feature.personajes.model.escena.ObjetoColocado
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Interfaz para la gestión y persistencia de la casa de un personaje.
 */
interface RepositorioCasa {
    fun obtenerCasa(propietarioId: String, personajeId: String): CasaSnapshot
    fun guardarCasa(casa: CasaSnapshot)
    fun casaPorDefecto(propietarioId: String, personajeId: String): CasaSnapshot
}

/**
 * Persistencia local de la casa en SharedPreferences (formato JSON serializado).
 * Admite [prefsOverride] para pruebas unitarias sin dependencias del framework de Android.
 */
class RepositorioCasaLocal(
    context: Context? = null,
    prefsOverride: SharedPreferences? = null,
    private val gestorIdentidad: GestorIdentidadEcdsa? = null
) : RepositorioCasa {

    private val prefs: SharedPreferences? =
        prefsOverride ?: context?.applicationContext?.getSharedPreferences("antired_casa_db", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun clave(propietarioId: String, personajeId: String) = "casa_${propietarioId}_$personajeId"

    override fun obtenerCasa(propietarioId: String, personajeId: String): CasaSnapshot {
        val p = prefs ?: return casaPorDefecto(propietarioId, personajeId)
        val raw = p.getString(clave(propietarioId, personajeId), null)
        if (raw.isNullOrBlank()) {
            val defecto = casaPorDefecto(propietarioId, personajeId)
            guardarCasa(defecto)
            return defecto
        }
        return runCatching { json.decodeFromString<CasaSnapshot>(raw) }
            .getOrElse {
                val defecto = casaPorDefecto(propietarioId, personajeId)
                guardarCasa(defecto)
                defecto
            }
    }

    override fun guardarCasa(casa: CasaSnapshot) {
        val p = prefs ?: return
        val firmada = MotorCasa.firmarSnapshot(casa, gestorIdentidad)
        p.edit().putString(clave(casa.propietarioId, casa.personajeId), json.encodeToString(firmada)).apply()
    }

    override fun casaPorDefecto(propietarioId: String, personajeId: String): CasaSnapshot {
        val objetosIniciales = listOf(
            ObjetoColocado(
                instanciaId = UUID.randomUUID().toString(),
                catalogoId = "cama_futurista",
                x = 1,
                y = 1
            ),
            ObjetoColocado(
                instanciaId = UUID.randomUUID().toString(),
                catalogoId = "comedero_automatico",
                x = 6,
                y = 1
            ),
            ObjetoColocado(
                instanciaId = UUID.randomUUID().toString(),
                catalogoId = "rascador_poste",
                x = 1,
                y = 5
            ),
            ObjetoColocado(
                instanciaId = UUID.randomUUID().toString(),
                catalogoId = "alfombra_pixel_red",
                x = 3,
                y = 3
            )
        )

        val inicial = CasaSnapshot(
            propietarioId = propietarioId,
            personajeId = personajeId,
            version = 1,
            temaId = "tema_pixel_013",
            objetos = objetosIniciales,
            timestamp = System.currentTimeMillis()
        )

        return MotorCasa.firmarSnapshot(inicial, gestorIdentidad)
    }
}

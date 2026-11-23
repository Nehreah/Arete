package com.renea.arete_social.feature.personajes.model

import kotlinx.serialization.Serializable

/**
 * Nivel de vínculo entre dos personajes según sus puntos de confianza acumulados.
 */
enum class NivelVinculo(val nivel: Int, val titulo: String, val confianzaMinima: Int) {
    CONOCIDO(1, "Conocido", 0),
    COMPANERO(2, "Compañero", 20),
    BUEN_AMIGO(3, "Buen Amigo", 45),
    MEJOR_AMIGO(4, "Mejor Amigo", 70),
    INSEPARABLE(5, "Inseparable", 90);

    companion object {
        fun desdeConfianza(confianza: Int): NivelVinculo {
            val c = confianza.coerceIn(0, 100)
            return values().findLast { c >= it.confianzaMinima } ?: CONOCIDO
        }
    }
}

/**
 * Relación persistente entre el personaje local y el de otra amistad confirmada.
 *
 * @param otroPersonajeId Identificador único del personaje amigo.
 * @param otroOwnerId ID del usuario propietario de la otra mascota.
 * @param nombreOtro Nombre actual del personaje amigo.
 * @param confianza Puntos de confianza (0..100).
 * @param interaccionesTotales Contador acumulativo de visitas y actividades juntos.
 * @param interaccionesHoy Interacciones registradas en el día en curso (máx [MAX_INTERACCIONES_DIARIAS]).
 * @param ultimoContactoMillis Marca de tiempo de la última interacción.
 * @param ultimaFechaResetDiarioMillis Marca de tiempo para resetear [interaccionesHoy] a las 04:00 AM o cambio de día.
 * @param actividadFavorita Actividad en la que ambos personajes han coincidido con mayor afinidad.
 */
@Serializable
data class Relacion(
    val otroPersonajeId: String,
    val otroOwnerId: String,
    val nombreOtro: String,
    val confianza: Int = 0,
    val interaccionesTotales: Int = 0,
    val interaccionesHoy: Int = 0,
    val ultimoContactoMillis: Long = 0L,
    val ultimaFechaResetDiarioMillis: Long = 0L,
    val actividadFavorita: String? = null
) {
    val nivelVinculo: NivelVinculo
        get() = NivelVinculo.desdeConfianza(confianza)

    val puedeInteractuarHoy: Boolean
        get() = interaccionesHoy < MAX_INTERACCIONES_DIARIAS

    companion object {
        const val MAX_INTERACCIONES_DIARIAS = 5
        const val MAX_CONFIANZA = 100
    }
}

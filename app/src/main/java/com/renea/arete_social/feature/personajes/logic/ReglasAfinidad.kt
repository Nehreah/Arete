package com.renea.arete_social.feature.personajes.logic

import com.renea.arete_social.feature.personajes.model.NivelVinculo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.Temperamento
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Tipos de actividades que pueden realizar los personajes en casas o ambientes compartidos.
 */
enum class TipoActividad(val nombreVisible: String) {
    ENTRENAR_GIMNASIO("Entrenamiento en Gimnasio"),
    PASEAR_PARQUE("Paseo en el Parque"),
    CHARLAR_CAFETERIA("Charla en Cafetería"),
    COMER_JUNTOS("Comida Compartida"),
    JUGAR_CASA("Juegos en Casa"),
    CREAR_ARTE("Creación Artística")
}

/**
 * Resultado de procesar una interacción entre dos personajes.
 */
data class ResultadoInteraccion(
    val exito: Boolean,
    val relacionActualizada: Relacion,
    val puntosConfianzaGanados: Int = 0,
    val subioDeNivel: Boolean = false,
    val motivoRechazo: String? = null
)

/**
 * Reglas de afinidad, compatibilidad y límites diarios de interacciones sociales.
 */
object ReglasAfinidad {

    private const val PUNTOS_BASE_INTERACCION = 5

    /**
     * Calcula el inicio del día social (corte a las 04:00 AM para coincidir con la mascota).
     */
    fun inicioDiaSocialMillis(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 4)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Matriz simétrica de compatibilidad entre dos temperamentos.
     * Retorna un factor multiplicador entre 0.85f y 1.35f.
     */
    fun compatibilidad(t1: Temperamento, t2: Temperamento): Float {
        if (t1 == t2) return 1.2f

        val par = if (t1.name < t2.name) t1 to t2 else t2 to t1
        return when (par) {
            Temperamento.ENERGETICO to Temperamento.SOCIABLE -> 1.35f
            Temperamento.CURIOSO to Temperamento.ENERGETICO -> 1.25f
            Temperamento.CURIOSO to Temperamento.TRANQUILO -> 1.25f
            Temperamento.ARTISTA to Temperamento.TRANQUILO -> 1.30f
            Temperamento.ARTISTA to Temperamento.CURIOSO -> 1.30f
            Temperamento.GLOTON to Temperamento.SOCIABLE -> 1.25f
            Temperamento.ENERGETICO to Temperamento.TRANQUILO -> 0.85f
            Temperamento.ARTISTA to Temperamento.GLOTON -> 1.05f
            Temperamento.CURIOSO to Temperamento.SOCIABLE -> 1.20f
            Temperamento.CURIOSO to Temperamento.GLOTON -> 1.10f
            Temperamento.ARTISTA to Temperamento.ENERGETICO -> 1.00f
            Temperamento.ARTISTA to Temperamento.SOCIABLE -> 1.20f
            Temperamento.GLOTON to Temperamento.TRANQUILO -> 1.15f
            Temperamento.ENERGETICO to Temperamento.GLOTON -> 0.95f
            Temperamento.SOCIABLE to Temperamento.TRANQUILO -> 1.15f
            else -> 1.0f
        }
    }

    /**
     * Factor de afinidad de un temperamento hacia una actividad específica.
     */
    fun afinidadActividad(temperamento: Temperamento, actividad: TipoActividad): Float {
        return when (actividad) {
            TipoActividad.ENTRENAR_GIMNASIO -> when (temperamento) {
                Temperamento.ENERGETICO -> 1.4f
                Temperamento.TRANQUILO -> 0.8f
                else -> 1.0f
            }
            TipoActividad.PASEAR_PARQUE -> when (temperamento) {
                Temperamento.TRANQUILO -> 1.35f
                Temperamento.CURIOSO -> 1.30f
                else -> 1.0f
            }
            TipoActividad.CHARLAR_CAFETERIA -> when (temperamento) {
                Temperamento.SOCIABLE -> 1.4f
                Temperamento.TRANQUILO -> 1.15f
                else -> 1.0f
            }
            TipoActividad.COMER_JUNTOS -> when (temperamento) {
                Temperamento.GLOTON -> 1.4f
                Temperamento.SOCIABLE -> 1.2f
                else -> 1.0f
            }
            TipoActividad.JUGAR_CASA -> when (temperamento) {
                Temperamento.CURIOSO -> 1.3f
                Temperamento.ENERGETICO -> 1.2f
                else -> 1.0f
            }
            TipoActividad.CREAR_ARTE -> when (temperamento) {
                Temperamento.ARTISTA -> 1.4f
                Temperamento.CURIOSO -> 1.2f
                else -> 1.0f
            }
        }
    }

    /**
     * Aplica una interacción social sobre una relación existente.
     * Respeta el tope diario de 5 interacciones para mitigar farming.
     */
    fun aplicarInteraccion(
        relacion: Relacion,
        actividad: TipoActividad,
        miTemperamento: Temperamento,
        otroTemperamento: Temperamento,
        ahoraMillis: Long = System.currentTimeMillis()
    ): ResultadoInteraccion {
        val inicioHoy = inicioDiaSocialMillis(ahoraMillis)
        val esMismoDia = relacion.ultimaFechaResetDiarioMillis == inicioHoy

        val interaccionesHoyActual = if (esMismoDia) relacion.interaccionesHoy else 0
        if (interaccionesHoyActual >= Relacion.MAX_INTERACCIONES_DIARIAS) {
            return ResultadoInteraccion(
                exito = false,
                relacionActualizada = relacion,
                puntosConfianzaGanados = 0,
                subioDeNivel = false,
                motivoRechazo = "Se alcanzó el límite diario de ${Relacion.MAX_INTERACCIONES_DIARIAS} interacciones con este personaje."
            )
        }

        val comp = compatibilidad(miTemperamento, otroTemperamento)
        val afinidadPropia = afinidadActividad(miTemperamento, actividad)
        val afinidadOtro = afinidadActividad(otroTemperamento, actividad)
        val factorPromedio = comp * ((afinidadPropia + afinidadOtro) / 2.0f)

        val puntosGanados = maxOf(1, (PUNTOS_BASE_INTERACCION * factorPromedio).roundToInt())
        val nuevaConfianza = minOf(Relacion.MAX_CONFIANZA, relacion.confianza + puntosGanados)

        val nivelAnterior = relacion.nivelVinculo
        val nuevoNivel = NivelVinculo.desdeConfianza(nuevaConfianza)
        val subioDeNivel = nuevoNivel.nivel > nivelAnterior.nivel

        val nuevaRelacion = relacion.copy(
            confianza = nuevaConfianza,
            interaccionesTotales = relacion.interaccionesTotales + 1,
            interaccionesHoy = interaccionesHoyActual + 1,
            ultimoContactoMillis = ahoraMillis,
            ultimaFechaResetDiarioMillis = inicioHoy,
            actividadFavorita = actividad.nombreVisible
        )

        return ResultadoInteraccion(
            exito = true,
            relacionActualizada = nuevaRelacion,
            puntosConfianzaGanados = puntosGanados,
            subioDeNivel = subioDeNivel
        )
    }
}

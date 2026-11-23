package com.renea.arete_social.gui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

enum class CicloDia { DIA, NOCHE }

/**
 * Constantes horarias del ciclo SolarPunk Día/Noche (§3.3).
 */
const val HORA_INICIO_DIA = 7
const val HORA_INICIO_NOCHE = 19

/**
 * Lógica pura de resolución de ciclo horario. Sin dependencias de Android,
 * 100% testeable con JUnit y reloj determinista inyectable.
 */
object ResolutorCicloDia {
    fun resolver(hora: LocalTime): CicloDia =
        if (hora.hour in HORA_INICIO_DIA until HORA_INICIO_NOCHE) CicloDia.DIA else CicloDia.NOCHE

    fun resolver(clock: Clock): CicloDia =
        resolver(LocalTime.now(clock))

    /**
     * Calcula la duración exacta hasta el próximo cambio de ciclo (07:00 o 19:00).
     */
    fun duracionHastaProximoCruce(ahora: LocalDateTime): Duration {
        val hoyInicioDia = ahora.toLocalDate().atTime(HORA_INICIO_DIA, 0)
        val hoyInicioNoche = ahora.toLocalDate().atTime(HORA_INICIO_NOCHE, 0)
        val mananaInicioDia = ahora.toLocalDate().plusDays(1).atTime(HORA_INICIO_DIA, 0)

        val proximoCruce = when {
            ahora.isBefore(hoyInicioDia) -> hoyInicioDia
            ahora.isBefore(hoyInicioNoche) -> hoyInicioNoche
            else -> mananaInicioDia
        }

        return Duration.between(ahora, proximoCruce)
    }
}

/**
 * Resolución segura del ciclo día/noche con fallback a [CicloDia.DIA] si el reloj
 * del sistema no puede leerse.
 */
fun resolverCicloDiaSeguro(clock: Clock = Clock.systemDefaultZone()): CicloDia =
    runCatching { ResolutorCicloDia.resolver(clock) }
        .getOrDefault(CicloDia.DIA)

/**
 * Estado reactivo del ciclo horario que se reprograma automáticamente al siguiente
 * cruce de las 07:00 o 19:00 horas (sin polling continuo).
 */
@Composable
fun rememberCicloDia(clock: Clock = Clock.systemDefaultZone()): State<CicloDia> {
    val cicloState = remember { mutableStateOf(resolverCicloDiaSeguro(clock)) }

    LaunchedEffect(clock) {
        while (true) {
            val ahora = LocalDateTime.now(clock)
            val duracion = ResolutorCicloDia.duracionHastaProximoCruce(ahora)
            val milisHastaCruce = duracion.toMillis().coerceAtLeast(1000L)

            delay(milisHastaCruce)
            cicloState.value = resolverCicloDiaSeguro(clock)
        }
    }

    return cicloState
}

val LocalCicloDia = staticCompositionLocalOf { CicloDia.DIA }

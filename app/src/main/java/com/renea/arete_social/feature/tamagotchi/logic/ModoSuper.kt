package com.renea.arete_social.feature.tamagotchi.logic

import com.renea.arete_social.domain.util.duracionLegible
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.RasgoPet
import kotlin.math.ceil
import kotlin.math.max

// ============================================================================
// MODO SÚPER — funciones puras (sin Android), como el resto de PetSimulation.
//
// Cuando Michi está perfecta (hambre, ánimo, higiene y salud al 100 y energía ≥ 97) despierta
// sus súper poderes durante 3 h: su dueño ve los lugares y eventos públicos de su zona regional
// (ver feature/descubrimiento/ServicioVisionRegional.kt). Se puede activar como máximo una vez
// cada 20 h. Los números están en PetBalance (SUPER_*) y se calibraron con
// simulacion_modo_super.py: la energía es el cuello de botella, así que la meta premia
// planificar cuándo duerme Michi y no insistir con muchas visitas.
//
// Todo cuelga de un único campo: PetEntity.superDesdeMillis (instante de la última activación).
// ============================================================================

/** Las cinco estadísticas que cuentan para el Súper. */
enum class EstadisticaSuper(val titulo: String) {
    HAMBRE("Comida"),
    ANIMO("Ánimo"),
    HIGIENE("Higiene"),
    SALUD("Salud"),
    ENERGIA("Energía")
}

/** Una estadística con su valor actual y el que se necesita. */
data class RequisitoSuper(val estadistica: EstadisticaSuper, val valor: Int, val objetivo: Int) {
    val cumplido: Boolean get() = valor >= objetivo
}

/**
 * Lo que la pantalla necesita para el panel «Camino al Súper».
 * [pista] es el consejo más relevante para avanzar (null si está activo o no hace falta).
 */
data class ProgresoSuper(
    val requisitos: List<RequisitoSuper>,
    val activo: Boolean,
    val restanteMillis: Long,
    val enfriamientoMillis: Long,
    val pista: String?
) {
    val metaCumplida: Boolean get() = requisitos.all { it.cumplido }
    val cumplidos: Int get() = requisitos.count { it.cumplido }
}

/** True si las estadísticas cumplen la meta (sin mirar si puede activarse ahora). */
fun PetEntity.cumpleMetaSuper(): Boolean =
    hunger >= PetBalance.SUPER_STAT_REQUERIDA &&
        happiness >= PetBalance.SUPER_STAT_REQUERIDA &&
        hygiene >= PetBalance.SUPER_STAT_REQUERIDA &&
        health >= PetBalance.SUPER_STAT_REQUERIDA &&
        energy >= PetBalance.SUPER_ENERGIA_MINIMA

/** True si el Modo Súper está activo en [now]. */
fun PetEntity.modoSuperActivo(now: Long = System.currentTimeMillis()): Boolean =
    superDesdeMillis > 0L && now >= superDesdeMillis && now < superDesdeMillis + PetBalance.SUPER_DURATION_MILLIS

/** Milisegundos que le quedan al Súper activo (0 si no está activo). */
fun PetEntity.milisSuperRestantes(now: Long = System.currentTimeMillis()): Long =
    if (modoSuperActivo(now)) superDesdeMillis + PetBalance.SUPER_DURATION_MILLIS - now else 0L

/** Milisegundos que faltan para poder activarlo otra vez (0 si ya se puede). */
fun PetEntity.milisHastaPoderActivar(now: Long = System.currentTimeMillis()): Long {
    if (superDesdeMillis <= 0L) return 0L
    val falta = superDesdeMillis + PetBalance.SUPER_COOLDOWN_MILLIS - now
    // Si el reloj retrocedió, nunca se espera más que el enfriamiento completo.
    return falta.coerceIn(0L, PetBalance.SUPER_COOLDOWN_MILLIS)
}

/** True si en este instante se cumplen TODAS las condiciones para despertar el Súper. */
fun PetEntity.puedeActivarSuper(now: Long = System.currentTimeMillis()): Boolean =
    estaDisponible() && !isSick &&
        !modoSuperActivo(now) &&
        milisHastaPoderActivar(now) == 0L &&
        cumpleMetaSuper()

/**
 * Activa el Modo Súper si se cumplen las condiciones. Idempotente: si ya está activo, o en
 * enfriamiento, devuelve la mascota sin cambios (nunca se reinicia ni se extiende).
 */
fun PetEntity.evaluarModoSuper(now: Long = System.currentTimeMillis()): PetEntity =
    if (puedeActivarSuper(now)) copy(superDesdeMillis = now) else this

// ── Dormir y despertar ───────────────────────────────────────────────────────

/**
 * Instante (milisegundos UTC) en que Michi llega a energía 100 y despierta sola.
 *  - Si duerme: contado desde la última puesta al día, con el bono de «Noctámbula».
 *  - Si está despierta y podría dormir: estimación de «si la duermes ahora».
 *  - Si no tiene sueño (energía ≥ 90) o no puede dormir: null.
 */
fun PetEntity.momentoDespertar(now: Long = System.currentTimeMillis()): Long? {
    if (isCorrupted || stage == PetStage.HUEVO) return null
    val bono = if (tieneRasgo(RasgoPet.NOCTAMBULA)) PetBalance.TRAIT_NIGHT_OWL_SLEEP_FACTOR else 1.0
    val tasa = PetBalance.ENERGY_RECOVERY_PER_MIN_ASLEEP * bono
    return if (isSleeping) {
        val faltan = (PetBalance.MAX_STAT - energy) - resto.energy
        val minutos = max(0.0, faltan / tasa)
        lastTickMillis + (minutos * 60_000.0).toLong()
    } else {
        if (energy >= PetBalance.NOT_SLEEPY_THRESHOLD) return null
        val minutos = (PetBalance.MAX_STAT - energy) / tasa
        now + (minutos * 60_000.0).toLong()
    }
}

/** Milisegundos hasta que despierte (o hasta que despertaría si la duermes ahora). Null si no aplica. */
fun PetEntity.milisHastaDespertar(now: Long = System.currentTimeMillis()): Long? =
    momentoDespertar(now)?.let { (it - now).coerceAtLeast(0L) }

// ── Panel «Camino al Súper» ──────────────────────────────────────────────────

/** Requisitos con su valor actual, el estado del Súper y el consejo más útil ahora. */
fun PetEntity.progresoSuper(now: Long = System.currentTimeMillis()): ProgresoSuper {
    val requisitos = listOf(
        RequisitoSuper(EstadisticaSuper.HAMBRE, hunger, PetBalance.SUPER_STAT_REQUERIDA),
        RequisitoSuper(EstadisticaSuper.ANIMO, happiness, PetBalance.SUPER_STAT_REQUERIDA),
        RequisitoSuper(EstadisticaSuper.HIGIENE, hygiene, PetBalance.SUPER_STAT_REQUERIDA),
        RequisitoSuper(EstadisticaSuper.SALUD, health, PetBalance.SUPER_STAT_REQUERIDA),
        RequisitoSuper(EstadisticaSuper.ENERGIA, energy, PetBalance.SUPER_ENERGIA_MINIMA)
    )
    val activo = modoSuperActivo(now)
    return ProgresoSuper(
        requisitos = requisitos,
        activo = activo,
        restanteMillis = milisSuperRestantes(now),
        enfriamientoMillis = milisHastaPoderActivar(now),
        pista = if (activo) null else pistaSuper(now)
    )
}

/** El consejo más relevante para llegar a la meta, o null si ya está lista o no hay nada que decir. */
private fun PetEntity.pistaSuper(now: Long): String? {
    if (isCorrupted) return "$name está fuera de línea. Reiníciala para empezar."
    if (stage == PetStage.HUEVO) return null
    if (isSick) return "$name está enferma: cúrala primero."
    if (isSleeping) {
        val falta = milisHastaDespertar(now)
        return if (falta != null && falta > 0L) "$name duerme: despertará en ${duracionLegible(falta)}." else null
    }
    val enfriamiento = milisHastaPoderActivar(now)
    if (enfriamiento > 0L) return "El Súper volverá a estar disponible en ${duracionLegible(enfriamiento)}."

    // 1) La energía manda: solo sube durmiendo y despertando sola (comer y la Chispa social dan un margen).
    if (energy < PetBalance.SUPER_ENERGIA_MINIMA) {
        val faltaEnergia = PetBalance.SUPER_ENERGIA_MINIMA - energy
        return when {
            faltaEnergia <= RACION_ENERGIA && hunger < PetBalance.FULL_THRESHOLD ->
                "Comer le sube un poco la energía."
            faltaEnergia <= CHISPA_ENERGIA && chispas > 0 ->
                "Una Chispa social le sube la energía lo justo."
            else ->
                "Su energía solo sube durmiendo y despertando sola. Una Chispa social da un margen."
        }
    }
    // 2) Comida: hay una «zona muerta» entre 90 y 99 (no quiere comer).
    if (hunger < PetBalance.SUPER_STAT_REQUERIDA) {
        if (hunger >= PetBalance.FULL_THRESHOLD) {
            val bajada = milisHastaBajar(hunger, PetBalance.FULL_THRESHOLD - 1, PetBalance.HUNGER_DECAY_PER_MIN)
            val espera = max(milisHasta(Cuidado.ALIMENTAR, now), bajada)
            return "Aún no tiene hambre; podrá comer en ${duracionLegible(espera)}."
        }
        if (hunger + RACION_HAMBRE < PetBalance.SUPER_STAT_REQUERIDA) {
            return "Una ración no la dejará al 100: usa una Chispa social o espera a la siguiente comida."
        }
        val espera = milisHasta(Cuidado.ALIMENTAR, now)
        if (espera > 0L) return "Podrá comer en ${duracionLegible(espera)}."
        return "Dale de comer: una ración la deja al 100."
    }
    // 3) Higiene: otra «zona muerta» entre 95 y 99 (ya está muy limpia para bañarla).
    if (hygiene < PetBalance.SUPER_STAT_REQUERIDA) {
        if (hygiene >= HIGIENE_YA_LIMPIA) {
            val bajada = milisHastaBajar(hygiene, HIGIENE_YA_LIMPIA - 1, PetBalance.HYGIENE_DECAY_PER_MIN)
            val espera = max(milisHasta(Cuidado.BANAR, now), bajada)
            return "Está muy limpia para bañarla; espera ${duracionLegible(espera)}."
        }
        val espera = milisHasta(Cuidado.BANAR, now)
        if (espera > 0L) return "Podrás bañarla en ${duracionLegible(espera)}."
        return "Báñala: la deja al 100."
    }
    if (happiness < PetBalance.SUPER_STAT_REQUERIDA) return "Los mimos le suben el ánimo."
    if (health < PetBalance.SUPER_STAT_REQUERIDA) return "Su salud sube sola mientras esté bien alimentada y limpia."
    return null
}

// ── Utilidades privadas ──────────────────────────────────────────────────────

/** Ganancias reales de la ración y de la Chispa social (se leen de FoodType para no duplicar números). */
private val RACION_HAMBRE get() = FoodType.RACION_SOLAR.hungerGain
private val RACION_ENERGIA get() = FoodType.RACION_SOLAR.energyGain
private val CHISPA_ENERGIA get() = FoodType.CHISPA_SOCIAL.energyGain

/** Por encima de este valor ya no se puede bañar (ver banar()). */
private const val HIGIENE_YA_LIMPIA = 95

/** Milisegundos que faltan para que [valor] baje hasta [umbral] a ritmo [tasa] por minuto (0 si ya está ahí). */
private fun milisHastaBajar(valor: Int, umbral: Int, tasa: Double): Long =
    if (valor <= umbral) 0L else (ceil((valor - umbral) / tasa) * 60_000.0).toLong()

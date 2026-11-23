package com.renea.arete_social.feature.tamagotchi.logic

import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.NivelCuidado
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetResto
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.RasgoPet
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ============================================================================
// SIMULACIÓN DE LA MASCOTA — funciones puras (sin base de datos ni Android).
// Cada acción recibe la mascota y devuelve una mascota nueva + un resultado.
// Así se pueden probar con tests unitarios y la pantalla siempre sabe qué pasó.
// ============================================================================

/** Mascota resultante + qué ocurrió. Si [resultado] no es OK, [pet] llega sin cambios de juego. */
data class AccionResultado(val pet: PetEntity, val resultado: ResultadoCuidado)

private fun PetEntity.rechazo(motivo: ResultadoCuidado) = AccionResultado(this, motivo)

private fun Int.coerceStat(): Int = coerceIn(PetBalance.MIN_STAT, PetBalance.MAX_STAT)

/** Cuidados que tienen tiempo de espera. */
enum class Cuidado { ALIMENTAR, JUGAR, BANAR }

// ── Tiempo y días ────────────────────────────────────────────────────────────

/**
 * Inicio del «día» de la mascota que contiene [now]: las 4:00 locales (no la medianoche).
 * Así quien se acuesta tarde no ve que los contadores diarios se reinician en pleno juego.
 */
fun inicioDiaMillis(now: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = now
    cal.set(Calendar.HOUR_OF_DAY, PetBalance.DAY_START_HOUR)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -1)
    return cal.timeInMillis
}

/** Pone a cero los contadores diarios cuando empezó un día nuevo (a las 4:00). */
fun PetEntity.resetDiarioSiNecesario(now: Long = System.currentTimeMillis()): PetEntity {
    val hoy = inicioDiaMillis(now)
    if (lastDayResetMillis == hoy) return this
    return copy(
        vecesAlimentadoHoy = 0,
        vecesLimpiadoHoy = 0,
        vecesJugadoHoy = 0,
        cariciasHoy = 0,
        chispasGanadasHoy = 0,
        puntosIndigestion = 0,
        visitasHoy = 0,
        lastDayResetMillis = hoy
    )
}

// ── Desgaste con restos decimales ────────────────────────────────────────────

private data class Paso(val valor: Int, val resto: Float)

/**
 * Suma [delta] (positivo o negativo) a una estadística entera sin perder las fracciones:
 * la parte entera se aplica y el sobrante se guarda para la próxima actualización.
 */
private fun aplicarDelta(valor: Int, resto: Float, delta: Double): Paso {
    val total = resto + delta
    val enteros = if (total >= 0) floor(total) else ceil(total) // truncar hacia cero
    val nuevo = (valor + enteros.toInt()).coerceStat()
    // Solo se descarta el sobrante si la estadística choca CONTRA el tope en la dirección del cambio
    // (p. ej. sube estando al 100). Si está al 100 y baja una fracción, esa fracción se conserva:
    // antes se perdía en cada tick de 15 s y una estadística al 100 casi nunca bajaba.
    val topada = (nuevo == PetBalance.MAX_STAT && total > 0.0) || (nuevo == PetBalance.MIN_STAT && total < 0.0)
    val sobrante = if (topada) 0f else (total - enteros).toFloat()
    return Paso(nuevo, sobrante)
}

/**
 * Aplica el paso del tiempo real entre [PetEntity.lastTickMillis] y [now].
 *  - El huevo no se desgasta (solo espera a nacer). Fuera de línea no se desgasta nada.
 *  - Al volver tras mucho tiempo solo se aplican [PetBalance.MAX_CATCHUP_MINUTES] de desgaste,
 *    y una ausencia nunca la deja fuera de línea (queda enferma, no «muerta»).
 *  - Dormida se desgasta muy poco y recupera energía; al llegar a 100 despierta SOLA y el resto
 *    del intervalo cuenta como despierta (así dormir 8 h y abrir la app a las 9 h es correcto).
 *  - La enfermedad es determinista: la salud baja cuando hambre o higiene llegan a zona crítica
 *    y, si baja de [PetBalance.HEALTH_TO_FALL_SICK], se enferma. Ya no depende de dados.
 *
 * [random] se conserva solo por compatibilidad con quien ya lo pasaba; ya no se usa.
 */
fun PetEntity.applyTick(
    now: Long = System.currentTimeMillis(),
    @Suppress("UNUSED_PARAMETER") random: Random = Random.Default,
    factorDesgaste: Double = 1.0
): PetEntity {
    var pet = copy(stage = PetStage.forAge((now - birthMillis).coerceAtLeast(0L)))
        .resetDiarioSiNecesario(now)
    if (pet.modoTurbo && now >= pet.turboHastaMillis) pet = pet.copy(modoTurbo = false)

    // El huevo y la mascota fuera de línea no se desgastan: solo se avanza el reloj.
    if (pet.isCorrupted || pet.stage == PetStage.HUEVO) {
        return if (now > pet.lastTickMillis) pet.copy(lastTickMillis = now) else pet
    }

    // El desgaste empieza al nacer: el tiempo que pasó dentro del huevo no cuenta.
    val nacimiento = pet.birthMillis + PetStage.BEBE.minAgeMillis
    val desde = max(pet.lastTickMillis, nacimiento)
    val minutosReales = (now - desde).coerceAtLeast(0L) / 60_000.0
    if (minutosReales <= 0.0) return pet

    val minutos = (minutosReales * factorDesgaste.coerceIn(1.0, 3.0)).coerceAtMost(PetBalance.MAX_CATCHUP_MINUTES)
    val ausente = minutosReales > PetBalance.AWAY_PROTECTION_MINUTES
    val piso = if (ausente) min(pet.health, PetBalance.AWAY_HEALTH_FLOOR) else 0

    var actual: PetEntity
    if (pet.isSleeping) {
        val bono = if (pet.tieneRasgo(RasgoPet.NOCTAMBULA)) PetBalance.TRAIT_NIGHT_OWL_SLEEP_FACTOR else 1.0
        val faltan = (PetBalance.MAX_STAT - pet.energy) - pet.resto.energy
        val minutosHastaLlena = if (faltan <= 0f) 0.0 else faltan / (PetBalance.ENERGY_RECOVERY_PER_MIN_ASLEEP * bono)
        if (minutosHastaLlena < minutos) {
            // Se llenó de energía durante el intervalo: durmió hasta ese momento y luego estuvo despierta.
            val dormido = pet.avanzar(minutosHastaLlena, dormida = true, pisoSalud = piso)
            actual = if (dormido.isCorrupted) {
                dormido
            } else {
                val momento = desde + (minutosHastaLlena * 60_000.0).toLong()
                dormido.copy(energy = PetBalance.MAX_STAT).despertar(momento)
                    .avanzar(minutos - minutosHastaLlena, dormida = false, pisoSalud = piso)
            }
        } else {
            actual = pet.avanzar(minutos, dormida = true, pisoSalud = piso)
        }
    } else {
        actual = pet.avanzar(minutos, dormida = false, pisoSalud = piso)
    }

    actual = actual.copy(lastTickMillis = now)
    if (actual.modoTurbo && now >= actual.turboHastaMillis) actual = actual.copy(modoTurbo = false)
    // El Modo Súper se evalúa al final de cada tick y tras cada cuidado (ver ModoSuper.kt).
    return actual.evaluarRasgos(now).evaluarModoSuper(now)
}

/** Minutos que faltan para que [valor] baje hasta [umbral] a ritmo [tasa] (0 si ya está por debajo). */
private fun minutosHastaBajar(valor: Int, umbral: Int, tasa: Double): Double =
    if (valor <= umbral || tasa <= 0.0) 0.0 else (valor - umbral) / tasa

/**
 * Avanza [minutos] con el estado fijo [dormida]. Es el corazón del desgaste:
 *  - hambre / higiene / ánimo bajan lento (y casi nada dormida);
 *  - la energía baja muy lento despierta y sube dormida;
 *  - la salud baja SOLO mientras hambre o higiene están en zona crítica, y sube cuando ambas están bien.
 * [pisoSalud] evita que una ausencia larga la deje fuera de línea.
 */
private fun PetEntity.avanzar(minutos: Double, dormida: Boolean, pisoSalud: Int): PetEntity {
    if (minutos <= 0.0) return this
    val factorSueno = if (dormida) PetBalance.SLEEP_DECAY_FACTOR else 1.0
    val factorSalud = if (tieneRasgo(RasgoPet.RESISTENTE)) PetBalance.TRAIT_RESISTANT_HEALTH_FACTOR else 1.0
    val tasaHambre = PetBalance.HUNGER_DECAY_PER_MIN * factorSueno
    val tasaHigiene = PetBalance.HYGIENE_DECAY_PER_MIN * factorSueno

    // Cuánto del intervalo estuvo en zona crítica (descuidada) y cuánto bien cuidada (≥ 50 en ambas).
    val hastaCritico = min(
        minutosHastaBajar(hunger, PetBalance.CRITICAL_THRESHOLD, tasaHambre),
        minutosHastaBajar(hygiene, PetBalance.CRITICAL_THRESHOLD, tasaHigiene)
    )
    val minutosDescuidada = (minutos - hastaCritico).coerceAtLeast(0.0)
    val hastaDejarDeEstarBien = min(
        minutosHastaBajar(hunger, PetBalance.WELL_CARED_THRESHOLD - 1, tasaHambre),
        minutosHastaBajar(hygiene, PetBalance.WELL_CARED_THRESHOLD - 1, tasaHigiene)
    )
    val minutosBien = min(minutos, hastaDejarDeEstarBien)

    val hambre = aplicarDelta(hunger, resto.hunger, -tasaHambre * minutos)
    val higiene = aplicarDelta(hygiene, resto.hygiene, -tasaHigiene * minutos)

    val deltaEnergia = if (dormida) {
        val bono = if (tieneRasgo(RasgoPet.NOCTAMBULA)) PetBalance.TRAIT_NIGHT_OWL_SLEEP_FACTOR else 1.0
        PetBalance.ENERGY_RECOVERY_PER_MIN_ASLEEP * bono * minutos
    } else {
        -PetBalance.ENERGY_DECAY_PER_MIN_AWAKE * minutos
    }
    val energia = aplicarDelta(energy, resto.energy, deltaEnergia)

    val sufre = hambre.valor <= PetBalance.CRITICAL_THRESHOLD ||
        higiene.valor <= PetBalance.CRITICAL_THRESHOLD ||
        energia.valor <= PetBalance.EXHAUSTED_THRESHOLD || isSick
    val penalAnimo = if (sufre) 1.5 else 1.0
    val animo = aplicarDelta(
        happiness, resto.happiness,
        -PetBalance.HAPPINESS_DECAY_PER_MIN * factorSueno * penalAnimo * minutos
    )

    var deltaSalud = -PetBalance.HEALTH_LOSS_NEGLECT_PER_MIN * minutosDescuidada * factorSalud
    if (isSick) {
        deltaSalud -= PetBalance.HEALTH_LOSS_SICK_PER_MIN * (minutos - minutosBien) * factorSalud
        deltaSalud += PetBalance.HEALTH_RECOVERY_SICK_PER_MIN * minutosBien
    } else {
        deltaSalud += PetBalance.HEALTH_RECOVERY_PER_MIN * minutosBien
    }
    val salud = aplicarDelta(health, resto.health, deltaSalud)
    val saludFinal = max(salud.valor, pisoSalud)

    val fueraDeLinea = saludFinal <= PetBalance.MIN_STAT
    val enferma = if (isSick) saludFinal < PetBalance.HEALTH_TO_CURE_SICKNESS
    else saludFinal < PetBalance.HEALTH_TO_FALL_SICK

    return copy(
        hunger = hambre.valor,
        hygiene = higiene.valor,
        happiness = animo.valor,
        energy = energia.valor,
        health = saludFinal,
        isSleeping = dormida && !fueraDeLinea,
        duermeDesdeMillis = if (fueraDeLinea) 0L else duermeDesdeMillis,
        isSick = enferma && !fueraDeLinea,
        isCorrupted = fueraDeLinea,
        resto = PetResto(
            hunger = hambre.resto,
            happiness = animo.resto,
            hygiene = higiene.resto,
            energy = energia.resto,
            health = if (saludFinal != salud.valor) 0f else salud.resto
        )
    )
}

// ── Experiencia y nivel ──────────────────────────────────────────────────────

/** Experiencia necesaria para pasar del nivel [nivel] al siguiente. */
fun xpParaSubir(nivel: Int): Int = (nivel * 100).coerceAtLeast(100)

/** Suma experiencia (doble en modo turbo) y sube de nivel todas las veces que corresponda. */
fun PetEntity.ganarXp(cantidad: Int, now: Long = System.currentTimeMillis()): PetEntity {
    if (cantidad <= 0) return this
    val multiplicador = if (modoTurbo && now < turboHastaMillis) 2 else 1
    var xp = experiencia + cantidad * multiplicador
    var lvl = nivel
    while (xp >= xpParaSubir(lvl)) {
        xp -= xpParaSubir(lvl)
        lvl++
    }
    return copy(experiencia = xp, nivel = lvl)
}

// ── Rasgos ───────────────────────────────────────────────────────────────────

fun PetEntity.rasgos(): Set<RasgoPet> =
    rasgosDesbloqueados.split(",")
        .mapNotNull { nombre -> RasgoPet.entries.firstOrNull { it.name == nombre.trim() } }
        .toSet()

fun PetEntity.tieneRasgo(rasgo: RasgoPet): Boolean = rasgo in rasgos()

private fun PetEntity.desbloquear(rasgo: RasgoPet): PetEntity {
    if (tieneRasgo(rasgo)) return this
    val nuevos = if (rasgosDesbloqueados.isBlank()) rasgo.name else "$rasgosDesbloqueados,${rasgo.name}"
    return copy(rasgosDesbloqueados = nuevos)
}

/** Desbloquea rasgos según el progreso acumulado. Idempotente. */
fun PetEntity.evaluarRasgos(now: Long = System.currentTimeMillis()): PetEntity {
    var r = this
    val edadHoras = (now - birthMillis) / 3_600_000L
    if (nivel >= 3 && health >= 70) r = r.desbloquear(RasgoPet.RESISTENTE)
    if (nivel >= 4) r = r.desbloquear(RasgoPet.VELOZ)
    if (edadHoras >= 48 && happiness >= 60) r = r.desbloquear(RasgoPet.NOCTAMBULA)
    if (nivel >= 5 && puntosIndigestion == 0) r = r.desbloquear(RasgoPet.ESTOMAGO_DE_HIERRO)
    if (chispasGanadasHoy >= 3) r = r.desbloquear(RasgoPet.SOCIABLE)
    return r
}

// ── Esperas entre cuidados ───────────────────────────────────────────────────

private fun PetEntity.ultimo(cuidado: Cuidado): Long = when (cuidado) {
    Cuidado.ALIMENTAR -> ultimoAlimentadoMillis
    Cuidado.JUGAR -> ultimoJugadoMillis
    Cuidado.BANAR -> ultimoLimpiadoMillis
}

private fun PetEntity.esperaBase(cuidado: Cuidado): Long {
    val base = when (cuidado) {
        Cuidado.ALIMENTAR -> PetBalance.COOLDOWN_FEED_MILLIS
        Cuidado.JUGAR -> PetBalance.COOLDOWN_PLAY_MILLIS
        Cuidado.BANAR -> PetBalance.COOLDOWN_CLEAN_MILLIS
    }
    return if (tieneRasgo(RasgoPet.VELOZ)) (base * PetBalance.TRAIT_FAST_COOLDOWN_FACTOR).toLong() else base
}

/**
 * Milisegundos que faltan para poder repetir [cuidado] (0 = ya se puede).
 * Si tiene mucha hambre o está muy sucia, la espera se salta: nunca se queda sin ayuda.
 */
fun PetEntity.milisHasta(cuidado: Cuidado, now: Long = System.currentTimeMillis()): Long {
    val ultimo = ultimo(cuidado)
    if (ultimo == 0L) return 0L
    if (cuidado == Cuidado.ALIMENTAR && hunger <= PetBalance.URGENT_THRESHOLD) return 0L
    if (cuidado == Cuidado.BANAR && hygiene <= PetBalance.URGENT_THRESHOLD) return 0L
    val espera = esperaBase(cuidado)
    return (ultimo + espera - now).coerceIn(0L, espera)
}

/** Factor de eficacia: 1.0 dentro del límite diario y baja hasta 0.3 si se abusa. */
private fun factorRendimiento(vecesHoy: Int, maxDia: Int): Double {
    if (vecesHoy < maxDia) return 1.0
    val exceso = (vecesHoy - maxDia + 1).coerceAtMost(3)
    return (1.0 - exceso * 0.23).coerceAtLeast(0.3)
}

// ── Visitas y nivel de cuidado ───────────────────────────────────────────────

/**
 * Anota que la persona atendió a la mascota. Cuidados separados por menos de
 * [PetBalance.VISIT_GAP_MILLIS] cuentan como una sola visita: dar de comer, bañar y jugar
 * de seguido es UNA visita, no tres.
 */
private fun PetEntity.registrarAtencion(now: Long): PetEntity {
    val visitaNueva = ultimaAtencionMillis == 0L ||
        ultimaAtencionMillis < lastDayResetMillis ||
        now < ultimaAtencionMillis ||
        now - ultimaAtencionMillis >= PetBalance.VISIT_GAP_MILLIS
    return copy(
        visitasHoy = if (visitaNueva) visitasHoy + 1 else visitasHoy,
        ultimaAtencionMillis = now
    )
}

/**
 * Cómo está de cuidada AHORA, según sus necesidades (no según cuántas visitas llevas):
 *  - necesita atención: enferma, fuera de línea o algo en zona crítica;
 *  - excelente: comida y ánimo ≥ 70 e higiene ≥ 55 (lo normal con 4-5 visitas repartidas);
 *  - estable: todo ≥ 35 (lo normal con 2-3 visitas);
 *  - justa: viva pero apagándose (lo normal con 1 visita).
 */
fun PetEntity.nivelCuidado(): NivelCuidado {
    val critico = PetBalance.CRITICAL_THRESHOLD
    return when {
        isCorrupted || isSick -> NivelCuidado.NECESITA_ATENCION
        hunger <= critico || hygiene <= critico || happiness <= critico -> NivelCuidado.NECESITA_ATENCION
        hunger >= 70 && happiness >= 70 && hygiene >= 55 -> NivelCuidado.EXCELENTE
        hunger >= 35 && happiness >= 35 && hygiene >= 35 -> NivelCuidado.ESTABLE
        else -> NivelCuidado.JUSTA
    }
}

/** Línea corta para la pantalla: «Hoy: 2/5 visitas · Estable». */
fun PetEntity.resumenAtencionHoy(): String =
    "Hoy: ${visitasHoy.coerceAtMost(PetBalance.IDEAL_VISITS_PER_DAY)}/${PetBalance.IDEAL_VISITS_PER_DAY} visitas · ${nivelCuidado().titulo}"

// ── Comprobaciones para la pantalla ──────────────────────────────────────────

/** True si puede hacer cualquier actividad (no duerme, no está fuera de línea, ya nació). */
fun PetEntity.estaDisponible(): Boolean = !isSleeping && !isCorrupted && stage != PetStage.HUEVO

/** True si cumple los requisitos de energía y hambre para jugar. */
fun PetEntity.canPlay(): Boolean =
    estaDisponible() && energy >= PetBalance.MIN_ENERGY_TO_PLAY && hunger >= PetBalance.MIN_HUNGER_TO_PLAY

/** Milisegundos hasta la siguiente etapa de vida (null si ya es anciana). */
fun PetEntity.milisHastaSiguienteEtapa(now: Long = System.currentTimeMillis()): Long? {
    val siguiente = stage.siguiente() ?: return null
    return (birthMillis + siguiente.minAgeMillis - now).coerceAtLeast(0L)
}

/** Aviso más importante que debe saber la persona ahora mismo (o null si todo va bien). */
fun PetEntity.necesidadUrgente(): String? = when {
    isCorrupted -> "$name está fuera de línea. Toca «Reiniciar» para despertarla."
    stage == PetStage.HUEVO -> null
    isSick -> "$name está enferma. Dale su medicina."
    isSleeping -> null
    hunger <= PetBalance.CRITICAL_THRESHOLD -> "$name tiene mucha hambre."
    hygiene <= PetBalance.CRITICAL_THRESHOLD -> "$name necesita un baño."
    energy <= PetBalance.CRITICAL_THRESHOLD -> "$name está agotada. Mándala a dormir toda la noche."
    happiness <= PetBalance.CRITICAL_THRESHOLD -> "$name está triste. Juega con ella o rásca la pantalla sobre ella para darle mimos."
    else -> null
}

// ── Acciones de cuidado ──────────────────────────────────────────────────────

private fun PetEntity.bloqueoGeneral(): ResultadoCuidado? = when {
    isCorrupted -> ResultadoCuidado.FUERA_DE_LINEA
    stage == PetStage.HUEVO -> ResultadoCuidado.HUEVO
    isSleeping -> ResultadoCuidado.DORMIDA
    else -> null
}

/**
 * Dar de comer. Reglas:
 *  - No come si ya está llena (≥ 90 %) ni si no pasó la espera (salvo hambre urgente).
 *  - Pasadas las 5 comidas del día rinde cada vez menos y le sienta mal
 *    (baja ánimo y salud), salvo con el rasgo «Estómago de hierro».
 *  - La chispa social cuesta 1 chispa; sin chispas NO se aplica (antes salía gratis).
 */
fun PetEntity.alimentar(comida: FoodType, now: Long = System.currentTimeMillis()): AccionResultado {
    bloqueoGeneral()?.let { return rechazo(it) }
    val base = resetDiarioSiNecesario(now)
    if (base.hunger >= PetBalance.FULL_THRESHOLD) return base.rechazo(ResultadoCuidado.NO_TIENE_HAMBRE)
    if (base.milisHasta(Cuidado.ALIMENTAR, now) > 0L) return base.rechazo(ResultadoCuidado.EN_ESPERA)
    if (comida.costsSpark && base.chispas <= 0) return base.rechazo(ResultadoCuidado.SIN_CHISPAS)

    val factor = factorRendimiento(base.vecesAlimentadoHoy, PetBalance.MAX_FEEDINGS_PER_DAY)
    val sobrealimentada = base.vecesAlimentadoHoy >= PetBalance.MAX_FEEDINGS_PER_DAY
    val leSienteMal = sobrealimentada && !base.tieneRasgo(RasgoPet.ESTOMAGO_DE_HIERRO)
    val penalAnimo = if (leSienteMal) PetBalance.OVERFEED_HAPPINESS_PENALTY else 0
    val penalSalud = if (leSienteMal) PetBalance.OVERFEED_HEALTH_PENALTY else 0

    val nueva = base.copy(
        hunger = (base.hunger + (comida.hungerGain * factor).toInt()).coerceStat(),
        energy = (base.energy + (comida.energyGain * factor).toInt()).coerceStat(),
        happiness = (base.happiness + (comida.happinessGain * factor).toInt() - penalAnimo).coerceStat(),
        health = (base.health - penalSalud).coerceAtLeast(1),
        chispas = if (comida.costsSpark) base.chispas - 1 else base.chispas,
        ultimoAlimentadoMillis = now,
        vecesAlimentadoHoy = base.vecesAlimentadoHoy + 1,
        puntosIndigestion = if (sobrealimentada) base.puntosIndigestion + 1 else base.puntosIndigestion
    ).registrarAtencion(now).ganarXp((comida.xpGain * factor).toInt(), now)
    return AccionResultado(nueva.evaluarModoSuper(now), ResultadoCuidado.OK)
}

/** Jugar: requiere energía y hambre suficientes; cansa, da hambre y ensucia un poco. */
fun PetEntity.jugar(now: Long = System.currentTimeMillis()): AccionResultado {
    bloqueoGeneral()?.let { return rechazo(it) }
    val base = resetDiarioSiNecesario(now)
    if (base.energy < PetBalance.MIN_ENERGY_TO_PLAY) return base.rechazo(ResultadoCuidado.POCA_ENERGIA)
    if (base.hunger < PetBalance.MIN_HUNGER_TO_PLAY) return base.rechazo(ResultadoCuidado.TIENE_HAMBRE)
    if (base.milisHasta(Cuidado.JUGAR, now) > 0L) return base.rechazo(ResultadoCuidado.EN_ESPERA)

    val factor = factorRendimiento(base.vecesJugadoHoy, PetBalance.MAX_PLAYS_PER_DAY)
    val nueva = base.copy(
        happiness = (base.happiness + (PetBalance.PLAY_HAPPINESS_GAIN * factor).toInt()).coerceStat(),
        energy = (base.energy - PetBalance.PLAY_ENERGY_COST).coerceStat(),
        hunger = (base.hunger - PetBalance.PLAY_HUNGER_COST).coerceStat(),
        hygiene = (base.hygiene - PetBalance.PLAY_HYGIENE_COST).coerceStat(),
        ultimoJugadoMillis = now,
        vecesJugadoHoy = base.vecesJugadoHoy + 1
    ).registrarAtencion(now).ganarXp((PetBalance.PLAY_XP * factor).toInt(), now)
    return AccionResultado(nueva.evaluarModoSuper(now), ResultadoCuidado.OK)
}

/** Bañar: higiene al máximo (menos si se abusa) y un poco más de ánimo. */
fun PetEntity.banar(now: Long = System.currentTimeMillis()): AccionResultado {
    bloqueoGeneral()?.let { return rechazo(it) }
    val base = resetDiarioSiNecesario(now)
    if (base.hygiene >= 95) return base.rechazo(ResultadoCuidado.YA_LIMPIA)
    if (base.milisHasta(Cuidado.BANAR, now) > 0L) return base.rechazo(ResultadoCuidado.EN_ESPERA)

    val factor = factorRendimiento(base.vecesLimpiadoHoy, PetBalance.MAX_CLEANINGS_PER_DAY)
    val nuevaHigiene = if (factor >= 1.0) PetBalance.MAX_STAT
    else (base.hygiene + (PetBalance.MAX_STAT - base.hygiene) * factor).toInt()
    val nueva = base.copy(
        hygiene = nuevaHigiene.coerceStat(),
        happiness = (base.happiness + (PetBalance.CLEAN_HAPPINESS_GAIN * factor).toInt()).coerceStat(),
        ultimoLimpiadoMillis = now,
        vecesLimpiadoHoy = base.vecesLimpiadoHoy + 1
    ).registrarAtencion(now).ganarXp((PetBalance.CLEAN_XP * factor).toInt(), now)
    return AccionResultado(nueva.evaluarModoSuper(now), ResultadoCuidado.OK)
}

/**
 * Medicina: solo si está enferma Y alguien le envió medicina antes (gasta una).
 * Cura salud; a la mascota no le gusta el sabor.
 */
fun PetEntity.curar(now: Long = System.currentTimeMillis()): AccionResultado {
    bloqueoGeneral()?.let { return rechazo(it) }
    if (!isSick) return rechazo(ResultadoCuidado.NO_ESTA_ENFERMA)
    if (medicinas <= 0) return rechazo(ResultadoCuidado.SIN_MEDICINA)
    val nuevaSalud = (health + PetBalance.MEDICINE_HEALTH_GAIN).coerceStat()
    val nueva = copy(
        medicinas = medicinas - 1,
        health = nuevaSalud,
        isSick = nuevaSalud < PetBalance.HEALTH_TO_CURE_SICKNESS,
        happiness = (happiness - PetBalance.MEDICINE_HAPPINESS_COST).coerceStat()
    ).registrarAtencion(now).ganarXp(PetBalance.MEDICINE_XP, now)
    return AccionResultado(nueva.evaluarModoSuper(now), ResultadoCuidado.OK)
}

/**
 * Despierta a la mascota en el instante [momento]. Si durmió al menos
 * [PetBalance.SLEEP_MILLIS_FOR_TURBO] tuvo un buen descanso: se levanta de mejor humor y
 * con modo turbo (XP doble) durante [PetBalance.TURBO_DURATION_MILLIS].
 */
private fun PetEntity.despertar(momento: Long): PetEntity {
    val durmio = if (duermeDesdeMillis > 0L) momento - duermeDesdeMillis else 0L
    val descansoLargo = durmio >= PetBalance.SLEEP_MILLIS_FOR_TURBO
    return copy(
        isSleeping = false,
        duermeDesdeMillis = 0L,
        happiness = if (descansoLargo) (happiness + PetBalance.REST_HAPPINESS_BONUS).coerceStat() else happiness,
        modoTurbo = modoTurbo || descansoLargo,
        turboHastaMillis = if (descansoLargo) max(turboHastaMillis, momento + PetBalance.TURBO_DURATION_MILLIS) else turboHastaMillis,
        resto = resto.copy(energy = 0f)
    )
}

/**
 * Dormir / despertar. Solo duerme si tiene sueño; despertar siempre se puede.
 * Dormirla toda la noche (6 h o más) y despertar da buen humor y XP doble un rato (modo turbo).
 * De un día normal, la energía tarda 7-8 h en llenarse y entonces despierta sola.
 */
fun PetEntity.alternarSueno(now: Long = System.currentTimeMillis()): AccionResultado {
    if (isCorrupted) return rechazo(ResultadoCuidado.FUERA_DE_LINEA)
    if (stage == PetStage.HUEVO) return rechazo(ResultadoCuidado.HUEVO)
    if (isSleeping) return AccionResultado(despertar(now).registrarAtencion(now), ResultadoCuidado.OK)
    if (energy >= PetBalance.NOT_SLEEPY_THRESHOLD) return rechazo(ResultadoCuidado.NO_TIENE_SUENO)
    return AccionResultado(
        copy(isSleeping = true, duermeDesdeMillis = now, resto = resto.copy(energy = 0f)).registrarAtencion(now),
        ResultadoCuidado.OK
    )
}

/** Mimos (rascarla en pantalla): suben bastante el ánimo, con pausa corta y tope diario. */
fun PetEntity.acariciar(now: Long = System.currentTimeMillis()): AccionResultado {
    bloqueoGeneral()?.let { return rechazo(it) }
    val base = resetDiarioSiNecesario(now)
    val desdeUltima = now - base.ultimaCariciaMillis
    if (desdeUltima in 0 until PetBalance.CUDDLE_MIN_GAP_MILLIS) return base.rechazo(ResultadoCuidado.EN_ESPERA)
    if (base.cariciasHoy >= PetBalance.MAX_CUDDLES_PER_DAY) return base.rechazo(ResultadoCuidado.LIMITE_DIARIO)
    val nueva = base.copy(
        happiness = (base.happiness + PetBalance.CUDDLE_HAPPINESS_GAIN).coerceStat(),
        ultimaCariciaMillis = now,
        cariciasHoy = base.cariciasHoy + 1
    ).registrarAtencion(now)
    return AccionResultado(nueva.evaluarModoSuper(now), ResultadoCuidado.OK)
}

/** Gana chispas al encontrarte con un amigo (tope diario; el rasgo Sociable da una extra). */
fun PetEntity.ganarChispaPorEncuentro(now: Long = System.currentTimeMillis()): AccionResultado {
    if (isCorrupted) return rechazo(ResultadoCuidado.FUERA_DE_LINEA)
    if (stage == PetStage.HUEVO) return rechazo(ResultadoCuidado.HUEVO)
    val base = resetDiarioSiNecesario(now)
    val quedan = PetBalance.MAX_SPARKS_FROM_FRIENDS_PER_DAY - base.chispasGanadasHoy
    if (quedan <= 0) return base.rechazo(ResultadoCuidado.LIMITE_DIARIO)
    val ganadas = min(quedan, if (base.tieneRasgo(RasgoPet.SOCIABLE)) 2 else 1)
    val nueva = base.copy(
        chispas = base.chispas + ganadas,
        chispasGanadasHoy = base.chispasGanadasHoy + ganadas
    )
    return AccionResultado(nueva.evaluarRasgos(now), ResultadoCuidado.OK)
}

/** Descuenta chispas regaladas. Falla si no hay suficientes. */
fun PetEntity.regalarChispas(cantidad: Int): AccionResultado {
    if (cantidad <= 0 || chispas < cantidad) return rechazo(ResultadoCuidado.SIN_CHISPAS)
    return AccionResultado(copy(chispas = chispas - cantidad), ResultadoCuidado.OK)
}

/** Suma chispas que otra persona le envió. Se limita la cantidad por regalo. */
fun PetEntity.recibirChispas(cantidad: Int): AccionResultado {
    if (cantidad <= 0) return rechazo(ResultadoCuidado.SIN_CHISPAS)
    val recibidas = cantidad.coerceAtMost(PetBalance.MAX_SPARKS_PER_GIFT)
    return AccionResultado(copy(chispas = chispas + recibidas), ResultadoCuidado.OK)
}

/** Guarda una medicina que otra persona le envió. Si el botiquín está lleno, no se acepta. */
fun PetEntity.recibirMedicina(): AccionResultado {
    if (medicinas >= PetBalance.MAX_MEDICINES_STORED) return rechazo(ResultadoCuidado.BOTIQUIN_LLENO)
    return AccionResultado(copy(medicinas = medicinas + 1), ResultadoCuidado.OK)
}

/** Limpia un nombre escrito por la persona: sin espacios sobrantes ni caracteres de control. Null si no sirve. */
fun nombreMascotaValido(crudo: String): String? {
    val limpio = crudo.filter { !it.isISOControl() }.trim().replace(Regex("\\s+"), " ")
    return limpio.takeIf { it.isNotEmpty() && it.length <= PetBalance.NAME_MAX_LENGTH }
}

/** Cambia el nombre de la mascota. */
fun PetEntity.renombrar(nuevo: String): AccionResultado {
    val nombre = nombreMascotaValido(nuevo) ?: return rechazo(ResultadoCuidado.NOMBRE_INVALIDO)
    return AccionResultado(copy(name = nombre), ResultadoCuidado.OK)
}

/** Reinicia a una mascota fuera de línea: vuelve con la mitad de cada necesidad y salud suficiente para no enfermar al instante. */
fun PetEntity.reiniciar(now: Long = System.currentTimeMillis()): AccionResultado {
    if (!isCorrupted) return rechazo(ResultadoCuidado.NO_ESTA_FUERA_DE_LINEA)
    val mitad = PetBalance.REBOOT_STAT_VALUE
    val nueva = copy(
        isCorrupted = false,
        isSick = false,
        isSleeping = false,
        duermeDesdeMillis = 0L,
        health = PetBalance.HEALTH_TO_CURE_SICKNESS,
        hunger = mitad,
        happiness = mitad,
        energy = mitad,
        hygiene = max(hygiene, mitad),
        modoTurbo = false,
        resto = PetResto(),
        lastTickMillis = now
    ).registrarAtencion(now)
    return AccionResultado(nueva.evaluarModoSuper(now), ResultadoCuidado.OK)
}

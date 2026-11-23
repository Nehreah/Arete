package com.renea.arete_social.feature.tamagotchi.model

/**
 * Todos los números del juego en un solo lugar.
 * Si quieres que la mascota sea más exigente o más relajada, cambia solo este archivo.
 *
 * FILOSOFÍA (v5): nadie debería vivir pegado a la app. Se piensa en el «día útil» de una
 * persona (~16 h despierta) y en la noche (~8 h dormida):
 *
 *   • 0 visitas en un día ........ se enferma (~1 día y medio sin nadie).
 *   • 1 visita al día ............ sigue viva, pero se va apagando: a los ~3 días se enferma.
 *   • 2 o 3 visitas al día ....... estable, nunca se enferma.
 *   • 4 o 5 visitas repartidas ... excelente (todo alto casi todo el día).
 *   • Dormirla toda la noche ..... recupera la energía (7-8 h) y la energía dura todo el día.
 *
 * Estos números salen de una simulación (ver LEEME_CAMBIOS_STATS.md) y están cubiertos por
 * tests de escenarios en PetSimulationTest («una semana con N visitas al día»).
 * Las tasas «por minuto» se aplican sobre tiempo real transcurrido (ver PetSimulation.kt).
 */
object PetBalance {
    const val MIN_STAT = 0
    const val MAX_STAT = 100

    /** Por debajo de este valor la mascota «necesita ayuda ya» (y empieza a perder salud). */
    const val CRITICAL_THRESHOLD = 20

    /** Por debajo de este valor se puede saltar la espera entre cuidados (hambre / higiene). */
    const val URGENT_THRESHOLD = 30

    /** Por encima de este valor ya no quiere comer. */
    const val FULL_THRESHOLD = 90

    /** Por encima de este valor de energía no tiene sueño. */
    const val NOT_SLEEPY_THRESHOLD = 90

    // ── Requisitos de actividades ────────────────────────────────────────────
    const val MIN_ENERGY_TO_PLAY = 30
    const val MIN_HUNGER_TO_PLAY = 40

    // ── Desgaste natural (puntos por minuto, DESPIERTA) ──────────────────────
    // Referencia: 0.065/min ≈ 3.9 puntos por hora ≈ 62 en las 16 h despierta.
    const val HUNGER_DECAY_PER_MIN = 0.065
    const val HAPPINESS_DECAY_PER_MIN = 0.05
    const val HYGIENE_DECAY_PER_MIN = 0.065

    /** Energía: se gasta MUY lento despierta (≈ 3 por hora, ~48 en todo el día útil)... */
    const val ENERGY_DECAY_PER_MIN_AWAKE = 0.05

    /** ...y dormida se recupera ≈ 8 por hora: de un día normal (~45) a 100 tarda 7-8 h. */
    const val ENERGY_RECOVERY_PER_MIN_ASLEEP = 0.135

    /** Mientras duerme, hambre / higiene / ánimo bajan a esta fracción del ritmo normal. */
    const val SLEEP_DECAY_FACTOR = 0.3

    /** Con la energía por debajo de este valor se considera agotada (se pone de mal humor). */
    const val EXHAUSTED_THRESHOLD = 10

    // ── Salud y enfermedad (deterministas: sin dados) ────────────────────────
    /** Salud perdida por minuto mientras hambre o higiene están en zona crítica. */
    const val HEALTH_LOSS_NEGLECT_PER_MIN = 0.06

    /** Salud perdida por minuto, además, mientras está enferma y NO bien cuidada. */
    const val HEALTH_LOSS_SICK_PER_MIN = 0.012

    /** Salud recuperada por minuto cuando hambre e higiene están ≥ 50 y está sana. */
    const val HEALTH_RECOVERY_PER_MIN = 0.03

    /** Enferma: si está bien cuidada se recupera sola, pero lento (≈ 0.6/h; ~1 día para sanar). */
    const val HEALTH_RECOVERY_SICK_PER_MIN = 0.01

    /** Mínimo de hambre e higiene para recuperar salud. */
    const val WELL_CARED_THRESHOLD = 50

    /** Si la salud baja de este valor, se enferma. */
    const val HEALTH_TO_FALL_SICK = 60

    /** Una mascota enferma se declara sana al llegar a este valor de salud. */
    const val HEALTH_TO_CURE_SICKNESS = 70

    /**
     * Máximo de minutos de desgaste que se aplican de golpe al volver a abrir la app (72 h).
     * Más allá de eso el tiempo se perdona.
     */
    const val MAX_CATCHUP_MINUTES = 72 * 60.0

    /** Si te fuiste más de este rato, la ausencia no puede dejarla fuera de línea. */
    const val AWAY_PROTECTION_MINUTES = 30.0

    /** Salud mínima que deja una ausencia: vuelves y está muy mal, pero no «muerta». */
    const val AWAY_HEALTH_FLOOR = 10

    // ── Cuidados ─────────────────────────────────────────────────────────────
    const val PLAY_HAPPINESS_GAIN = 22
    const val PLAY_ENERGY_COST = 4
    const val PLAY_HUNGER_COST = 4
    const val PLAY_HYGIENE_COST = 3
    const val PLAY_XP = 20

    const val CLEAN_HAPPINESS_GAIN = 8
    const val CLEAN_XP = 12

    const val MEDICINE_HEALTH_GAIN = 50
    const val MEDICINE_HAPPINESS_COST = 15
    const val MEDICINE_XP = 15

    // ── Regalos entre personas (se envían desde el mapa) ─────────────────────
    /** Medicinas que puede guardar la mascota a la vez. */
    const val MAX_MEDICINES_STORED = 3

    /** Chispas máximas por regalo (el receptor ignora cantidades mayores). */
    const val MAX_SPARKS_PER_GIFT = 5

    /** Pausa antes de volver a enviarle medicina a la MISMA persona. */
    const val MEDICINE_SEND_COOLDOWN_MILLIS = 10 * 60_000L

    // ── Nombre ───────────────────────────────────────────────────────────────
    const val DEFAULT_NAME = "Michi"
    const val NAME_MAX_LENGTH = 16

    // ── Descanso ─────────────────────────────────────────────────────────────
    /**
     * Si duerme al menos este rato y despierta, tuvo un «buen descanso»: XP doble un buen rato
     * y se levanta de mejor humor. Es el incentivo para dormirla mientras tú duermes.
     */
    const val SLEEP_MILLIS_FOR_TURBO = 6 * 3_600_000L

    /** Cuánto dura el XP doble después de un buen descanso. */
    const val TURBO_DURATION_MILLIS = 3 * 3_600_000L

    /** Ánimo extra al despertar tras un buen descanso. */
    const val REST_HAPPINESS_BONUS = 10

    /** Mimos = rascarla con el dedo sobre la mascota. Cada tanda de caricias sube bastante el ánimo. */
    const val CUDDLE_HAPPINESS_GAIN = 5
    const val CUDDLE_MIN_GAP_MILLIS = 4_000L

    const val OVERFEED_HAPPINESS_PENALTY = 5
    const val OVERFEED_HEALTH_PENALTY = 3

    const val REBOOT_STAT_VALUE = 50

    // ── Esperas entre cuidados (pensadas para ~5 atenciones en el día despierta) ─
    const val COOLDOWN_FEED_MILLIS = 3 * 3_600_000L
    const val COOLDOWN_PLAY_MILLIS = 3 * 3_600_000L
    const val COOLDOWN_CLEAN_MILLIS = 5 * 3_600_000L

    // ── Límites diarios (a partir de ahí el cuidado rinde cada vez menos) ───
    const val MAX_FEEDINGS_PER_DAY = 5
    const val MAX_CLEANINGS_PER_DAY = 2
    const val MAX_PLAYS_PER_DAY = 4
    const val MAX_CUDDLES_PER_DAY = 8
    const val MAX_SPARKS_FROM_FRIENDS_PER_DAY = 6

    /** Cuánto debe pasar para que un mismo amigo vuelva a darte una chispa. */
    const val FRIEND_SPARK_COOLDOWN_MILLIS = 15 * 60_000L

    // ── Visitas ──────────────────────────────────────────────────────────────
    /** Dos cuidados separados por menos de esto cuentan como la misma visita. */
    const val VISIT_GAP_MILLIS = 45 * 60_000L

    /** Visitas al día con las que la mascota está «excelente». */
    const val IDEAL_VISITS_PER_DAY = 5

    /** El «día» de la mascota empieza a esta hora local (de madrugada, cuando casi todos duermen). */
    const val DAY_START_HOUR = 4

    // ── Rasgos ───────────────────────────────────────────────────────────────
    const val TRAIT_FAST_COOLDOWN_FACTOR = 0.8
    const val TRAIT_RESISTANT_HEALTH_FACTOR = 0.5
    const val TRAIT_NIGHT_OWL_SLEEP_FACTOR = 1.25

    const val FOREGROUND_TICK_INTERVAL_MILLIS = 15_000L

    // ── Modo Súper ───────────────────────────────────────────────────────────
    // Meta calibrada con simulacion_modo_super.py (ver PLAN_CHATS_ZONALES_Y_LUGARES.md, §4.4):
    // «alcanzable pero no sencilla». La energía es el cuello de botella: solo llega a 100 cuando
    // Michi despierta sola y baja ~3 puntos por hora, así que con esta meta la visita (comer +
    // bañar + mimos) debe llegar ~2,3 h después de que despierte (~3,9 h con una Chispa social).
    //   · Energía mínima 100 → ventana ~1,3 h (más difícil).
    //   · Energía mínima  95 → ventana ~2,9 h.
    //   · Energía mínima  90 → ventana ~4,6 h (la alcanza casi cualquier cuidador constante).

    /** Hambre, ánimo, higiene y salud que debe tener para despertar el Súper. */
    const val SUPER_STAT_REQUERIDA = 100

    /** Energía mínima («bien descansada»). */
    const val SUPER_ENERGIA_MINIMA = 97

    /** Cuánto dura el Modo Súper una vez activado. */
    const val SUPER_DURATION_MILLIS = 3 * 3_600_000L

    /** Mínimo entre dos activaciones, contado desde la anterior (incluye las 3 h de duración). */
    const val SUPER_COOLDOWN_MILLIS = 20 * 3_600_000L
}

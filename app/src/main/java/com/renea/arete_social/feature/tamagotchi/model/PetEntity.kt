package com.renea.arete_social.feature.tamagotchi.model

import com.renea.arete_social.feature.personajes.model.Temperamento
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Restos decimales del desgaste. Las estadísticas son enteros (0..100), pero el desgaste
 * por minuto es fraccionario (p. ej. 0,5 de hambre/min). Sin guardar el "resto", cada
 * actualización de 15 s perdía un punto entero completo y la mascota se desgastaba ~8 veces
 * más rápido de lo configurado. Estos acumuladores lo resuelven.
 */
@Serializable
data class PetResto(
    val hunger: Float = 0f,
    val happiness: Float = 0f,
    val hygiene: Float = 0f,
    val energy: Float = 0f,
    val health: Float = 0f
)

/**
 * Entidad central de la mascota, guardada en SharedPreferences como JSON.
 *
 * HISTORIAL DE ESQUEMA
 *  v1 — campos base.
 *  v2 — cuidado realista: esperas, contadores diarios, rendimientos decrecientes, rasgos.
 *  v3 — restos decimales, mimos, entrenamiento con espera, chispas ganadas por día.
 *       Se renombraron campos con jerga ("energía oscura", "IQ"...) a nombres simples;
 *       @SerialName conserva el nombre antiguo en el JSON para no perder mascotas guardadas.
 *  v4 — medicinas recibidas de otras personas (la mascota solo se cura si alguien le envió una) y
 *       nombre editable. La mascota por defecto pasa a llamarse «Michi».
 *  v5 — ritmo pensado para el día de una persona: visitas del día y última atención.
 *  v6 — Modo Súper: instante de la última activación (0 = nunca). La duración y el enfriamiento
 *       se calculan con PetBalance, así que cambiar esos valores no obliga a migrar datos.
 *  v7 — Mundo social (Pixel Chix): identificador público único desacoplado (`personajeId`) y
 *       personalidad innata (`temperamento`), con valores por defecto para migración segura.
 */
@Serializable
data class PetEntity(
    val ownerId: String,
    val name: String,
    val petType: PetType = PetType.GATO,
    val stage: PetStage = PetStage.HUEVO,

    // ── Necesidades (0..100) ─────────────────────────────────────────────────
    val hunger: Int = 80,
    val happiness: Int = 80,
    val energy: Int = 80,
    val hygiene: Int = 80,
    val health: Int = 100,

    // ── Estados ──────────────────────────────────────────────────────────────
    val isSleeping: Boolean = false,
    val isSick: Boolean = false,
    /** "Fuera de línea": la salud llegó a 0. Se arregla con Reiniciar. */
    val isCorrupted: Boolean = false,

    // ── Tiempo ───────────────────────────────────────────────────────────────
    val birthMillis: Long = System.currentTimeMillis(),
    val lastTickMillis: Long = System.currentTimeMillis(),

    // ── Progreso ─────────────────────────────────────────────────────────────
    @SerialName("neuralExp") val experiencia: Int = 0,
    @SerialName("neuralLevel") val nivel: Int = 1,
    @SerialName("isOverclocked") val modoTurbo: Boolean = false,
    @SerialName("overclockExpiresMillis") val turboHastaMillis: Long = 0L,
    @SerialName("darkEnergy") val chispas: Int = 0,

    // ── Cuidado realista (v2) ────────────────────────────────────────────────
    val ultimoAlimentadoMillis: Long = 0L,
    val ultimoLimpiadoMillis: Long = 0L,
    val ultimoJugadoMillis: Long = 0L,
    val vecesAlimentadoHoy: Int = 0,
    val vecesLimpiadoHoy: Int = 0,
    val vecesJugadoHoy: Int = 0,
    val lastDayResetMillis: Long = 0L,
    /** Rasgos desbloqueados, separados por comas. Ver [RasgoPet]. */
    val rasgosDesbloqueados: String = "",
    val puntosIndigestion: Int = 0,

    // ── v3 ───────────────────────────────────────────────────────────────────
    val ultimaCariciaMillis: Long = 0L,
    val cariciasHoy: Int = 0,
    val chispasGanadasHoy: Int = 0,
    val resto: PetResto = PetResto(),

    /** Instante en que se durmió (0 si está despierta). Sirve para premiar el descanso con XP doble. */
    val duermeDesdeMillis: Long = 0L,

    // ── v4 ───────────────────────────────────────────────────────────────────
    /** Medicinas que le han enviado otras personas desde el mapa. Curar gasta una. */
    val medicinas: Int = 0,

    // ── v5 ───────────────────────────────────────────────────────────────────
    /** Visitas de hoy (cuidados separados por 45 min o más). Se reinicia a las 4:00. */
    val visitasHoy: Int = 0,
    /** Instante del último cuidado, para saber si el siguiente cuenta como visita nueva. */
    val ultimaAtencionMillis: Long = 0L,

    // ── v6 ───────────────────────────────────────────────────────────────────
    /**
     * Instante en que se activó por última vez el Modo Súper (0 = nunca). Con él se calculan la
     * duración ([PetBalance.SUPER_DURATION_MILLIS]) y el enfriamiento ([PetBalance.SUPER_COOLDOWN_MILLIS]).
     * Ver feature/tamagotchi/logic/ModoSuper.kt.
     */
    val superDesdeMillis: Long = 0L,

    // ── v7 ───────────────────────────────────────────────────────────────────
    /** Identificador público único del personaje para visitas y ambientes compartidos. */
    val personajeId: String = "",
    /** Temperamento base que modula afinidades y compatibilidad social. */
    val temperamento: Temperamento = Temperamento.CURIOSO,

    val schemaVersion: Int = 7
)

package com.renea.arete_social.feature.miausnake.social

/**
 * Mejor puntuación verificada por día UTC del reto diario. Inmutable; conserva solo los últimos
 * [MAX_DAYS] días. Texto: una línea `díaUTC=puntos`.
 */
class DailyBook private constructor(private val days: Map<Long, Int>) {
    fun best(day: Long): Int? = days[day]

    /** Guarda [score] si mejora lo que ya había ese día. Puntuaciones negativas se ignoran. */
    fun record(day: Long, score: Int): DailyBook {
        if (score < 0 || day < 0) return this
        if ((days[day] ?: -1) >= score) return this
        return of(days + (day to score))
    }

    fun toText(): String = days.entries.sortedByDescending { it.key }.joinToString("\n") { "${it.key}=${it.value}" }

    companion object {
        const val MAX_DAYS = 60
        val EMPTY = DailyBook(emptyMap())

        private fun of(m: Map<Long, Int>) =
            DailyBook(m.entries.sortedByDescending { it.key }.take(MAX_DAYS).associate { it.key to it.value })

        fun fromText(text: String?): DailyBook {
            if (text.isNullOrBlank()) return EMPTY
            val m = HashMap<Long, Int>()
            for (line in text.lineSequence()) {
                val i = line.indexOf('=')
                if (i <= 0) continue
                val d = line.substring(0, i).trim().toLongOrNull() ?: continue
                val s = line.substring(i + 1).trim().toIntOrNull() ?: continue
                if (d < 0 || s < 0) continue
                m[d] = maxOf(s, m[d] ?: 0)
            }
            return of(m)
        }
    }
}

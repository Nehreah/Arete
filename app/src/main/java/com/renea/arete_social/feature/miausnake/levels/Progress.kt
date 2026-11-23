package com.renea.arete_social.feature.miausnake.levels

/** Registro de récord en un mapa y modo determinado. */
data class LevelRecord(
    val bestScore: Int = 0,
    val bestTimeMs: Long? = null,
    val medal: Medal = Medal.NONE
)

/** Resultado de una actualización de récord. */
data class RecordUpdate(
    val spec: LevelSpec,
    val mode: GameMode,
    val score: Int,
    val won: Boolean,
    val elapsedMs: Long,
    val newBestScore: Boolean = false,
    val newBestTime: Boolean = false,
    val newMedal: Medal = Medal.NONE
)

/** Libro inmutable de récords y progreso de Miau Snake. */
class ProgressBook(
    private val records: Map<String, LevelRecord> = emptyMap()
) {
    fun get(spec: LevelSpec, mode: GameMode): LevelRecord? = records[key(spec.id, mode)]

    fun medal(spec: LevelSpec): Medal = get(spec, GameMode.META)?.medal ?: Medal.NONE

    fun record(
        spec: LevelSpec,
        mode: GameMode,
        score: Int,
        won: Boolean,
        elapsedMs: Long
    ): Pair<ProgressBook, RecordUpdate?> {
        val k = key(spec.id, mode)
        val current = records[k] ?: LevelRecord()

        var newBestScore = false
        var newBestTime = false
        var earnedMedal = current.medal

        if (score > current.bestScore) {
            newBestScore = true
        }

        if (mode == GameMode.META && won) {
            val par = spec.parMs
            val currentMedal = current.medal
            val candidateMedal = when {
                elapsedMs <= (par * 0.9).toLong() -> Medal.GOLD
                elapsedMs <= par -> Medal.SILVER
                else -> Medal.BRONZE
            }
            if (candidateMedal.ordinal > currentMedal.ordinal) {
                earnedMedal = candidateMedal
            }
            if (current.bestTimeMs == null || elapsedMs < current.bestTimeMs) {
                newBestTime = true
            }
        }

        val bestScore = maxOf(current.bestScore, score)
        val bestTime = if (newBestTime) elapsedMs else current.bestTimeMs

        val updated = current.copy(
            bestScore = bestScore,
            bestTimeMs = bestTime,
            medal = earnedMedal
        )

        val updateEvent = RecordUpdate(
            spec = spec,
            mode = mode,
            score = score,
            won = won,
            elapsedMs = elapsedMs,
            newBestScore = newBestScore,
            newBestTime = newBestTime,
            newMedal = if (earnedMedal.ordinal > current.medal.ordinal) earnedMedal else Medal.NONE
        )

        val newRecords = records.toMutableMap()
        newRecords[k] = updated
        return Pair(ProgressBook(newRecords), updateEvent)
    }

    fun toText(): String = buildString {
        for ((k, rec) in records) {
            append(k).append('|')
            append(rec.bestScore).append('|')
            append(rec.bestTimeMs ?: -1L).append('|')
            append(rec.medal.name).append('\n')
        }
    }

    companion object {
        val EMPTY = ProgressBook(emptyMap())

        fun fromText(text: String?): ProgressBook {
            if (text.isNullOrBlank()) return EMPTY
            val map = HashMap<String, LevelRecord>()
            for (line in text.lineSequence()) {
                val parts = line.split('|')
                if (parts.size >= 4) {
                    val k = parts[0].trim()
                    val score = parts[1].trim().toIntOrNull() ?: 0
                    val timeMs = parts[2].trim().toLongOrNull()?.takeIf { it >= 0 }
                    val medal = runCatching { Medal.valueOf(parts[3].trim()) }.getOrDefault(Medal.NONE)
                    map[k] = LevelRecord(bestScore = score, bestTimeMs = timeMs, medal = medal)
                }
            }
            return ProgressBook(map)
        }

        private fun key(mapId: String, mode: GameMode) = "${mapId}_${mode.code}"
    }
}

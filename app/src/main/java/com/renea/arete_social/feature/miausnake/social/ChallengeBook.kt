package com.renea.arete_social.feature.miausnake.social

enum class ChallengeRole { SENT, RECEIVED }

/**
 * Un reto guardado. [packet] es el texto `MIAUR1:…` completo (autosuficiente: de él se vuelve a
 * sacar la repetición para jugar contra su fantasma). [myScore] es la mejor puntuación propia
 * VERIFICADA en ese reto.
 */
data class ChallengeEntry(
    val id: String,
    val role: ChallengeRole,
    val packet: String,
    val myScore: Int?,
    val atDay: Long
)

/**
 * Libro de retos: inmutable, idempotente (el mismo paquete dos veces es una sola entrada) y acotado
 * (los más viejos salen). Formato de texto, una línea por entrada:
 * `id|S o R|puntos o -|díaUTC|paquete`. Lo corrupto se ignora; el id debe coincidir con el paquete.
 */
class ChallengeBook private constructor(val entries: List<ChallengeEntry>) {

    fun get(id: String): ChallengeEntry? = entries.firstOrNull { it.id == id }
    fun contains(id: String): Boolean = get(id) != null

    /** Añade al principio. Si ya existía (mismo paquete) no cambia nada. Paquetes enormes no se guardan. */
    fun add(role: ChallengeRole, packet: String, myScore: Int?, atDay: Long): ChallengeBook {
        if (!canStore(packet)) return this
        val id = idOf(packet)
        if (contains(id)) return this
        val e = ChallengeEntry(id, role, packet, myScore?.takeIf { it >= 0 }, atDay)
        return ChallengeBook((listOf(e) + entries).take(MAX_ENTRIES))
    }

    /** Guarda la mejor puntuación propia; una peor no la pisa. */
    fun withMyScore(id: String, score: Int): ChallengeBook {
        if (score < 0) return this
        val cur = get(id) ?: return this
        if ((cur.myScore ?: -1) >= score) return this
        return ChallengeBook(entries.map { if (it.id == id) it.copy(myScore = score) else it })
    }

    fun remove(id: String): ChallengeBook = ChallengeBook(entries.filterNot { it.id == id })

    fun toText(): String = entries.joinToString("\n") { e ->
        listOf(e.id, if (e.role == ChallengeRole.SENT) "S" else "R", e.myScore?.toString() ?: "-", e.atDay.toString(), e.packet)
            .joinToString("|")
    }

    companion object {
        const val MAX_ENTRIES = 20
        const val MAX_STORED_CHARS = 16_000
        val EMPTY = ChallengeBook(emptyList())

        fun canStore(packet: String): Boolean =
            packet.length <= MAX_STORED_CHARS && packet.startsWith("MIAUR1:") &&
                packet.none { it == '|' || it.isWhitespace() }

        fun idOf(packet: String): String = Ids.of(packet)

        fun fromText(text: String?): ChallengeBook {
            if (text.isNullOrBlank()) return EMPTY
            val out = ArrayList<ChallengeEntry>()
            val seen = HashSet<String>()
            for (line in text.lineSequence()) {
                if (out.size >= MAX_ENTRIES) break
                val p = line.split('|')
                if (p.size != 5) continue
                val role = when (p[1]) {
                    "S" -> ChallengeRole.SENT
                    "R" -> ChallengeRole.RECEIVED
                    else -> continue
                }
                val score: Int? = if (p[2] == "-") null else (p[2].toIntOrNull()?.takeIf { it >= 0 } ?: continue)
                val day = p[3].toLongOrNull() ?: continue
                val packet = p[4]
                if (!canStore(packet) || p[0] != idOf(packet) || !seen.add(p[0])) continue
                out.add(ChallengeEntry(p[0], role, packet, score, day))
            }
            return ChallengeBook(out)
        }
    }
}

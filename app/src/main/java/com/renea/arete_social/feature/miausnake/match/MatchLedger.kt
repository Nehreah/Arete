package com.renea.arete_social.feature.miausnake.match

enum class MatchOutcome(val code: String) {
    WON("W"), LOST("L"), TIE("T"), PEER_INVALID("X"), NO_RESULT("N");

    companion object {
        fun fromCode(c: String): MatchOutcome? = entries.firstOrNull { it.code == c }
    }
}

/** Un duelo cercano ya jugado hasta el final por mí. El resultado del rival se re-simuló antes de contarlo. */
data class MatchRecord(
    val id: String,
    val atDay: Long,
    val peerId: String,
    val peerName: String,
    val mapNo: Int,
    val mode: Char,
    val myScore: Int?,
    val peerScore: Int?,
    val outcome: MatchOutcome
)

/** Historial acotado e idempotente (una sesión = una entrada). Una línea por entrada; lo corrupto se ignora. */
class MatchLedger private constructor(val entries: List<MatchRecord>) {

    fun contains(id: String): Boolean = entries.any { it.id == id }

    fun add(r: MatchRecord): MatchLedger =
        if (contains(r.id) || !valid(r)) this else MatchLedger((listOf(r) + entries).take(MAX_ENTRIES))

    fun wins(): Int = entries.count { it.outcome == MatchOutcome.WON }
    fun losses(): Int = entries.count { it.outcome == MatchOutcome.LOST }
    fun ties(): Int = entries.count { it.outcome == MatchOutcome.TIE }

    fun toText(): String = entries.joinToString("\n") { r ->
        listOf(
            r.id, r.atDay, r.peerId, r.peerName, r.mapNo, r.mode, r.myScore?.toString() ?: "-",
            r.peerScore?.toString() ?: "-", r.outcome.code
        ).joinToString("|")
    }

    companion object {
        const val MAX_ENTRIES = 30
        val EMPTY = MatchLedger(emptyList())

        private fun valid(r: MatchRecord): Boolean =
            MatchLimits.ID.matches(r.id) && MatchLimits.PEER.matches(r.peerId) &&
                r.peerName == MatchText.cleanName(r.peerName) && r.mapNo in 1..20 && (r.mode == 'I' || r.mode == 'M') &&
                (r.myScore ?: 0) >= 0 && (r.peerScore ?: 0) >= 0

        fun fromText(text: String?): MatchLedger {
            if (text.isNullOrBlank()) return EMPTY
            val out = ArrayList<MatchRecord>()
            val seen = HashSet<String>()
            for (line in text.lineSequence()) {
                if (out.size >= MAX_ENTRIES) break
                val p = line.split('|')
                if (p.size != 9) continue
                val day = p[1].toLongOrNull() ?: continue
                val map = p[4].toIntOrNull() ?: continue
                if (p[5].length != 1) continue
                val my: Int? = if (p[6] == "-") null else (p[6].toIntOrNull() ?: continue)
                val their: Int? = if (p[7] == "-") null else (p[7].toIntOrNull() ?: continue)
                val outcome = MatchOutcome.fromCode(p[8]) ?: continue
                val r = MatchRecord(p[0], day, p[2], p[3], map, p[5][0], my, their, outcome)
                if (valid(r) && seen.add(r.id)) out.add(r)
            }
            return MatchLedger(out)
        }
    }
}

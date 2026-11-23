package com.renea.arete_social.feature.miausnake.core

/** MIAU1-17-I-7F3A9C -> reglas 1, mapa 17, modo infinito, semilla de 24 bits. */
data class Challenge(val rules: Int, val mapNo: Int, val mode: Char, val seed: Long) {
    fun toCode(): String =
        "MIAU$rules-$mapNo-$mode-" + (seed and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')

    companion object {
        private val RX = Regex("MIAU(\\d{1,3})-(\\d{1,2})-([IM])-([0-9A-F]{6})")

        fun parse(code: String): Challenge? {
            val g = RX.matchEntire(code.trim())?.groupValues ?: return null
            val rules = g[1].toIntOrNull() ?: return null
            val map = g[2].toIntOrNull() ?: return null
            if (map !in 1..20) return null
            return Challenge(rules, map, g[3][0], g[4].toLong(16))
        }

        /** Reto diario: el día UTC decide mapa y semilla. Offline y reproducible. */
        fun daily(nowMs: Long): Challenge {
            val h = Xorshift64Star.next((nowMs / 86_400_000L) xor GOLDEN)
            return Challenge(RULES_VERSION, ((h ushr 16) % 20).toInt() + 1, 'I', h and 0xFFFFFFL)
        }
    }
}

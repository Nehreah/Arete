package com.renea.arete_social.feature.miausnake.core

import java.io.ByteArrayOutputStream

const val RULES_VERSION = 1

/** Tick en que el giro se CONSUME (no el de la pulsación). */
data class TurnInput(val tick: Long, val dir: Direction)

data class Replay(
    val levelId: String,
    val levelVersion: Int,
    val rulesVersion: Int,
    val seed: Long,
    val inputs: List<TurnInput>
)

/**
 * Formato compacto: por giro, varint de ((delta de tick) << 2 | ordinal de dirección).
 * El decodificador no confía en datos externos: acota bytes, giros y ticks.
 */
object ReplayCodec {
    const val MAX_BYTES = 64 * 1024
    const val MAX_TURNS = 20_000
    const val MAX_TICK = 200_000L

    fun encode(inputs: List<TurnInput>): ByteArray {
        val out = ByteArrayOutputStream()
        var last = 0L
        for (i in inputs) {
            require(i.tick >= last) { "giros desordenados" }
            writeVarint(out, ((i.tick - last) shl 2) or i.dir.ordinal.toLong())
            last = i.tick
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, maxTurns: Int = MAX_TURNS, maxTick: Long = MAX_TICK): List<TurnInput>? {
        if (bytes.size > MAX_BYTES) return null
        val res = ArrayList<TurnInput>()
        var i = 0
        var last = 0L
        while (i < bytes.size) {
            var v = 0L
            var shift = 0
            while (true) {
                if (i >= bytes.size || shift > 56) return null
                val b = bytes[i++].toInt() and 0xFF
                v = v or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) break
                shift += 7
            }
            if (v < 0L) return null
            last += v ushr 2
            if (last < 0L || last > maxTick) return null
            res.add(TurnInput(last, Direction.entries[(v and 3L).toInt()]))
            if (res.size > maxTurns) return null
        }
        return res
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v >= 0x80L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }
}

/**
 * Re-simula la repetición. true solo si es legal, la partida TERMINÓ (victoria o derrota)
 * y la puntuación coincide. Prueba consistencia con las reglas, no que la jugó una persona.
 */
fun verify(r: Replay, lv: Level, claimedScore: Int, maxTicks: Long = ReplayCodec.MAX_TICK): Boolean {
    if (r.levelId != lv.id || r.levelVersion != lv.version || r.rulesVersion != RULES_VERSION) return false
    var s = GameEngine.initial(lv, r.seed)
    val it = r.inputs.iterator()
    var turn = if (it.hasNext()) it.next() else null
    while (s.status == GameStatus.Running && s.tick < maxTicks) {
        val t = turn
        if (t != null) {
            if (t.tick < s.tick) return false                       // orden inválido
            if (t.tick == s.tick) {
                if (t.dir == s.direction.opposite || t.dir == s.direction) return false // giro ilegal o nulo
                s = s.copy(queue = listOf(t.dir))
                turn = if (it.hasNext()) it.next() else null
            }
        }
        s = s.applyTick(lv)
    }
    return turn == null && s.isFinished && s.score == claimedScore
}

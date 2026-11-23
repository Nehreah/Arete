package com.renea.arete_social.feature.miausnake.core

/** 0x9E3779B97F4A7C15 como Long con signo. */
const val GOLDEN = -7046029254386353131L

/** xorshift64*: el estado vive en [GameState]; nunca se usa java.util.Random ni kotlin.random. */
object Xorshift64Star {
    fun seed(s: Long): Long = if (s == 0L) GOLDEN else s

    fun next(s: Long): Long {
        var x = s
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        return x
    }

    fun output(s: Long): Long = s * 2685821657736338717L
}

/** Elige entre celdas libres (sin rechazo): un tablero casi lleno nunca entra en bucle. */
fun spawnFood(free: List<Cell>, rng: Long): Pair<Cell?, Long> {
    if (free.isEmpty()) return null to rng
    val s = Xorshift64Star.next(rng)
    val n = free.size.toLong()
    val idx = (((Xorshift64Star.output(s) % n) + n) % n).toInt()
    return free[idx] to s
}

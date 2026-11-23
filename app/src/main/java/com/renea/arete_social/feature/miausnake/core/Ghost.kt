package com.renea.arete_social.feature.miausnake.core

/**
 * La repetición de un rival convertida en un recorrido que se puede dibujar como sombra.
 *
 * Idea clave: el cuerpo de la serpiente en el tick t son exactamente las últimas `largo(t)` celdas
 * que pisó la cabeza. Por eso basta guardar el camino de la cabeza, el largo en cada tick y el
 * tiempo virtual acumulado (no hace falta guardar la serpiente entera por tick).
 *
 * El fantasma avanza por TIEMPO VIRTUAL ([tickAt]): si el rival fue más rápido, su sombra va por delante.
 */
class GhostTrack internal constructor(
    val cols: Int,
    val rows: Int,
    /** Celdas como y*cols+x: cola(2), cola(1), cabeza(0), cabeza(1)… */
    private val path: IntArray,
    private val lengths: IntArray,
    /** Milisegundos virtuales acumulados al llegar a cada tick (creciente). */
    private val elapsed: LongArray,
    val finalStatus: GameStatus
) {
    /** Último tick del rival. */
    val ticks: Int get() = lengths.size - 1
    val totalMs: Long get() = elapsed[elapsed.size - 1]

    /** Mayor tick cuyo tiempo acumulado no supera [ms]. */
    fun tickAt(ms: Long): Int {
        if (ms <= 0L) return 0
        var lo = 0
        var hi = elapsed.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (elapsed[mid] <= ms) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * Escribe en [out] las celdas de la serpiente en [tick], cabeza primero, y devuelve cuántas.
     * Si [out] es corto se escribe solo lo que quepa. No reserva memoria.
     */
    fun fill(tick: Int, out: IntArray): Int {
        val t = tick.coerceIn(0, ticks)
        val n = minOf(lengths[t], out.size)
        val head = t + 2
        for (k in 0 until n) out[k] = path[head - k]
        return n
    }
}

private class IntBuf(cap: Int = 256) {
    var a = IntArray(cap)
    var n = 0
    fun add(v: Int) {
        if (n == a.size) a = a.copyOf(a.size * 2)
        a[n++] = v
    }
}

private class LongBuf(cap: Int = 256) {
    var a = LongArray(cap)
    var n = 0
    fun add(v: Long) {
        if (n == a.size) a = a.copyOf(a.size * 2)
        a[n++] = v
    }
}

/**
 * Construye el fantasma re-simulando [r]. null si la repetición no es legal o no terminó
 * (mismas reglas que [resimulate]). Es CPU: llamar fuera del hilo principal.
 */
fun buildGhost(r: Replay, lv: Level, maxTicks: Long = ReplayCodec.MAX_TICK): GhostTrack? {
    val path = IntBuf()
    val lengths = IntBuf()
    val elapsed = LongBuf()
    fun idx(c: Cell) = c.y * lv.cols + c.x
    var first = true
    val fin = resimulate(r, lv, maxTicks) { s ->
        if (first) {
            first = false
            path.add(idx(s.snake[2])); path.add(idx(s.snake[1]))
        }
        path.add(idx(s.snake[0]))
        lengths.add(s.snake.size)
        elapsed.add(s.elapsedMs)
    } ?: return null
    return GhostTrack(
        lv.cols, lv.rows,
        path.a.copyOf(path.n), lengths.a.copyOf(lengths.n), elapsed.a.copyOf(elapsed.n),
        fin.status
    )
}

package com.renea.arete_social.feature.miausnake.core

import java.util.Locale

private const val DAY_MS = 86_400_000L

/** Día UTC (días desde 1970-01-01). Es el mismo corte que usa [Challenge.daily]. */
fun utcDay(nowMs: Long): Long = Math.floorDiv(nowMs, DAY_MS)

/** (año, mes, día) del calendario civil para un día UTC. Sin java.time: funciona en cualquier API. */
fun civilFromDay(day: Long): Triple<Int, Int, Int> {
    val z = day + 719_468L
    val era = Math.floorDiv(z, 146_097L)
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    return Triple((y + if (m <= 2L) 1L else 0L).toInt(), m.toInt(), d.toInt())
}

/** «2027-01-15». */
fun formatUtcDay(day: Long): String {
    val (y, m, d) = civilFromDay(day)
    return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d)
}

/** El reto del día para [nowMs]. Misma fecha UTC = mismo reto en todos los teléfonos, sin conexión. */
fun dailyChallengeFor(nowMs: Long): Challenge = Challenge.daily(nowMs)

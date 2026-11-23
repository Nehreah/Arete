package com.renea.arete_social.domain.util

/**
 * Fuente de la hora actual.
 *
 * Existe para NO llamar a `System.currentTimeMillis()` por todo el código de descubrimiento y de
 * los súper poderes de Michi: los tests pueden pasar un reloj controlado y, el día que se quiera
 * confiar en la hora del servidor (decisión S2 del plan, pospuesta), basta con dar otra
 * implementación sin tocar las reglas.
 */
fun interface Reloj {
    fun ahora(): Long

    companion object {
        /** La hora del teléfono. */
        val Sistema: Reloj = Reloj { System.currentTimeMillis() }
    }
}

package com.renea.arete_social.feature.tamagotchi.model

import kotlinx.serialization.Serializable

/**
 * Sesiones de «juego externo» (hoy: Miau Snake) que quieren contar como la actividad JUGAR de Michi.
 *
 * Una sesión se ABRE al empezar una partida (sin consumir nada) y se CIERRA una sola vez:
 *  - RECLAMADA: se aplicó la actividad de jugar a la mascota (una única mutación).
 *  - DESCARTADA: se cerró sin recompensa (abandono, partida demasiado corta, Michi ya no podía jugar…).
 * Una sesión que no está ABIERTA no puede volver a cerrarse: así no hay doble recompensa.
 */
@Serializable
enum class EstadoSesionJuego { ABIERTA, RECLAMADA, DESCARTADA }

@Serializable
data class SesionJuego(
    val id: String,
    val estado: EstadoSesionJuego = EstadoSesionJuego.ABIERTA,
    val abiertaMillis: Long,
    val cerradaMillis: Long = 0L
)

/** Registro persistido (acotado) de las sesiones de juego de una mascota. */
@Serializable
data class LibroSesionesJuego(val sesiones: List<SesionJuego> = emptyList()) {
    fun buscar(id: String): SesionJuego? = sesiones.firstOrNull { it.id == id }
}

/**
 * Lo que el puente pide aplicar al cerrar una sesión. La mascota NO decide la política de cada
 * juego: solo la ejecuta dentro de su propia transacción.
 *
 * @param aplicarJugar si es true se ejecuta exactamente la misma actividad que «Automático»
 *   ([com.renea.arete_social.feature.tamagotchi.logic.jugar]): mismos requisitos, coste y espera.
 * @param xpExtra experiencia adicional (solo si la actividad se aplicó y dentro del límite diario).
 */
data class RecompensaJuego(val aplicarJugar: Boolean, val xpExtra: Int = 0) {
    init {
        require(xpExtra >= 0) { "xpExtra negativo" }
        require(aplicarJugar || xpExtra == 0) { "sin actividad no puede haber experiencia extra" }
    }

    companion object {
        /** Cerrar la sesión sin recompensa ni coste. */
        val Ninguna = RecompensaJuego(aplicarJugar = false)
    }
}

sealed interface AperturaSesionJuego {
    /** Sesión registrada. No se consumió nada. */
    data object Abierta : AperturaSesionJuego

    /** Michi no puede jugar ahora (dormida, sin energía, en espera…). No se creó la sesión. */
    data class Rechazada(val motivo: ResultadoCuidado) : AperturaSesionJuego

    /** El id ya existía (abierta o cerrada): nunca se reutiliza. */
    data object IdRepetido : AperturaSesionJuego

    /** Este repositorio no implementa sesiones de juego. */
    data object NoSoportado : AperturaSesionJuego
}

sealed interface CierreSesionJuego {
    /** Se aplicó la actividad de jugar una vez. [xpExtra] es la experiencia extra realmente concedida. */
    data class Aplicado(val xpExtra: Int) : CierreSesionJuego

    /** Al cerrar, Michi ya no podía jugar: no se aplicó nada y la sesión quedó descartada. */
    data class Rechazado(val motivo: ResultadoCuidado) : CierreSesionJuego

    /** Se cerró sin recompensa (la política así lo pidió o la sesión había caducado). */
    data object Descartado : CierreSesionJuego

    /** Esa sesión ya se había cerrado: no se tocó nada. */
    data object YaCerrada : CierreSesionJuego

    /** Nadie abrió esa sesión (o ya se purgó): no se puede reclamar. */
    data object SesionDesconocida : CierreSesionJuego

    /** Este repositorio no implementa sesiones de juego. */
    data object NoSoportado : CierreSesionJuego
}

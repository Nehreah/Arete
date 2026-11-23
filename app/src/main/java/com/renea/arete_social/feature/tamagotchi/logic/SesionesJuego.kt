package com.renea.arete_social.feature.tamagotchi.logic

import com.renea.arete_social.feature.tamagotchi.model.AperturaSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.EstadoSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.LibroSesionesJuego
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import com.renea.arete_social.feature.tamagotchi.model.SesionJuego

/**
 * Lógica PURA (sin Android ni E/S) del cierre transaccional de sesiones de juego externo.
 * El repositorio la ejecuta dentro de su `Mutex`, con la mascota ya puesta al día, y guarda a la
 * vez la mascota y el libro: o se aplican las dos cosas o ninguna.
 *
 * No modifica [PetSimulation]: la actividad se aplica llamando a la MISMA [jugar] que «Automático».
 */
object SesionesJuego {
    /** Sesiones recordadas por mascota (las más antiguas se olvidan primero). */
    const val MAX_SESIONES = 64

    /** Una sesión abierta que nadie cerró en este tiempo ya no se puede reclamar. */
    const val ABIERTA_CADUCA_MILLIS = 24 * 3_600_000L

    /** Tope de seguridad de la experiencia extra por sesión (la política de cada juego va por debajo). */
    const val XP_EXTRA_MAXIMO = 50

    data class Apertura(val libro: LibroSesionesJuego, val resultado: AperturaSesionJuego)

    data class Cierre(val pet: PetEntity, val libro: LibroSesionesJuego, val resultado: CierreSesionJuego)

    /**
     * Abre una sesión SIN consumir nada: solo comprueba, con un ensayo de [jugar] sobre una copia,
     * que Michi puede jugar ahora. Si no puede, no se crea la sesión y se devuelve el motivo.
     * Abrir una sesión nueva descarta cualquier otra que siguiera abierta (solo hay una a la vez).
     */
    fun abrir(pet: PetEntity, libro: LibroSesionesJuego, sesionId: String, now: Long): Apertura {
        require(sesionId.isNotBlank()) { "sesionId vacío" }
        if (libro.buscar(sesionId) != null) return Apertura(libro, AperturaSesionJuego.IdRepetido)

        val ensayo = pet.jugar(now)
        if (!ensayo.resultado.exito) {
            return Apertura(libro, AperturaSesionJuego.Rechazada(ensayo.resultado))
        }

        val sinAbiertas = libro.sesiones.map {
            if (it.estado == EstadoSesionJuego.ABIERTA) it.copy(estado = EstadoSesionJuego.DESCARTADA, cerradaMillis = now) else it
        }
        val nuevas = (sinAbiertas + SesionJuego(id = sesionId, abiertaMillis = now)).takeLast(MAX_SESIONES)
        return Apertura(LibroSesionesJuego(nuevas), AperturaSesionJuego.Abierta)
    }

    /**
     * Cierra la sesión UNA sola vez. Orden: sesión conocida → todavía abierta → no caducada →
     * ¿hay actividad que aplicar? → Michi puede jugar ahora → una única mutación y marca RECLAMADA.
     * Cualquier otro camino deja a la mascota intacta.
     */
    fun cerrar(pet: PetEntity, libro: LibroSesionesJuego, sesionId: String, recompensa: RecompensaJuego, now: Long): Cierre {
        val sesion = libro.buscar(sesionId) ?: return Cierre(pet, libro, CierreSesionJuego.SesionDesconocida)
        if (sesion.estado != EstadoSesionJuego.ABIERTA) return Cierre(pet, libro, CierreSesionJuego.YaCerrada)

        fun conEstado(estado: EstadoSesionJuego) = libro.copy(
            sesiones = libro.sesiones.map { if (it.id == sesionId) it.copy(estado = estado, cerradaMillis = now) else it }
        )

        if (now - sesion.abiertaMillis > ABIERTA_CADUCA_MILLIS || !recompensa.aplicarJugar) {
            return Cierre(pet, conEstado(EstadoSesionJuego.DESCARTADA), CierreSesionJuego.Descartado)
        }

        val intento = pet.jugar(now)
        if (!intento.resultado.exito) {
            return Cierre(pet, conEstado(EstadoSesionJuego.DESCARTADA), CierreSesionJuego.Rechazado(intento.resultado))
        }

        // Pasado el límite diario la actividad rinde menos (ver PetSimulation): el extra tampoco se acumula.
        val dentroDelLimite = intento.pet.vecesJugadoHoy <= PetBalance.MAX_PLAYS_PER_DAY
        val extra = if (dentroDelLimite) recompensa.xpExtra.coerceAtMost(XP_EXTRA_MAXIMO) else 0
        val nueva = if (extra > 0) intento.pet.ganarXp(extra, now) else intento.pet
        return Cierre(nueva, conEstado(EstadoSesionJuego.RECLAMADA), CierreSesionJuego.Aplicado(extra))
    }
}

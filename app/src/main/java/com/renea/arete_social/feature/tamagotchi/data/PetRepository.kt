package com.renea.arete_social.feature.tamagotchi.data

import com.renea.arete_social.feature.tamagotchi.model.AperturaSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.flow.Flow

/**
 * Punto único de acceso a los datos de la mascota.
 * Cada acción devuelve un [ResultadoCuidado] para que la pantalla pueda explicar
 * por qué algo no se pudo hacer (en vez de fallar en silencio).
 */
interface PetRepository {
    fun observePet(ownerId: String): Flow<PetEntity?>
    suspend fun ensurePetExists(ownerId: String, name: String, type: PetType)

    /** Pone la mascota al día con el tiempo real transcurrido. */
    suspend fun tick(ownerId: String)

    suspend fun feed(ownerId: String, food: FoodType): ResultadoCuidado
    suspend fun play(ownerId: String): ResultadoCuidado
    suspend fun clean(ownerId: String): ResultadoCuidado
    suspend fun heal(ownerId: String): ResultadoCuidado
    suspend fun toggleSleep(ownerId: String): ResultadoCuidado
    suspend fun cuddle(ownerId: String): ResultadoCuidado
    suspend fun reboot(ownerId: String): ResultadoCuidado

    /** Cambia el nombre de la mascota. */
    suspend fun rename(ownerId: String, name: String): ResultadoCuidado

    /** Suma chispas que otra persona envió a esta mascota. */
    suspend fun receiveSparks(ownerId: String, amount: Int): ResultadoCuidado

    /** Guarda una medicina que otra persona envió (la mascota solo se cura si tiene una). */
    suspend fun receiveMedicine(ownerId: String): ResultadoCuidado

    /** Suma una chispa por encontrarte con un amigo (con tope diario). */
    suspend fun earnSparkFromFriend(ownerId: String): ResultadoCuidado

    /** Descuenta chispas que se envían a otra persona desde el mapa. */
    suspend fun giftSparks(ownerId: String, amount: Int): ResultadoCuidado

    /** Actualiza directamente el estado persistente de la mascota. */
    suspend fun actualizarPet(ownerId: String, pet: PetEntity) {}

    // ── Juegos externos (Miau Snake, Fase 3) ─────────────────────────────────
    // Valores por defecto «no soportado» para no obligar a otras implementaciones (p. ej. fakes de
    // test) a cambiar: nunca aplican nada en silencio. «Automático» NO usa estas dos funciones.

    /**
     * Registra el inicio de una partida de un juego externo SIN consumir nada. Solo prueba si Michi
     * puede jugar ahora; si no, devuelve el motivo y no crea la sesión.
     */
    suspend fun abrirSesionJuego(ownerId: String, sesionId: String): AperturaSesionJuego =
        AperturaSesionJuego.NoSoportado

    /**
     * Cierra la sesión exactamente UNA vez, dentro de la misma transacción que guarda a la mascota:
     * comprueba que sigue abierta, aplica como mucho una mutación y la marca como reclamada.
     */
    suspend fun cerrarSesionJuego(ownerId: String, sesionId: String, recompensa: RecompensaJuego): CierreSesionJuego =
        CierreSesionJuego.NoSoportado
}

package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.miausnake.core.Level
import com.renea.arete_social.feature.miausnake.core.Replay
import com.renea.arete_social.feature.miausnake.core.verify
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.model.AperturaSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Lo que la partida informa al terminar o abandonar. */
sealed interface ResultadoSnake {
    /** La partida llegó a victoria o choque. [replay] y [score] permiten verificarla re-simulando. */
    class Terminada(
        val level: Level,
        val replay: Replay,
        val score: Int,
        val bites: Int,
        val won: Boolean
    ) : ResultadoSnake

    /** El jugador salió a mitad de partida (o se perdió la pantalla). Nunca da recompensa. */
    data object Abandonada : ResultadoSnake
}

sealed interface AperturaSnake {
    data class Lista(val sesionId: String) : AperturaSnake

    /** Michi no puede jugar ahora: se explica y se puede volver o jugar SIN recompensa. */
    data class NoDisponible(val motivo: ResultadoCuidado) : AperturaSnake

    /** Fallo al abrir (disco, repositorio…). Se informa; jamás se ejecuta «Automático» en su lugar. */
    data class Error(val causa: Throwable) : AperturaSnake
}

sealed interface CierreSnake {
    /** Se aplicó UNA vez la actividad de jugar. [xpExtra] = experiencia extra concedida por esfuerzo. */
    data class Recompensado(val xpExtra: Int) : CierreSnake
    data object PartidaCorta : CierreSnake
    data object Abandonada : CierreSnake
    data object RepeticionInvalida : CierreSnake

    /** Al cerrar, Michi ya no podía jugar (se durmió, se agotó…): no se aplicó nada. */
    data class MichiNoPudo(val motivo: ResultadoCuidado) : CierreSnake
    data object YaContada : CierreSnake
    data object SesionDesconocida : CierreSnake

    /** Fallo al guardar. La sesión sigue abierta: se puede reintentar con seguridad (es idempotente). */
    data class Error(val causa: Throwable) : CierreSnake

    /** ¿Michi recibió realmente la actividad de jugar? */
    val recompensado: Boolean get() = this is Recompensado
}

/**
 * Puente de dominio entre Miau Snake y Michi. Es la ÚNICA vía por la que una partida puede afectar a
 * la mascota: Snake nunca toca la entidad ni la simulación. Contrato:
 *  - [abrir] no consume nada; si Michi no puede jugar no hay sesión.
 *  - [cerrar] aplica como mucho UNA recompensa por sesión (la sesión se reclama una sola vez).
 *  - Abandonar o terminar «sin valor» cierra la sesión sin recompensa ni coste.
 */
interface MichiPlayBridge {
    suspend fun abrir(): AperturaSnake
    suspend fun cerrar(sesionId: String, resultado: ResultadoSnake): CierreSnake
}

class MichiPlayBridgeLocal(
    private val repository: PetRepository,
    private val ownerId: String,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : MichiPlayBridge {

    private val mutex = Mutex()

    /** Cierres DEFINITIVOS ya resueltos: un segundo cierre (doble toque, recomposición) devuelve lo mismo. */
    private val cierres = HashMap<String, CierreSnake>()

    override suspend fun abrir(): AperturaSnake = mutex.withLock {
        val id = newId()
        try {
            when (val r = repository.abrirSesionJuego(ownerId, id)) {
                AperturaSesionJuego.Abierta -> AperturaSnake.Lista(id)
                is AperturaSesionJuego.Rechazada -> AperturaSnake.NoDisponible(r.motivo)
                AperturaSesionJuego.IdRepetido -> AperturaSnake.Error(IllegalStateException("id de sesión repetido"))
                AperturaSesionJuego.NoSoportado -> AperturaSnake.Error(IllegalStateException("el repositorio no admite sesiones de juego"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AperturaSnake.Error(e)
        }
    }

    override suspend fun cerrar(sesionId: String, resultado: ResultadoSnake): CierreSnake = mutex.withLock {
        cierres[sesionId]?.let { return@withLock it }
        val cierre = try {
            calcular(sesionId, resultado)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock CierreSnake.Error(e) // no se memoriza: reintentar es seguro
        }
        cierres[sesionId] = cierre
        cierre
    }

    private suspend fun calcular(sesionId: String, resultado: ResultadoSnake): CierreSnake {
        // 1) ¿Qué pide la política? Si no hay recompensa, igualmente se cierra la sesión (descartada).
        val sinRecompensa: CierreSnake?
        val recompensa: RecompensaJuego
        when (resultado) {
            ResultadoSnake.Abandonada -> {
                sinRecompensa = CierreSnake.Abandonada
                recompensa = RecompensaJuego.Ninguna
            }
            is ResultadoSnake.Terminada -> {
                // El puente no se fía de la UI: re-simula la repetición (debe estar TERMINADA y coincidir).
                val valida = verify(resultado.replay, resultado.level, resultado.score)
                if (!valida) {
                    sinRecompensa = CierreSnake.RepeticionInvalida
                    recompensa = RecompensaJuego.Ninguna
                } else {
                    recompensa = SnakeRewardPolicy.decidir(resultado.bites, resultado.won)
                    sinRecompensa = if (recompensa.aplicarJugar) null else CierreSnake.PartidaCorta
                }
            }
        }

        // 2) Cierre transaccional en el repositorio de Michi.
        return when (val r = repository.cerrarSesionJuego(ownerId, sesionId, recompensa)) {
            is CierreSesionJuego.Aplicado -> CierreSnake.Recompensado(r.xpExtra)
            is CierreSesionJuego.Rechazado -> CierreSnake.MichiNoPudo(r.motivo)
            CierreSesionJuego.Descartado -> sinRecompensa ?: CierreSnake.SesionDesconocida // caducó sin cerrarse
            CierreSesionJuego.YaCerrada -> CierreSnake.YaContada
            CierreSesionJuego.SesionDesconocida -> CierreSnake.SesionDesconocida
            CierreSesionJuego.NoSoportado -> throw IllegalStateException("el repositorio no admite sesiones de juego")
        }
    }
}

/** Mensaje corto para la pantalla de resultado. No usa nada de Android. */
fun CierreSnake.texto(nombre: String): String = when (this) {
    is CierreSnake.Recompensado ->
        if (xpExtra > 0) "$nombre jugó contigo y está más contenta. ¡Extra de +$xpExtra XP!"
        else "$nombre jugó contigo y está más contenta."
    CierreSnake.PartidaCorta -> "Partida muy corta: con menos de ${SnakeBalance.MIN_BITES} bocados no cuenta como jugar."
    CierreSnake.Abandonada -> "Partida abandonada: no cuenta como jugar."
    CierreSnake.RepeticionInvalida -> "No se pudo validar la partida, así que $nombre no recibe recompensa."
    is CierreSnake.MichiNoPudo -> motivo.texto(nombre) + " Esta partida no cuenta como jugar."
    CierreSnake.YaContada -> "Esta partida ya se había contado."
    CierreSnake.SesionDesconocida -> "No se encontró la sesión de esta partida: no cuenta como jugar."
    is CierreSnake.Error -> "No se pudo guardar el resultado. Inténtalo de nuevo."
}

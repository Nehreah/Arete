package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.miausnake.core.EdgeMode
import com.renea.arete_social.feature.miausnake.core.GameSession
import com.renea.arete_social.feature.miausnake.core.GameStatus
import com.renea.arete_social.feature.miausnake.core.TestLevels
import com.renea.arete_social.feature.miausnake.core.bot.BfsBot
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.SesionesJuego
import com.renea.arete_social.feature.tamagotchi.logic.applyTick
import com.renea.arete_social.feature.tamagotchi.logic.inicioDiaMillis
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.model.AperturaSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.CierreSesionJuego
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.LibroSesionesJuego
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.RecompensaJuego
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** Mascota adulta y descansada a las 05:00 de un día fijo. */
object Fixtures {
    val t0: Long = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 5, 5, 0, 0) }.timeInMillis
    const val HORA = 3_600_000L

    fun adulta(bloque: PetEntity.() -> PetEntity = { this }): PetEntity =
        PetEntity(
            ownerId = "u", name = "Michi", stage = PetStage.ADULTO,
            birthMillis = t0 - PetStage.ADULTO.minAgeMillis - HORA,
            lastTickMillis = t0, lastDayResetMillis = inicioDiaMillis(t0)
        ).bloque()
}

/** Partidas reales (motor + bot) para alimentar al puente con repeticiones verificables. */
object SnakeTestGames {
    val nivelMeta = TestLevels.open(cols = 20, rows = 28, goalBites = 12)
    val nivelInfinito = TestLevels.open(cols = 20, rows = 28, edge = EdgeMode.SOLID, goalBites = null)

    fun terminada(level: com.renea.arete_social.feature.miausnake.core.Level, s: GameSession) =
        ResultadoSnake.Terminada(level, s.replay(), s.state.score, s.state.foodEaten, s.state.status == GameStatus.Won)

    fun victoria(seed: Long = 1L): ResultadoSnake.Terminada {
        val s = BfsBot.play(nivelMeta, seed)
        check(s.state.status == GameStatus.Won)
        return terminada(nivelMeta, s)
    }

    /** El bot come hasta el mínimo del rango y luego choca recto; se busca una semilla con total dentro de [rango]. */
    fun choque(rango: IntRange): ResultadoSnake.Terminada {
        for (seed in 1L..500L) {
            val s = GameSession(nivelInfinito, seed)
            while (!s.state.isFinished && s.state.foodEaten < rango.first) {
                val d = BfsBot.nextDirection(s.state, nivelInfinito)
                if (d != s.state.direction) s.turn(d)
                s.tick()
            }
            while (!s.state.isFinished) s.tick()
            if (s.state.status == GameStatus.GameOver && s.state.foodEaten in rango) return terminada(nivelInfinito, s)
        }
        error("no hay semilla con choque en $rango")
    }
}

/** Repositorio en memoria con la MISMA lógica transaccional que PetRepositoryLocal (Mutex + SesionesJuego). */
class RepoSesionesFake(pet: PetEntity, var ahora: Long) : PetRepository {
    var pet: PetEntity = pet
        private set
    var libro = LibroSesionesJuego()
        private set
    private val mutex = Mutex()
    private val flujo = MutableStateFlow<PetEntity?>(pet)

    var playLlamadas = 0
    var abrirLlamadas = 0
    var cerrarLlamadas = 0
    var fallarAbrir: Throwable? = null
    var fallosCierrePendientes = 0
    var soportaSesiones = true

    fun forzar(nueva: PetEntity) { pet = nueva; flujo.value = nueva }

    private fun alDia() = pet.applyTick(now = ahora)
    private fun guardar(p: PetEntity) { pet = p; flujo.value = p }

    override fun observePet(ownerId: String): Flow<PetEntity?> = flujo
    override suspend fun ensurePetExists(ownerId: String, name: String, type: PetType) {}
    override suspend fun tick(ownerId: String) {}
    override suspend fun play(ownerId: String): ResultadoCuidado = mutex.withLock {
        playLlamadas++
        val r = alDia().jugar(ahora); guardar(r.pet); r.resultado
    }
    override suspend fun abrirSesionJuego(ownerId: String, sesionId: String): AperturaSesionJuego {
        if (!soportaSesiones) return super.abrirSesionJuego(ownerId, sesionId)
        return mutex.withLock {
            abrirLlamadas++
            fallarAbrir?.let { throw it }
            val p = alDia(); val r = SesionesJuego.abrir(p, libro, sesionId, ahora)
            guardar(p); libro = r.libro; r.resultado
        }
    }
    override suspend fun cerrarSesionJuego(ownerId: String, sesionId: String, recompensa: RecompensaJuego): CierreSesionJuego {
        if (!soportaSesiones) return super.cerrarSesionJuego(ownerId, sesionId, recompensa)
        return mutex.withLock {
            cerrarLlamadas++
            if (fallosCierrePendientes > 0) { fallosCierrePendientes--; throw java.io.IOException("disco") }
            val r = SesionesJuego.cerrar(alDia(), libro, sesionId, recompensa, ahora)
            guardar(r.pet); libro = r.libro; r.resultado
        }
    }

    private fun no(): Nothing = throw UnsupportedOperationException()
    override suspend fun feed(ownerId: String, food: FoodType): ResultadoCuidado = no()
    override suspend fun clean(ownerId: String): ResultadoCuidado = no()
    override suspend fun heal(ownerId: String): ResultadoCuidado = no()
    override suspend fun toggleSleep(ownerId: String): ResultadoCuidado = no()
    override suspend fun cuddle(ownerId: String): ResultadoCuidado = no()
    override suspend fun reboot(ownerId: String): ResultadoCuidado = no()
    override suspend fun rename(ownerId: String, name: String): ResultadoCuidado = no()
    override suspend fun receiveSparks(ownerId: String, amount: Int): ResultadoCuidado = no()
    override suspend fun receiveMedicine(ownerId: String): ResultadoCuidado = no()
    override suspend fun earnSparkFromFriend(ownerId: String): ResultadoCuidado = no()
    override suspend fun giftSparks(ownerId: String, amount: Int): ResultadoCuidado = no()
}

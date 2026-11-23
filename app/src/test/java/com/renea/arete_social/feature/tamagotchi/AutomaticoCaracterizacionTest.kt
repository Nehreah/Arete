package com.renea.arete_social.feature.tamagotchi

import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.inicioDiaMillis
import com.renea.arete_social.feature.tamagotchi.logic.jugar
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import com.renea.arete_social.feature.tamagotchi.viewmodel.PetViewModel
import com.renea.arete_social.gui.tamagotchi.PetActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * FASE 0 (Miau Snake) — test de caracterización del modo AUTOMÁTICO de Michi.
 *
 * Congela el comportamiento actual de JUGAR antes de añadir el selector «Automático / Miau Snake»:
 *   PetScreen (Jugar) -> PetViewModel.play() -> PetRepository.play(ownerId) -> PetEntity.jugar().
 * Si una fase posterior cambia este comportamiento sin querer, este archivo lo detecta.
 * Los números salen de [PetBalance]: un reajuste deliberado del balance NO rompe estos tests,
 * pero un cambio de reglas (orden de rechazos, contadores, esperas, rendimiento) sí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticoCaracterizacionTest {

    private val minuto = 60_000L
    private val t0: Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.JANUARY, 5, 5, 0, 0)
    }.timeInMillis

    private fun adulta(block: PetEntity.() -> PetEntity = { this }): PetEntity =
        PetEntity(
            ownerId = "u", name = "Michi", stage = PetStage.ADULTO,
            birthMillis = t0 - 30 * 60 * minuto,
            lastTickMillis = t0,
            lastDayResetMillis = inicioDiaMillis(t0)
        ).block()

    // ── PetEntity.jugar(): efectos exactos ───────────────────────────────────

    @Test
    fun `jugar aplica exactamente los efectos actuales`() {
        val base = adulta()
        val r = base.jugar(t0)
        assertEquals(ResultadoCuidado.OK, r.resultado)
        val esperada = base.copy(
            happiness = (base.happiness + PetBalance.PLAY_HAPPINESS_GAIN).coerceAtMost(PetBalance.MAX_STAT),
            energy = base.energy - PetBalance.PLAY_ENERGY_COST,
            hunger = base.hunger - PetBalance.PLAY_HUNGER_COST,
            hygiene = base.hygiene - PetBalance.PLAY_HYGIENE_COST,
            ultimoJugadoMillis = t0,
            vecesJugadoHoy = 1,
            visitasHoy = 1,
            ultimaAtencionMillis = t0,
            experiencia = PetBalance.PLAY_XP
        )
        assertEquals(esperada, r.pet)
    }

    @Test
    fun `pasado el limite diario el rendimiento baja pero sigue siendo OK`() {
        val cansada = adulta { copy(happiness = 50, vecesJugadoHoy = PetBalance.MAX_PLAYS_PER_DAY) }
        val r = cansada.jugar(t0)
        assertEquals(ResultadoCuidado.OK, r.resultado)
        // Primer juego por encima del límite: factor 1 - 0.23 = 0.77.
        assertEquals(50 + (PetBalance.PLAY_HAPPINESS_GAIN * 0.77).toInt(), r.pet.happiness)
        assertEquals((PetBalance.PLAY_XP * 0.77).toInt(), r.pet.experiencia)
        assertEquals(PetBalance.MAX_PLAYS_PER_DAY + 1, r.pet.vecesJugadoHoy)
    }

    // ── PetEntity.jugar(): rechazos (y prioridad entre ellos) ────────────────

    @Test
    fun `los rechazos devuelven el motivo exacto y no cambian a la mascota`() {
        val casos = listOf(
            adulta { copy(isCorrupted = true) } to ResultadoCuidado.FUERA_DE_LINEA,
            adulta { copy(stage = PetStage.HUEVO) } to ResultadoCuidado.HUEVO,
            adulta { copy(isSleeping = true) } to ResultadoCuidado.DORMIDA,
            adulta { copy(energy = PetBalance.MIN_ENERGY_TO_PLAY - 1) } to ResultadoCuidado.POCA_ENERGIA,
            adulta { copy(hunger = PetBalance.MIN_HUNGER_TO_PLAY - 1) } to ResultadoCuidado.TIENE_HAMBRE
        )
        for ((pet, motivo) in casos) {
            val r = pet.jugar(t0)
            assertEquals(motivo, r.resultado)
            assertEquals(pet, r.pet)
        }
    }

    @Test
    fun `prioridad de rechazos - bloqueo general, luego energia, luego hambre`() {
        val dormidaSinEnergia = adulta { copy(isSleeping = true, energy = 0, hunger = 0) }
        assertEquals(ResultadoCuidado.DORMIDA, dormidaSinEnergia.jugar(t0).resultado)
        val sinEnergiaNiComida = adulta { copy(energy = 0, hunger = 0) }
        assertEquals(ResultadoCuidado.POCA_ENERGIA, sinEnergiaNiComida.jugar(t0).resultado)
    }

    @Test
    fun `la energia y el hambre minimas todavia permiten jugar`() {
        val justa = adulta {
            copy(energy = PetBalance.MIN_ENERGY_TO_PLAY, hunger = PetBalance.MIN_HUNGER_TO_PLAY)
        }
        assertEquals(ResultadoCuidado.OK, justa.jugar(t0).resultado)
    }

    @Test
    fun `espera entre juegos - rechaza antes y permite justo al cumplirse`() {
        val jugada = adulta().jugar(t0).pet
        val rechazo = jugada.jugar(t0 + minuto)
        assertEquals(ResultadoCuidado.EN_ESPERA, rechazo.resultado)
        assertEquals(jugada, rechazo.pet)
        val despues = jugada.copy(energy = 80, hunger = 80)
            .jugar(t0 + PetBalance.COOLDOWN_PLAY_MILLIS)
        assertEquals(ResultadoCuidado.OK, despues.resultado)
        assertEquals(2, despues.pet.vecesJugadoHoy)
    }

    // ── PetViewModel.play(): delegación y mensajes ───────────────────────────

    private class RepoFake(var resultadoPlay: ResultadoCuidado = ResultadoCuidado.OK) : PetRepository {
        val pet = MutableStateFlow<PetEntity?>(
            PetEntity(ownerId = "u", name = "Michi", stage = PetStage.ADULTO)
        )
        val llamadasPlay = mutableListOf<String>()
        var otrasAcciones = 0

        override fun observePet(ownerId: String): Flow<PetEntity?> = pet
        override suspend fun ensurePetExists(ownerId: String, name: String, type: PetType) {}
        override suspend fun tick(ownerId: String) {}
        override suspend fun play(ownerId: String): ResultadoCuidado {
            llamadasPlay += ownerId
            return resultadoPlay
        }
        override suspend fun feed(ownerId: String, food: FoodType): ResultadoCuidado = otra()
        override suspend fun clean(ownerId: String): ResultadoCuidado = otra()
        override suspend fun heal(ownerId: String): ResultadoCuidado = otra()
        override suspend fun toggleSleep(ownerId: String): ResultadoCuidado = otra()
        override suspend fun cuddle(ownerId: String): ResultadoCuidado = otra()
        override suspend fun reboot(ownerId: String): ResultadoCuidado = otra()
        override suspend fun rename(ownerId: String, name: String): ResultadoCuidado = otra()
        override suspend fun receiveSparks(ownerId: String, amount: Int): ResultadoCuidado = otra()
        override suspend fun receiveMedicine(ownerId: String): ResultadoCuidado = otra()
        override suspend fun earnSparkFromFriend(ownerId: String): ResultadoCuidado = otra()
        override suspend fun giftSparks(ownerId: String, amount: Int): ResultadoCuidado = otra()

        private fun otra(): ResultadoCuidado { otrasAcciones++; return ResultadoCuidado.OK }
    }

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: RepoFake, dueno: String = "u", quienMira: String = "u") =
        PetViewModel(repository = repo, ownerId = dueno, ownerNombre = "Ana", viewerId = quienMira)

    @Test
    fun `play delega una sola vez en el repositorio con el id del dueno y anima`() {
        val repo = RepoFake()
        val vm = vm(repo)
        val observador = CoroutineScope(dispatcher).launch { vm.uiState.collect { } }
        vm.play()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u"), repo.llamadasPlay)
        assertEquals(0, repo.otrasAcciones)
        assertEquals(PetActionType.PLAY, vm.uiState.value.activeAnimation)
        observador.cancel()
    }

    @Test
    fun `si jugar falla se explica el motivo y no hay animacion`() {
        val repo = RepoFake(resultadoPlay = ResultadoCuidado.POCA_ENERGIA)
        val vm = vm(repo)
        val observador = CoroutineScope(dispatcher).launch { vm.uiState.collect { } }
        vm.play()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u"), repo.llamadasPlay)
        assertNull(vm.uiState.value.activeAnimation)
        assertEquals(ResultadoCuidado.POCA_ENERGIA.texto("Michi"), vm.uiState.value.alertMessage)
        observador.cancel()
    }

    @Test
    fun `quien solo visita no puede jugar`() {
        val repo = RepoFake()
        vm(repo, dueno = "u", quienMira = "otra").play()
        dispatcher.scheduler.runCurrent()
        assertEquals(emptyList<String>(), repo.llamadasPlay)
    }
}

package com.renea.arete_social.feature.tamagotchi

import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.inicioDiaMillis
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import com.renea.arete_social.feature.tamagotchi.superpoderes.ServicioModoSuper
import com.renea.arete_social.testutils.RelojFalso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicioModoSuperTest {

    private val reloj = RelojFalso()
    private val hora = 3_600_000L

    private class RepoFalso(inicial: PetEntity?) : PetRepository {
        val pet = MutableStateFlow(inicial)
        override fun observePet(ownerId: String): Flow<PetEntity?> = pet
        override suspend fun ensurePetExists(ownerId: String, name: String, type: PetType) = Unit
        override suspend fun tick(ownerId: String) = Unit
        override suspend fun feed(ownerId: String, food: FoodType) = ResultadoCuidado.OK
        override suspend fun play(ownerId: String) = ResultadoCuidado.OK
        override suspend fun clean(ownerId: String) = ResultadoCuidado.OK
        override suspend fun heal(ownerId: String) = ResultadoCuidado.OK
        override suspend fun toggleSleep(ownerId: String) = ResultadoCuidado.OK
        override suspend fun cuddle(ownerId: String) = ResultadoCuidado.OK
        override suspend fun reboot(ownerId: String) = ResultadoCuidado.OK
        override suspend fun rename(ownerId: String, name: String) = ResultadoCuidado.OK
        override suspend fun receiveSparks(ownerId: String, amount: Int) = ResultadoCuidado.OK
        override suspend fun receiveMedicine(ownerId: String) = ResultadoCuidado.OK
        override suspend fun earnSparkFromFriend(ownerId: String) = ResultadoCuidado.OK
        override suspend fun giftSparks(ownerId: String, amount: Int) = ResultadoCuidado.OK
    }

    private fun mascota(superDesde: Long) = PetEntity(
        ownerId = "u", name = "Michi", stage = PetStage.ADULTO,
        birthMillis = reloj.ahora - 30 * hora, lastTickMillis = reloj.ahora,
        lastDayResetMillis = inicioDiaMillis(reloj.ahora), superDesdeMillis = superDesde
    )

    private val trabajo = Job()
    private val scope = CoroutineScope(trabajo + Dispatchers.Default)

    @Test
    fun `sin mascota no esta activo`() {
        val s = ServicioModoSuper(RepoFalso(null), scope, reloj)
        assertEquals(ServicioModoSuper.Estado(), s.calcular(null, reloj.ahora))
        trabajo.cancel()
    }

    @Test
    fun `calcula activo y vencimiento a partir de la mascota`() {
        val s = ServicioModoSuper(RepoFalso(null), scope, reloj)
        val desde = reloj.ahora - hora
        val e = s.calcular(mascota(desde), reloj.ahora)
        assertTrue(e.activo)
        assertEquals(desde + PetBalance.SUPER_DURATION_MILLIS, e.venceEn)
        assertFalse(s.calcular(mascota(desde), desde + 3 * hora).activo)
        assertFalse(s.calcular(mascota(0L), reloj.ahora).activo)
        trabajo.cancel()
    }

    @Test
    fun `se activa cuando la mascota entra en modo super y se apaga cuando vence`() = runBlocking {
        val repo = RepoFalso(mascota(0L))
        val s = ServicioModoSuper(repo, scope, reloj, intervaloReevaluacionMs = 20L)
        s.iniciar("u")
        assertFalse(s.estado.value.activo)

        repo.pet.value = mascota(reloj.ahora)
        withTimeout(5_000) { s.estado.first { it.activo } }

        reloj.avanzarHoras(3.0)                    // el reloj sigue solo; la mascota no cambia
        withTimeout(5_000) { s.estado.first { !it.activo } }
        assertNull(s.estado.value.venceEn)
        s.detener()
        trabajo.cancel()
    }

    @Test
    fun `detener apaga el estado`() = runBlocking {
        val repo = RepoFalso(mascota(reloj.ahora))
        val s = ServicioModoSuper(repo, scope, reloj, intervaloReevaluacionMs = 20L)
        s.iniciar("u")
        withTimeout(5_000) { s.estado.first { it.activo } }
        s.detener()
        assertFalse(s.estado.value.activo)
        trabajo.cancel()
    }

    @Test
    fun `avisa al activarse pero no al arrancar con un super que ya estaba en marcha`() = runBlocking {
        val repo = RepoFalso(mascota(reloj.ahora - hora))
        val avisos = mutableListOf<String>()
        val s = ServicioModoSuper(repo, scope, reloj, intervaloReevaluacionMs = 20L, alActivarse = { avisos += it.name })
        s.iniciar("u")
        withTimeout(5_000) { s.estado.first { it.activo } }
        delay(100)
        assertTrue("no debe avisar al arrancar", avisos.isEmpty())

        // El Súper vence y, un día después, Michi vuelve a llegar a la meta: nueva activación.
        reloj.avanzarHoras(25.0)
        repo.pet.value = mascota(reloj.ahora)
        withTimeout(5_000) { while (avisos.isEmpty()) delay(10) }
        assertEquals(listOf("Michi"), avisos)
        s.detener()
        trabajo.cancel()
    }
}

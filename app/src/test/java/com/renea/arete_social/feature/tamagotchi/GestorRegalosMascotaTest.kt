package com.renea.arete_social.feature.tamagotchi

import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.logic.AccionResultado
import com.renea.arete_social.feature.tamagotchi.logic.curar
import com.renea.arete_social.feature.tamagotchi.logic.ganarChispaPorEncuentro
import com.renea.arete_social.feature.tamagotchi.logic.recibirChispas
import com.renea.arete_social.feature.tamagotchi.logic.recibirMedicina
import com.renea.arete_social.feature.tamagotchi.logic.regalarChispas
import com.renea.arete_social.feature.tamagotchi.logic.renombrar
import com.renea.arete_social.feature.tamagotchi.model.FoodType
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import com.renea.arete_social.feature.tamagotchi.model.PetStage
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import com.renea.arete_social.feature.tamagotchi.regalos.GestorRegalosMascota
import com.renea.arete_social.feature.tamagotchi.regalos.RegaloMascotaEntrante
import com.renea.arete_social.feature.tamagotchi.regalos.ResultadoEnvioRegalo
import com.renea.arete_social.feature.tamagotchi.regalos.TipoRegalo
import com.renea.arete_social.testutils.FakeSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestorRegalosMascotaTest {

    /** Repositorio en memoria que usa las mismas funciones puras que el real. */
    private class RepoFake(inicial: PetEntity? = null) : PetRepository {
        val pet = MutableStateFlow(inicial)

        private fun aplicar(accion: (PetEntity) -> AccionResultado): ResultadoCuidado {
            val actual = pet.value ?: return ResultadoCuidado.SIN_MASCOTA
            val r = accion(actual)
            pet.value = r.pet
            return r.resultado
        }

        override fun observePet(ownerId: String): Flow<PetEntity?> = pet
        override suspend fun ensurePetExists(ownerId: String, name: String, type: PetType) {
            if (pet.value == null) pet.value = PetEntity(ownerId = ownerId, name = name, petType = type, stage = PetStage.ADULTO)
        }
        override suspend fun tick(ownerId: String) = Unit
        override suspend fun feed(ownerId: String, food: FoodType) = ResultadoCuidado.OK
        override suspend fun play(ownerId: String) = ResultadoCuidado.OK
        override suspend fun clean(ownerId: String) = ResultadoCuidado.OK
        override suspend fun heal(ownerId: String) = aplicar { it.curar() }
        override suspend fun toggleSleep(ownerId: String) = ResultadoCuidado.OK
        override suspend fun cuddle(ownerId: String) = ResultadoCuidado.OK
        override suspend fun reboot(ownerId: String) = ResultadoCuidado.OK
        override suspend fun rename(ownerId: String, name: String) = aplicar { it.renombrar(name) }
        override suspend fun receiveSparks(ownerId: String, amount: Int) = aplicar { it.recibirChispas(amount) }
        override suspend fun receiveMedicine(ownerId: String) = aplicar { it.recibirMedicina() }
        override suspend fun earnSparkFromFriend(ownerId: String) = aplicar { it.ganarChispaPorEncuentro() }
        override suspend fun giftSparks(ownerId: String, amount: Int) = aplicar { it.regalarChispas(amount) }
    }

    private class Envio(val destinatario: String, val id: String, val tipo: TipoRegalo, val cantidad: Int)

    private class Escenario(mascota: PetEntity?, val miId: String = "yo", var reloj: Long = 1_000_000L) {
        val repo = RepoFake(mascota)
        val enviados = mutableListOf<Envio>()
        val avisos = mutableListOf<String>()
        val gestor = GestorRegalosMascota(
            repositorio = repo,
            prefs = FakeSharedPreferences(),
            miIdProveedor = { miId },
            miNombreProveedor = { "Yo" },
            transporte = { dest, id, tipo, n, _ -> enviados += Envio(dest, id, tipo, n) },
            alNotificar = { _, texto -> avisos += texto },
            scope = CoroutineScope(Dispatchers.Unconfined),
            ahora = { reloj }
        )
    }

    private fun mascota(block: PetEntity.() -> PetEntity = { this }) =
        PetEntity(ownerId = "yo", name = "Michi", stage = PetStage.ADULTO).block()

    private fun regalo(id: String, tipo: TipoRegalo, n: Int = 1, de: String = "otro") =
        RegaloMascotaEntrante(regaloId = id, remitenteId = de, remitenteNombre = "Ana", tipo = tipo, cantidad = n)

    @Test
    fun `enviar una chispa descuenta una y la manda`() = runBlocking {
        val e = Escenario(mascota { copy(chispas = 2) })
        val r = e.gestor.enviar("otro", TipoRegalo.CHISPA)
        assertEquals(ResultadoEnvioRegalo.Enviado, r)
        assertEquals(1, e.repo.pet.value?.chispas)
        assertEquals(1, e.enviados.size)
        assertEquals("otro", e.enviados[0].destinatario)
        assertEquals(TipoRegalo.CHISPA, e.enviados[0].tipo)
    }

    @Test
    fun `sin chispas no se envia nada`() = runBlocking {
        val e = Escenario(mascota { copy(chispas = 0) })
        assertEquals(ResultadoEnvioRegalo.SinChispas, e.gestor.enviar("otro", TipoRegalo.CHISPA))
        assertTrue(e.enviados.isEmpty())
    }

    @Test
    fun `la medicina es gratis pero tiene pausa por persona`() = runBlocking {
        val e = Escenario(mascota { copy(chispas = 0) })
        assertEquals(ResultadoEnvioRegalo.Enviado, e.gestor.enviar("otro", TipoRegalo.MEDICINA))
        assertTrue(e.gestor.enviar("otro", TipoRegalo.MEDICINA) is ResultadoEnvioRegalo.EsperaMedicina)
        // A otra persona sí se puede
        assertEquals(ResultadoEnvioRegalo.Enviado, e.gestor.enviar("tercero", TipoRegalo.MEDICINA))
        // Pasada la pausa, otra vez a la primera
        e.reloj += PetBalance.MEDICINE_SEND_COOLDOWN_MILLIS + 1
        assertEquals(ResultadoEnvioRegalo.Enviado, e.gestor.enviar("otro", TipoRegalo.MEDICINA))
        assertEquals(3, e.enviados.size)
    }

    @Test
    fun `no se pueden enviar regalos a uno mismo`() = runBlocking {
        val e = Escenario(mascota { copy(chispas = 3) })
        assertEquals(ResultadoEnvioRegalo.ATiMismo, e.gestor.enviar("yo", TipoRegalo.CHISPA))
        assertEquals(3, e.repo.pet.value?.chispas)
    }

    @Test
    fun `recibir medicina permite curar y se gasta`() = runBlocking {
        val e = Escenario(mascota { copy(isSick = true, health = 30) })
        assertEquals(ResultadoCuidado.SIN_MEDICINA, e.repo.heal("yo"))

        e.gestor.recibir(regalo("g1", TipoRegalo.MEDICINA))
        assertEquals(1, e.repo.pet.value?.medicinas)
        assertEquals(1, e.avisos.size)

        assertEquals(ResultadoCuidado.OK, e.repo.heal("yo"))
        assertEquals(0, e.repo.pet.value?.medicinas)
        assertEquals(ResultadoCuidado.NO_ESTA_ENFERMA, e.repo.heal("yo"))
    }

    @Test
    fun `recibir chispas las suma`() = runBlocking {
        val e = Escenario(mascota())
        e.gestor.recibir(regalo("g1", TipoRegalo.CHISPA))
        assertEquals(1, e.repo.pet.value?.chispas)
    }

    @Test
    fun `un regalo repetido por dos canales solo se aplica una vez`() = runBlocking {
        val e = Escenario(mascota())
        e.gestor.recibir(regalo("g1", TipoRegalo.CHISPA))
        e.gestor.recibir(regalo("g1", TipoRegalo.CHISPA))
        assertEquals(1, e.repo.pet.value?.chispas)
        assertEquals(1, e.avisos.size)
    }

    @Test
    fun `si todavia no hay mascota se crea para no perder el regalo`() = runBlocking {
        val e = Escenario(null)
        e.gestor.recibir(regalo("g1", TipoRegalo.MEDICINA))
        assertEquals(1, e.repo.pet.value?.medicinas)
    }

    @Test
    fun `con el botiquin lleno la medicina extra se ignora`() = runBlocking {
        val e = Escenario(mascota { copy(medicinas = PetBalance.MAX_MEDICINES_STORED) })
        e.gestor.recibir(regalo("g1", TipoRegalo.MEDICINA))
        assertEquals(PetBalance.MAX_MEDICINES_STORED, e.repo.pet.value?.medicinas)
        assertTrue(e.avisos.isEmpty())
    }

    @Test
    fun `superar el limite de 10 regalos diarios por remitente descarta los excedentes`() = runBlocking {
        val e = Escenario(mascota())
        // Enviar 10 regalos válidos
        for (i in 1..10) {
            e.gestor.recibir(regalo("id_$i", TipoRegalo.CHISPA, de = "spammer"))
        }
        assertEquals(10, e.repo.pet.value?.chispas)
        assertEquals(10, e.avisos.size)

        // El regalo 11 del mismo remitente debe ser descartado
        e.gestor.recibir(regalo("id_11", TipoRegalo.CHISPA, de = "spammer"))
        assertEquals(10, e.repo.pet.value?.chispas)
        assertEquals(10, e.avisos.size)

        // Pero otro remitente sí puede enviar
        e.gestor.recibir(regalo("id_12", TipoRegalo.CHISPA, de = "amigo_legitimo"))
        assertEquals(11, e.repo.pet.value?.chispas)
        assertEquals(11, e.avisos.size)
    }

    @Test
    fun `la cantidad de chispas se fuerza a 1 maximo aunque el paquete declare mas`() = runBlocking {
        val e = Escenario(mascota())
        // Paquete malicioso intentando inflar con 9999 chispas
        val regaloInflado = RegaloMascotaEntrante(
            regaloId = "malicious_gift",
            remitenteId = "attacker",
            remitenteNombre = "Attacker",
            tipo = TipoRegalo.CHISPA,
            cantidad = 9999
        )
        e.gestor.recibir(regaloInflado)
        // Debe acotarse a exactamente 1 chispa
        assertEquals(1, e.repo.pet.value?.chispas)
    }
}

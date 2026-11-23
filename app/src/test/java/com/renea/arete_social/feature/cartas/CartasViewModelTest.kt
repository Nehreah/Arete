package com.renea.arete_social.feature.cartas

import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.NuevaCarta
import com.renea.arete_social.domain.repository.RepositorioCartas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartasViewModelTest {

    private class RepoFake : RepositorioCartas {
        val guardadas = mutableListOf<Carta>()
        val leidas = mutableListOf<String>()
        override suspend fun enviar(remitenteId: String, remitenteNombre: String, nueva: NuevaCarta): Carta =
            Carta("c${guardadas.size}", remitenteId, remitenteNombre, nueva.destinatarioId, nueva.destinatarioNombre, nueva.contenido, false, 1L)
                .also { guardadas += it }
        override suspend fun recibidas(usuarioId: String) = guardadas.filter { it.destinatarioId == usuarioId }
        override suspend fun enviadas(usuarioId: String) = guardadas.filter { it.remitenteId == usuarioId }
        override suspend fun marcarLeida(cartaId: String) { leidas += cartaId }
        override suspend fun eliminar(cartaId: String) { guardadas.removeAll { it.id == cartaId } }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: RepoFake, invitado: Boolean = false) =
        CartasViewModel(repo, "yo", invitado, esAmigo = { it == "ana" })

    @Test fun `solo se escribe a amistades`() {
        val repo = RepoFake()
        var error: String? = null
        vm(repo).enviar("Yo", "desconocido", "Pepe", "Hola", { error = it })
        assertNotNull(error)
        assertEquals(0, repo.guardadas.size)
    }

    @Test fun `enviar guarda la carta y la lista en enviadas`() {
        val repo = RepoFake()
        val v = vm(repo)
        var error: String? = "pendiente"
        v.enviar("Yo", "ana", "Ana", "  Hola Ana ", { error = it })
        assertNull(error)
        assertEquals("Hola Ana", repo.guardadas.single().contenido)
        assertEquals(1, v.estado.value.enviadas.size)
    }

    @Test fun `abrir marca leida solo cartas recibidas`() {
        val repo = RepoFake()
        repo.guardadas += Carta("k1", "ana", "Ana", "yo", "Yo", "Hola", false, 1L)
        val v = vm(repo)
        assertEquals(1, v.estado.value.sinLeer)
        v.abrir(v.estado.value.recibidas.single())
        assertEquals(0, v.estado.value.sinLeer)
        assertEquals(listOf("k1"), repo.leidas)
    }

    @Test fun `invitado no puede enviar ni cargar`() {
        val repo = RepoFake()
        val v = vm(repo, invitado = true)
        var error: String? = null
        v.enviar("Yo", "ana", "Ana", "Hola", { error = it })
        assertNotNull(error)
        assertFalse(v.estado.value.disponible)
    }
}

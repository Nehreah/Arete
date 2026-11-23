package com.renea.arete_social.feature.mensajeria

import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.repository.ID_SALA_GENERAL
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.domain.repository.RepositorioSalas
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de unificación y dedupe de salas (§5B.2).
 */
class SalasViewModelTest {

    private val repoFake = object : RepositorioSalas {
        override suspend fun obtenerOCrearSalaGeneral(): Sala = error("Not needed")
        override suspend fun crearSala(nombre: String, descripcion: String, creadorId: String, esPublica: Boolean, clave: String?): Sala = error("Not needed")
        override suspend fun listarSalas(): List<Sala> = emptyList()
        override fun observarSalas() = flowOf(emptyList<Sala>())
        override suspend fun obtenerSala(id: String): Sala? = null
        override suspend fun unirseASala(salaId: String, clave: String?): Boolean = true
    }

    private val viewModel = SalasViewModel(
        repositorioSalas = repoFake,
        usuarioIdActual = "user-test-123"
    )

    @Test
    fun `unificarYFiltrarSalas - solo existe un chat general aunque vengan duplicados remotos`() {
        val salaGeneralFija = Sala(
            id = ID_SALA_GENERAL,
            nombre = "Chat General (500m)",
            chatId = ID_CHAT_GENERAL,
            creadorId = "sistema",
            tipo = TipoSala.GENERAL
        )

        val salaRemotaGeneralDuplicada = Sala(
            id = "sql-uuid-general",
            nombre = "General",
            chatId = ID_CHAT_GENERAL,
            creadorId = "admin",
            tipo = TipoSala.PERSONALIZADA
        )

        val salaRemotaOtraDuplicada = Sala(
            id = "sql-uuid-general-2",
            nombre = "Sala General",
            chatId = "otro-chat-id-general",
            creadorId = "admin",
            tipo = TipoSala.GENERAL
        )

        val salaPersonalizada = Sala(
            id = "sala-estudio-1",
            nombre = "Grupo de Estudio",
            chatId = "chat-estudio-1",
            creadorId = "user-1",
            tipo = TipoSala.PERSONALIZADA
        )

        val salaComunidad = Sala(
            id = "sala-comunidad-psico",
            nombre = "# Psicología",
            chatId = "chat-comunidad-psico",
            creadorId = "comunidad",
            tipo = TipoSala.COMUNIDAD
        )

        val resultado = viewModel.unificarYFiltrarSalas(
            remotas = listOf(salaGeneralFija, salaRemotaGeneralDuplicada, salaRemotaOtraDuplicada, salaPersonalizada),
            comunidades = listOf(salaComunidad)
        )

        // Verificaciones
        val generales = resultado.filter { it.tipo == TipoSala.GENERAL || it.chatId == ID_CHAT_GENERAL }
        assertEquals("Debe existir exactamente UN solo chat general", 1, generales.size)
        assertEquals("El general debe tener el ID_CHAT_GENERAL oficial", ID_CHAT_GENERAL, generales.first().chatId)

        assertTrue("Debe incluir la sala de comunidad", resultado.any { it.tipo == TipoSala.COMUNIDAD && it.nombre == "# Psicología" })
        assertTrue("Debe incluir la sala personalizada", resultado.any { it.id == "sala-estudio-1" })

        assertEquals("Debe tener 3 salas en total (1 general + 1 comunidad + 1 personalizada)", 3, resultado.size)
    }
}

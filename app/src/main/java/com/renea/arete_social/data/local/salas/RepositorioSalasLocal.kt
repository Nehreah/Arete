package com.renea.arete_social.data.local.salas

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.model.Sala
import com.renea.arete_social.domain.model.TipoSala
import com.renea.arete_social.domain.repository.ID_CHAT_GENERAL
import com.renea.arete_social.domain.repository.ID_SALA_GENERAL
import com.renea.arete_social.domain.repository.RepositorioSalas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RepositorioSalasLocal(
    context: Context? = null
) : RepositorioSalas {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("antired_salas_local_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val salasEnMemoria = ConcurrentHashMap<String, Sala>()

    private val salaGeneralFija = Sala(
        id = ID_SALA_GENERAL,
        nombre = "Chat Aréte",
        descripcion = "El chat general: abierto a todos",
        chatId = ID_CHAT_GENERAL,
        creadorId = "sistema",
        esPublica = true,
        clave = null,
        tipo = TipoSala.GENERAL,
        entidadId = null,
        fechaCreacion = 0L
    )

    private val _flujoSalas = MutableStateFlow<List<Sala>>(listOf(salaGeneralFija))

    init {
        salasEnMemoria[ID_SALA_GENERAL] = salaGeneralFija
        cargarDesdePrefs()
        actualizarFlujo()
    }

    private fun cargarDesdePrefs() {
        prefs?.all?.forEach { (clave, valor) ->
            if (clave.startsWith("sala_") && valor is String) {
                runCatching {
                    val sala = json.decodeFromString(Sala.serializer(), valor)
                    salasEnMemoria[sala.id] = sala
                }
            }
        }
    }

    private fun guardarEnPrefs(sala: Sala) {
        prefs?.let {
            val serializado = runCatching { json.encodeToString(Sala.serializer(), sala) }.getOrNull() ?: return
            it.edit().putString("sala_${sala.id}", serializado).apply()
        }
    }

    private fun actualizarFlujo() {
        val lista = (listOf(salaGeneralFija) + salasEnMemoria.values.filter { it.id != ID_SALA_GENERAL })
            .distinctBy { it.id }
            .sortedWith(compareBy<Sala> { it.tipo != TipoSala.GENERAL }.thenByDescending { it.fechaCreacion })
        _flujoSalas.value = lista
    }

    override suspend fun obtenerOCrearSalaGeneral(): Sala = salaGeneralFija

    override suspend fun crearSala(
        nombre: String,
        descripcion: String,
        creadorId: String,
        esPublica: Boolean,
        clave: String?
    ): Sala {
        val chatId = UUID.randomUUID().toString()
        val sala = Sala(
            id = UUID.randomUUID().toString(),
            nombre = nombre,
            descripcion = descripcion,
            chatId = chatId,
            creadorId = creadorId,
            esPublica = esPublica,
            clave = if (esPublica) null else clave?.trim(),
            tipo = TipoSala.PERSONALIZADA,
            entidadId = null,
            fechaCreacion = System.currentTimeMillis()
        )
        salasEnMemoria[sala.id] = sala
        guardarEnPrefs(sala)
        actualizarFlujo()
        return sala
    }

    override suspend fun listarSalas(): List<Sala> = _flujoSalas.value

    override fun observarSalas(): Flow<List<Sala>> = _flujoSalas.asStateFlow()

    override suspend fun obtenerSala(id: String): Sala? {
        if (id == ID_SALA_GENERAL) return salaGeneralFija
        return salasEnMemoria[id]
    }

    override suspend fun unirseASala(salaId: String, clave: String?): Boolean {
        val sala = salasEnMemoria[salaId] ?: return false
        if (sala.esPublica) return true
        return sala.clave == null || sala.clave == clave?.trim()
    }
}

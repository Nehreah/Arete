package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Sala
import kotlinx.coroutines.flow.Flow

const val ID_CHAT_GENERAL = "general-500m-antired-chat"
const val ID_SALA_GENERAL = "sala-general-500m"

interface RepositorioSalas {
    suspend fun crearSala(
        nombre: String,
        descripcion: String,
        creadorId: String,
        esPublica: Boolean,
        clave: String?
    ): Sala

    suspend fun listarSalas(): List<Sala>
    fun observarSalas(): Flow<List<Sala>>
    suspend fun obtenerSala(id: String): Sala?
    suspend fun obtenerOCrearSalaGeneral(): Sala
    suspend fun unirseASala(salaId: String, clave: String?): Boolean = false
}

package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.ZonaRegional
import kotlinx.coroutines.flow.Flow

interface RepositorioLugares {
    suspend fun crear(nombre: String, descripcion: String, ubicacion: Ubicacion): Lugar
    suspend fun obtenerPorId(id: String): Lugar?
    fun observarLugar(id: String): Flow<Lugar?>
    suspend fun lugaresCercanos(origen: Ubicacion, radioMetros: Double): List<Lugar>

    /**
     * Lugares dentro de la caja geográfica de una zona regional (Modo Súper). Devuelve como mucho
     * [limite]. La pertenencia exacta a la región la afina quien llama. Las implementaciones sin
     * red devuelven una lista vacía.
     */
    suspend fun lugaresEnZona(zona: ZonaRegional, limite: Int = 300): List<Lugar> = emptyList()
}
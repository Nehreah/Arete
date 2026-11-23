package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.NuevaObra
import com.renea.arete_social.domain.model.ObraLibreria

import com.renea.arete_social.domain.model.ResultadoCompartirMapa
import kotlinx.coroutines.flow.Flow

interface RepositorioLibreria {
    suspend fun listar(autorId: String): List<ObraLibreria>
    suspend fun crear(autorId: String, nueva: NuevaObra): ObraLibreria
    suspend fun eliminar(obra: ObraLibreria)

    /** Enlace temporal para abrir el PDF de [obra]; el archivo no es público. */
    suspend fun urlTemporalPdf(obra: ObraLibreria): String

    /**
     * Marca la obra para ser leída por el Michi en el mapa durante 24 horas.
     * Solo se permite compartir una obra cada 1 hora.
     */
    suspend fun compartirEnMapa(obraId: String, autorId: String): ResultadoCompartirMapa

    /**
     * Obras compartidas en las últimas 24 horas por los autores indicados (cercanos).
     */
    suspend fun listarObrasCercanasMapa(autoresIds: List<String>): List<ObraLibreria>

    /**
     * Registra que un usuario lector abrió la ficha/pergamino de una obra.
     */
    suspend fun registrarLecturaObra(obraId: String, autorId: String, lectorId: String)

    /**
     * Emite los eventos de lectura dirigidos a este autor.
     */
    fun escucharLecturasObras(autorId: String): Flow<String>
}


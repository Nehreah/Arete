package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Region

/** Las ciudades principales con chat regional. */
interface RepositorioRegiones {
    /**
     * Regiones disponibles. Nunca falla ni devuelve una lista vacía: si no se puede leer la tabla
     * remota se usan las regiones embebidas en la app (RegionesPredeterminadas).
     */
    suspend fun listar(): List<Region>
}

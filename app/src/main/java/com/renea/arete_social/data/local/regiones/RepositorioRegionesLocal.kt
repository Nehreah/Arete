package com.renea.arete_social.data.local.regiones

import com.renea.arete_social.domain.model.Region
import com.renea.arete_social.domain.model.RegionesPredeterminadas
import com.renea.arete_social.domain.repository.RepositorioRegiones

/**
 * Proveedor local de regiones basado en las regiones predeterminadas embebidas.
 */
class RepositorioRegionesLocal : RepositorioRegiones {
    override suspend fun listar(): List<Region> = RegionesPredeterminadas.todas
}

package com.renea.arete_social.domain.ruta

import com.renea.arete_social.domain.model.ModoRuta
import com.renea.arete_social.domain.model.RutaEncuentro
import com.renea.arete_social.domain.model.Ubicacion

/** Calcula cómo ir de [origen] a [destino]. Nunca lanza: sin servicio de rutas devuelve una línea recta. */
interface CalculadorRuta {
    suspend fun calcular(origen: Ubicacion, destino: Ubicacion, modo: ModoRuta): RutaEncuentro
}

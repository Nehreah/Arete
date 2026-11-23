package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

/**
 * Coordenada geográfica con marca de tiempo. Se usa tanto para la posición de un [Usuario]
 * como para la posición fija de un [Lugar] o un [Evento].
 *
 * @param precisionMetros Radio de incertidumbre del GPS en metros (campo `accuracy` de
 *   `android.location.Location`). Null cuando no se conoce (ubicación de red, posición de un
 *   lugar/evento, o ubicación de otro usuario).
 */
@Serializable
data class Ubicacion(
    val latitud: Double,
    val longitud: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val precisionMetros: Float? = null
)

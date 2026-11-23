package com.renea.arete_social.domain.proximity

import com.renea.arete_social.domain.model.Dispositivo
import com.renea.arete_social.domain.model.Ubicacion
import kotlinx.coroutines.flow.Flow

/**
 * Contrato único para "quién está cerca". Hoy lo implementa un detector por GPS
 * (data/proximity/gps/). En la Parte III, un detector BLE implementará esta misma
 * interfaz sin que feature/mapa/ tenga que cambiar.
 */
interface DetectorProximidad {
    /** Posición propia, en vivo. Necesaria para el marcador propio en el mapa. */
    fun ubicacionPropia(): Flow<Ubicacion>
    fun descubrir(): Flow<List<Dispositivo>>
    fun detener()
}
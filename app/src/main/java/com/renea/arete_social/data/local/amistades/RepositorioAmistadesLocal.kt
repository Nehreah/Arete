package com.renea.arete_social.data.local.amistades

import com.renea.arete_social.domain.repository.AmistadesServidor
import com.renea.arete_social.domain.repository.RepositorioAmistades
import com.renea.arete_social.feature.auth.GestorAmistades

class RepositorioAmistadesLocal : RepositorioAmistades {
    override suspend fun obtenerTodas(miId: String): AmistadesServidor {
        return AmistadesServidor(
            amigosIds = GestorAmistades.amigosIds.value,
            solicitudesRecibidas = GestorAmistades.solicitudes.value,
            solicitudesEnviadasIds = GestorAmistades.solicitudesEnviadasIds.value
        )
    }

    override suspend fun solicitar(miId: String, otroId: String): Boolean {
        if (GestorAmistades.tieneSolicitud(otroId)) {
            GestorAmistades.agregarAmigo(otroId)
            return true
        }
        GestorAmistades.marcarSolicitudEnviada(otroId)
        return false
    }

    override suspend fun aceptar(miId: String, otroId: String) {
        GestorAmistades.agregarAmigo(otroId)
    }

    override suspend fun eliminar(miId: String, otroId: String) {
        GestorAmistades.eliminarAmigo(otroId)
        GestorAmistades.cancelarSolicitudEnviada(otroId)
        GestorAmistades.rechazarSolicitud(otroId)
    }
}

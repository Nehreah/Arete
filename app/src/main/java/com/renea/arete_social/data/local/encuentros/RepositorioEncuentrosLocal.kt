package com.renea.arete_social.data.local.encuentros

import com.renea.arete_social.domain.model.EncuentroRemoto
import com.renea.arete_social.domain.model.EstadoEncuentro
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.repository.RepositorioEncuentros
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RepositorioEncuentrosLocal(
    private val miUsuarioIdProveedor: () -> String?
) : RepositorioEncuentros {

    private val encuentros = ConcurrentHashMap<String, EncuentroRemoto>()
    private val ubicaciones = ConcurrentHashMap<String, ConcurrentHashMap<String, Ubicacion>>()

    override suspend fun solicitar(amigoId: String): String {
        val miId = miUsuarioIdProveedor() ?: "yo"
        val id = UUID.randomUUID().toString()
        val ahora = System.currentTimeMillis()
        val remoto = EncuentroRemoto(
            id = id,
            solicitanteId = miId,
            invitadoId = amigoId,
            estado = EstadoEncuentro.PENDIENTE,
            expiraEnMillis = ahora + 10 * 60 * 1000L,
            creadoEnMillis = ahora
        )
        encuentros[id] = remoto
        return id
    }

    override suspend fun responder(encuentroId: String, acepta: Boolean): EstadoEncuentro {
        val actual = encuentros[encuentroId] ?: return EstadoEncuentro.EXPIRADO
        val nuevoEstado = if (acepta) EstadoEncuentro.ACTIVO else EstadoEncuentro.RECHAZADO
        val ahora = System.currentTimeMillis()
        val actualizado = actual.copy(
            estado = nuevoEstado,
            expiraEnMillis = if (acepta) ahora + 60 * 60 * 1000L else actual.expiraEnMillis
        )
        encuentros[encuentroId] = actualizado
        return nuevoEstado
    }

    override suspend fun terminar(encuentroId: String) {
        val actual = encuentros[encuentroId] ?: return
        encuentros[encuentroId] = actual.copy(estado = EstadoEncuentro.TERMINADO)
        ubicaciones.remove(encuentroId)
    }

    override suspend fun publicarUbicacion(encuentroId: String, ubicacion: Ubicacion) {
        val miId = miUsuarioIdProveedor() ?: "yo"
        val mapa = ubicaciones.getOrPut(encuentroId) { ConcurrentHashMap() }
        mapa[miId] = ubicacion
    }

    override suspend fun obtenerAbiertos(): List<EncuentroRemoto> {
        val miId = miUsuarioIdProveedor() ?: return emptyList()
        val ahora = System.currentTimeMillis()
        return encuentros.values.filter {
            (it.solicitanteId == miId || it.invitadoId == miId) &&
            (it.estado == EstadoEncuentro.PENDIENTE || it.estado == EstadoEncuentro.ACTIVO) &&
            it.expiraEnMillis > ahora
        }
    }

    override suspend fun obtener(encuentroId: String): EncuentroRemoto? = encuentros[encuentroId]

    override suspend fun ubicacionDe(encuentroId: String, usuarioId: String): Ubicacion? {
        return ubicaciones[encuentroId]?.get(usuarioId)
    }
}

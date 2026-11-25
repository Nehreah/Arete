package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.model.ZonaRegional
import kotlinx.coroutines.flow.Flow

interface RepositorioEventos {
    suspend fun crear(
        nombre: String,
        descripcion: String,
        fecha: Long,
        lugarId: String? = null,
        creadorId: String,
        comunidadId: String? = null,
        ubicacion: Ubicacion? = null,
        visibilidad: VisibilidadEvento = VisibilidadEvento.PUBLICO,
        fotoUrl: String? = null
    ): Evento

    suspend fun obtenerPorId(id: String): Evento?
    fun observarEvento(id: String): Flow<Evento?>
    suspend fun unirse(eventoId: String, usuarioId: String)
    suspend fun salir(eventoId: String, usuarioId: String)
    suspend fun expulsarParticipante(eventoId: String, usuarioId: String) = salir(eventoId, usuarioId)
    suspend fun actualizar(
        id: String,
        nombre: String,
        descripcion: String,
        fecha: Long,
        ubicacion: Ubicacion?,
        visibilidad: VisibilidadEvento,
        fotoUrl: String?
    )
    suspend fun eliminar(eventoId: String)
    suspend fun asignarRol(eventoId: String, usuarioId: String, rol: String)
    suspend fun listarParticipantesConRoles(eventoId: String): List<com.renea.arete_social.domain.model.ParticipanteEvento>
    suspend fun subirPortada(eventoId: String, bytes: ByteArray, extension: String = "jpg"): String
    suspend fun eventosCercanos(origen: Ubicacion, radioMetros: Double): List<Evento>
    suspend fun listarPorComunidad(comunidadId: String): List<Evento>
    suspend fun listarMisEventos(usuarioId: String): List<Evento> = emptyList()

    /** Flujo que emite los IDs de eventos eliminados para sincronización reactiva en mapa y descubrimientos. */
    val eventosEliminados: Flow<String> get() = kotlinx.coroutines.flow.emptyFlow()

    /** Notifica que un evento ha sido eliminado o ya no existe en el servidor. */
    fun notificarEliminado(eventoId: String) {}

    /**
     * Flujo que emite [Unit] cada vez que cualquier evento es creado, actualizado o eliminado
     * localmente (incluyendo los recibidos vía mesh). Permite que las pantallas reaccionen
     * inmediatamente sin necesitar un cambio de posición GPS.
     */
    val eventosActualizados: Flow<Unit> get() = kotlinx.coroutines.flow.emptyFlow()

    /** Señaliza que un evento cambió. Las implementaciones locales emiten en [eventosActualizados]. */
    fun notificarActualizado() {}

    /**
     * Eventos PÚBLICOS dentro de la caja geográfica de una zona regional, con fecha igual o posterior
     * a [desdeMillis] (Modo Súper), ordenados por fecha y como mucho [limite]. La pertenencia exacta
     * a la región la afina quien llama. Las implementaciones sin red devuelven una lista vacía.
     */
    suspend fun eventosPublicosEnZona(zona: ZonaRegional, desdeMillis: Long, limite: Int = 300): List<Evento> = emptyList()
}
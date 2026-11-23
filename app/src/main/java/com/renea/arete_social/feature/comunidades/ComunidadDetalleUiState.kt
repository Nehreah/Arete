package com.renea.arete_social.feature.comunidades

import com.renea.arete_social.domain.model.RolComunidad
import com.renea.arete_social.domain.model.Comunidad
import com.renea.arete_social.domain.model.Evento
import kotlinx.serialization.Serializable

@Serializable
data class PublicacionMuro(
    val id: String = "",
    val autorId: String = "",
    val autor: String = "",
    val tiempo: String = "",
    val texto: String = "",
    val imagenUri: String? = null,
    val likes: Int = 0,
    val likedPorMi: Boolean = false,
    val comentarios: Int = 0,
    val esEvento: Boolean = false,
    val categoria: String = "Todos",
    /**
     * true → la publicación no se ha sincronizado local/mesh todavía.
     * Se muestra al autor con etiqueta "Pendiente de envío".
     */
    val pendiente: Boolean = false
)

@Serializable
data class MiembroComunidad(
    val id: String = "",
    val nombre: String = "",
    val rol: String = "Miembro",
    val online: Boolean = false,
    val avatarEmoji: String = "👤",
    val fotoUrl: String? = null
)

data class EventoComunidad(
    val id: String = "",
    val titulo: String = "",
    val fecha: String = "",
    val ubicacion: String = "",
    val descripcion: String = "",
    val asistentes: Int = 0,
    val asistire: Boolean = false,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val eventoReal: Evento? = null
)

@Serializable
data class ArchivoComunidad(
    val id: String = "",
    val comunidadId: String = "",
    val nombre: String = "",
    val url: String = "",
    val tamanoBytes: Long = 0L,
    val tipoMime: String = "",
    val autorNombre: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface ComunidadDetalleUiState {
    data object Cargando : ComunidadDetalleUiState
    data class Listo(
        val comunidad: Comunidad,
        val uniendose: Boolean = false,
        val esMiembro: Boolean = false,
        val publicando: Boolean = false,
        val miRol: String = "MIEMBRO",
        val notificacionesSilenciadas: Boolean = false,
        val publicaciones: List<PublicacionMuro> = emptyList(),
        val miembros: List<MiembroComunidad> = emptyList(),
        val eventos: List<EventoComunidad> = emptyList(),
        val archivos: List<ArchivoComunidad> = emptyList(),
        /** IDs (IK hex) de personas que solicitaron unirse y esperan aprobación. Solo visible para líder/admin. */
        val solicitudesPendientes: List<String> = emptyList(),
        /** true cuando el propio usuario envió solicitud de membresía pero aún no fue aprobado. */
        val solicitudEnviada: Boolean = false
    ) : ComunidadDetalleUiState {
        val esLider: Boolean get() = RolComunidad.desdeCodigo(miRol).esLiderODueno()
        val esModerador: Boolean get() = RolComunidad.desdeCodigo(miRol) == RolComunidad.MODERATOR || RolComunidad.desdeCodigo(miRol) == RolComunidad.ADMIN
        val puedeGestionar: Boolean get() = RolComunidad.desdeCodigo(miRol).puedeGestionar()
        val puedeCrearEventos: Boolean get() = RolComunidad.desdeCodigo(miRol).puedeCrearEventos()
    }
    data class Error(val mensaje: String) : ComunidadDetalleUiState
}

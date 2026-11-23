package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.model.EstadoPerfilSnapshot
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.domain.model.TipoPublicacionPerfil

/**
 * Mappers para conversión entre modelos de publicación de perfil y snapshots Bluetooth,
 * resolviendo hashes y almacenamiento de medios en disco mediante [AlmacenMediaPerfil].
 */
fun List<PublicacionPerfil>.aEstadosSnapshot(ahora: Long = System.currentTimeMillis()): List<EstadoPerfilSnapshot> =
    filter { it.estaVigenteSemanal(ahora) }
        .mapNotNull { pub ->
            when (pub.tipo) {
                TipoPublicacionPerfil.TEXTO_RETRO -> {
                    EstadoPerfilSnapshot(id = pub.id, texto = pub.contenido, ts = pub.timestamp, fotoHash = null)
                }
                TipoPublicacionPerfil.FOTO_MATRIX -> {
                    val hash = if (pub.contenido.startsWith("/") || pub.contenido.startsWith("file:")) {
                        AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(pub.contenido, esAvatar = false)
                    } else null
                    if (hash != null) {
                        EstadoPerfilSnapshot(id = pub.id, texto = "", ts = pub.timestamp, fotoHash = hash)
                    } else null
                }
            }
        }

fun SnapshotPerfil.aPublicaciones(ahora: Long = System.currentTimeMillis()): List<PublicacionPerfil> =
    estados
        .map {
            if (it.fotoHash != null) {
                val rutaLocal = AlmacenMediaPerfil.obtenerArchivoPorHash(it.fotoHash)?.absolutePath ?: ""
                PublicacionPerfil(
                    id = it.id,
                    usuarioId = usuarioId,
                    autorNombre = nombre,
                    tipo = TipoPublicacionPerfil.FOTO_MATRIX,
                    contenido = rutaLocal,
                    timestamp = it.ts
                )
            } else {
                PublicacionPerfil(
                    id = it.id,
                    usuarioId = usuarioId,
                    autorNombre = nombre,
                    tipo = TipoPublicacionPerfil.TEXTO_RETRO,
                    contenido = it.texto,
                    timestamp = it.ts
                )
            }
        }
        .filter { it.estaVigenteSemanal(ahora) }
        .sortedByDescending { it.timestamp }

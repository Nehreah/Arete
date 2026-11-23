package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.data.local.room.dao.PeerProfileDao
import com.renea.arete_social.data.local.room.entity.PeerProfileEntity
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.repository.PerfilPar
import com.renea.arete_social.domain.repository.ResolverPerfiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación local y Room de [ResolverPerfiles].
 * Fuente de verdad unificada para resolución de nombres, fotos y perfiles de pares (vecinos BLE y red).
 */
class ResolverPerfilesLocal(
    private val peerProfileDao: PeerProfileDao,
    private val cacheUsuariosLocal: CacheUsuariosLocal?,
    private val cachePerfilLocal: CachePerfilLocal?
) : ResolverPerfiles {

    override suspend fun resolverNombre(usuarioId: String): String {
        if (usuarioId.isBlank()) return "Alguien"
        val peer = peerProfileDao.getPeer(usuarioId)
        if (peer != null && peer.nombre.isNotBlank() && peer.nombre != "Cargando…") {
            return peer.nombre
        }
        val ble = cachePerfilLocal?.recibido(usuarioId)
        if (ble != null && ble.nombre.isNotBlank()) {
            return ble.nombre
        }
        val usuario = cacheUsuariosLocal?.obtener(usuarioId)
        if (usuario != null && usuario.nombre.isNotBlank()) {
            return usuario.nombre
        }
        return peer?.nombre?.takeIf { it.isNotBlank() && it != "Cargando…" } ?: "Alguien"
    }

    override suspend fun resolverFoto(usuarioId: String): String? {
        if (usuarioId.isBlank()) return null
        val peer = peerProfileDao.getPeer(usuarioId)
        if (peer != null && !peer.fotoPath.isNullOrBlank()) return peer.fotoPath
        val ble = cachePerfilLocal?.recibido(usuarioId)
        if (ble != null && !ble.foto.isNullOrBlank()) return ble.foto
        val usuario = cacheUsuariosLocal?.obtener(usuarioId)
        if (usuario != null && !usuario.foto.isNullOrBlank()) return usuario.foto
        return null
    }

    override suspend fun obtenerPerfil(usuarioId: String): PerfilPar? {
        if (usuarioId.isBlank()) return null
        val peer = peerProfileDao.getPeer(usuarioId)
        if (peer != null) {
            return PerfilPar(
                usuarioId = peer.usuarioId,
                nombre = peer.nombre,
                descripcion = peer.descripcion,
                foto = peer.fotoPath,
                portada = peer.portadaPath,
                esAmigo = peer.esAmigo,
                ultimaVezVisto = peer.ultimaVezVisto
            )
        }
        val ble = cachePerfilLocal?.recibido(usuarioId)
        if (ble != null) {
            return PerfilPar(
                usuarioId = ble.usuarioId,
                nombre = ble.nombre,
                descripcion = ble.descripcion,
                foto = ble.foto,
                portada = ble.portada,
                esAmigo = !ble.privado,
                ultimaVezVisto = ble.actualizadoEn
            )
        }
        val usuario = cacheUsuariosLocal?.obtener(usuarioId)
        if (usuario != null) {
            return PerfilPar(
                usuarioId = usuario.id,
                nombre = usuario.nombre,
                descripcion = usuario.descripcion,
                foto = usuario.foto,
                portada = usuario.portada,
                esAmigo = false,
                ultimaVezVisto = System.currentTimeMillis()
            )
        }
        return null
    }

    override fun observarPerfil(usuarioId: String): Flow<PerfilPar?> {
        return peerProfileDao.observePeer(usuarioId).map { peer ->
            peer?.let {
                PerfilPar(
                    usuarioId = it.usuarioId,
                    nombre = it.nombre,
                    descripcion = it.descripcion,
                    foto = it.fotoPath,
                    portada = it.portadaPath,
                    esAmigo = it.esAmigo,
                    ultimaVezVisto = it.ultimaVezVisto
                )
            }
        }
    }

    override suspend fun registrarParDescubierto(usuarioId: String, nombreProvisional: String?) {
        if (usuarioId.isBlank()) return
        val previo = peerProfileDao.getPeer(usuarioId)
        val nombreLimpio = nombreProvisional?.trim()?.takeIf { it.isNotEmpty() && it != "Cargando…" }
        val nombreFinal = when {
            previo != null && previo.nombre.isNotBlank() && previo.nombre != "Cargando…" -> {
                if (nombreLimpio != null && nombreLimpio.length > previo.nombre.length) nombreLimpio else previo.nombre
            }
            nombreLimpio != null -> nombreLimpio
            previo != null -> previo.nombre
            else -> "Alguien"
        }

        peerProfileDao.insertOrUpdate(
            PeerProfileEntity(
                usuarioId = usuarioId,
                nombre = nombreFinal,
                descripcion = previo?.descripcion ?: "",
                fotoPath = previo?.fotoPath,
                fotoHash = previo?.fotoHash,
                portadaPath = previo?.portadaPath,
                portadaHash = previo?.portadaHash,
                perfilVer = previo?.perfilVer ?: 0L,
                esAmigo = previo?.esAmigo ?: false,
                ultimaVezVisto = System.currentTimeMillis()
            )
        )
    }

    override suspend fun registrarPerfilRecibido(snapshot: SnapshotPerfil) {
        if (snapshot.usuarioId.isBlank()) return
        val previo = peerProfileDao.getPeer(snapshot.usuarioId)
        val entidad = PeerProfileEntity(
            usuarioId = snapshot.usuarioId,
            nombre = snapshot.nombre.ifBlank { previo?.nombre ?: "Alguien" },
            descripcion = snapshot.descripcion.ifBlank { previo?.descripcion ?: "" },
            fotoPath = snapshot.foto ?: previo?.fotoPath,
            fotoHash = snapshot.fotoHash ?: previo?.fotoHash,
            portadaPath = snapshot.portada ?: previo?.portadaPath,
            portadaHash = snapshot.portadaHash ?: previo?.portadaHash,
            perfilVer = snapshot.perfilVer,
            esAmigo = !snapshot.privado || (previo?.esAmigo == true),
            esPrivado = snapshot.privado,
            ultimaVezVisto = System.currentTimeMillis()
        )
        peerProfileDao.insertOrUpdate(entidad)

        val usuarioPrevio = cacheUsuariosLocal?.obtener(snapshot.usuarioId)
        cacheUsuariosLocal?.guardar(
            Usuario(
                id = snapshot.usuarioId,
                nombre = entidad.nombre,
                foto = entidad.fotoPath,
                portada = entidad.portadaPath,
                descripcion = entidad.descripcion,
                hobby = snapshot.hobby ?: usuarioPrevio?.hobby,
                deporte = snapshot.deporte ?: usuarioPrevio?.deporte,
                ritmoMusical = snapshot.ritmoMusical ?: usuarioPrevio?.ritmoMusical,
                serieFavorita = snapshot.serieFavorita ?: usuarioPrevio?.serieFavorita,
                libroFavorito = snapshot.libroFavorito ?: usuarioPrevio?.libroFavorito,
                signoZodiacal = snapshot.signoZodiacal ?: usuarioPrevio?.signoZodiacal
            )
        )
    }

    override suspend fun registrarInteraccionMensaje(remitenteId: String, remitenteNombre: String?, remitenteFoto: String?) {
        if (remitenteId.isBlank()) return
        val previo = peerProfileDao.getPeer(remitenteId)
        val nombre = remitenteNombre?.takeIf { it.isNotBlank() && it != "Alguien" } ?: previo?.nombre ?: "Alguien"
        val foto = remitenteFoto?.takeIf { it.isNotBlank() } ?: previo?.fotoPath
        peerProfileDao.insertOrUpdate(
            PeerProfileEntity(
                usuarioId = remitenteId,
                nombre = nombre,
                descripcion = previo?.descripcion ?: "",
                fotoPath = foto,
                fotoHash = previo?.fotoHash,
                portadaPath = previo?.portadaPath,
                portadaHash = previo?.portadaHash,
                perfilVer = previo?.perfilVer ?: 0L,
                esAmigo = previo?.esAmigo ?: false,
                ultimaVezVisto = System.currentTimeMillis()
            )
        )
    }

    override suspend fun actualizarRutaMediaPar(usuarioId: String, hashSha256: String, rutaArchivo: String) {
        val peer = peerProfileDao.getPeer(usuarioId) ?: return
        var nuevoFotoPath = peer.fotoPath
        var nuevoPortadaPath = peer.portadaPath
        var modificado = false

        if (peer.fotoHash == hashSha256) {
            nuevoFotoPath = rutaArchivo
            modificado = true
        } else if (peer.portadaHash == hashSha256) {
            nuevoPortadaPath = rutaArchivo
            modificado = true
        } else if (peer.fotoHash == null && peer.portadaHash == null) {
            nuevoFotoPath = rutaArchivo
            modificado = true
        }

        if (modificado) {
            val actualizado = peer.copy(fotoPath = nuevoFotoPath, portadaPath = nuevoPortadaPath)
            peerProfileDao.insertOrUpdate(actualizado)
            val usuarioPrevio = cacheUsuariosLocal?.obtener(peer.usuarioId)
            cacheUsuariosLocal?.guardar(
                Usuario(
                    id = peer.usuarioId,
                    nombre = peer.nombre,
                    foto = nuevoFotoPath,
                    portada = nuevoPortadaPath,
                    descripcion = peer.descripcion,
                    hobby = usuarioPrevio?.hobby,
                    deporte = usuarioPrevio?.deporte,
                    ritmoMusical = usuarioPrevio?.ritmoMusical,
                    serieFavorita = usuarioPrevio?.serieFavorita,
                    libroFavorito = usuarioPrevio?.libroFavorito,
                    signoZodiacal = usuarioPrevio?.signoZodiacal
                )
            )
            cachePerfilLocal?.actualizarRutaMedia(usuarioId, hashSha256, rutaArchivo)
        }
    }

    override suspend fun podarDesconocidos(limiteTimestamp: Long) {
        peerProfileDao.podarDesconocidosAntiguos(limiteTimestamp)
    }
}

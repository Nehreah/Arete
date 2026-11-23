package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.data.local.room.dao.PeerProfileDao
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.model.Visibilidad
import com.renea.arete_social.domain.repository.RepositorioUsuarios
import com.renea.arete_social.feature.auth.GestorSesionLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Implementación 100% Local-First de [RepositorioUsuarios] para la red descentralizada P2P.
 * Gestiona de forma soberana y local los perfiles, avatares y usuarios.
 */
class RepositorioUsuariosLocal(
    private val cacheUsuariosLocal: CacheUsuariosLocal,
    private val peerProfileDao: PeerProfileDao? = null,
    private val miIdProveedor: () -> String? = { GestorSesionLocal.obtenerUltimoUsuario() }
) : RepositorioUsuarios {

    override suspend fun obtenerPorId(id: String): Usuario? {
        val miId = miIdProveedor()
        if (id == miId) {
            val enCache = cacheUsuariosLocal.obtener(id)
            val nombre = GestorSesionLocal.obtenerUltimoNombre() ?: enCache?.nombre ?: "Usuario"
            val foto = GestorSesionLocal.obtenerUltimaFoto() ?: enCache?.foto
            val portada = GestorSesionLocal.obtenerUltimaPortada() ?: enCache?.portada
            val visibilidad = GestorSesionLocal.obtenerUltimaVisibilidad()

            val usuarioPropio = (enCache ?: Usuario(id = id, nombre = nombre)).copy(
                nombre = nombre,
                foto = foto,
                portada = portada,
                estadoVisibilidad = visibilidad
            )
            cacheUsuariosLocal.guardar(usuarioPropio)
            return usuarioPropio
        }

        // Buscar en caché de usuarios conocidos o pares en Room
        val enCache = cacheUsuariosLocal.obtener(id)
        if (enCache != null) return enCache

        val peer = peerProfileDao?.getPeer(id)
        if (peer != null) {
            val u = Usuario(
                id = peer.usuarioId,
                nombre = peer.nombre,
                descripcion = peer.descripcion,
                foto = peer.fotoPath,
                portada = peer.portadaPath,
                perfilPrivado = peer.esPrivado
            )
            cacheUsuariosLocal.guardar(u)
            return u
        }

        return null
    }

    override fun observarUsuario(id: String): Flow<Usuario?> = flow {
        emit(obtenerPorId(id))
    }

    override suspend fun actualizarPerfil(id: String, nombre: String, foto: String?, descripcion: String) {
        val miId = miIdProveedor()
        if (id == miId) {
            GestorSesionLocal.guardarUltimoNombre(nombre)
            GestorSesionLocal.guardarUltimaFoto(foto)
        }
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = nombre)
        val modificado = actual.copy(
            nombre = nombre,
            foto = foto ?: actual.foto,
            descripcion = descripcion
        )
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarDatosPersonales(
        id: String,
        hobby: String?,
        deporte: String?,
        ritmoMusical: String?,
        serieFavorita: String?,
        libroFavorito: String?,
        signoZodiacal: String?
    ) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = GestorSesionLocal.obtenerUltimoNombre() ?: "Usuario")
        val modificado = actual.copy(
            hobby = hobby,
            deporte = deporte,
            ritmoMusical = ritmoMusical,
            serieFavorita = serieFavorita,
            libroFavorito = libroFavorito,
            signoZodiacal = signoZodiacal
        )
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarVisibilidad(id: String, visibilidad: Visibilidad) {
        val miId = miIdProveedor()
        if (id == miId) {
            GestorSesionLocal.guardarUltimaVisibilidad(visibilidad)
        }
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(estadoVisibilidad = visibilidad)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarPerfilPrivado(id: String, privado: Boolean) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(perfilPrivado = privado)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarParcheMichi(id: String, activo: Boolean) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(parcheActivo = activo)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarEstadoMichi(id: String, estado: String?, venceEn: Long?) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(estadoMichi = estado, estadoMichiHasta = venceEn)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarUbicacion(id: String, ubicacion: Ubicacion) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(ubicacion = ubicacion)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarClavePublicaE2ee(id: String, clavePublicaBase64: String) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(clavePublicaE2ee = clavePublicaBase64)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun actualizarClavePublicaFirma(id: String, clavePublicaBase64: String) {
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(clavePublicaFirma = clavePublicaBase64)
        cacheUsuariosLocal.guardar(modificado)
    }

    override suspend fun usuariosCercanos(origen: Ubicacion, radioMetros: Double): List<Usuario> {
        // Bug fix: se usa una ventana de 5 min para no mostrar peers que ya no están cerca.
        // Sin esto, un peer seguía apareciendo en el mapa GPS aunque se apagara el BT.
        val ventanaMs = 5 * 60_000L
        val limiteTimestamp = System.currentTimeMillis() - ventanaMs
        val todos = peerProfileDao?.getRecentPeers(limiteTimestamp) ?: emptyList()
        return todos.mapNotNull { peer ->
            cacheUsuariosLocal.obtener(peer.usuarioId)
        }.filter { u ->
            val loc = u.ubicacion ?: return@filter false
            val dist = distanciaMetros(origen.latitud, origen.longitud, loc.latitud, loc.longitud)
            dist <= radioMetros
        }
    }

    override suspend fun subirFoto(id: String, bytes: ByteArray, extension: String): String {
        val resultado = AlmacenMediaPerfil.guardarImagen(bytes, esAvatar = true, extension = extension.ifBlank { "jpg" })
        val ruta = resultado.rutaMiniatura
        actualizarPerfil(id, GestorSesionLocal.obtenerUltimoNombre() ?: "Usuario", ruta, "")
        return ruta
    }

    override suspend fun subirPortada(id: String, bytes: ByteArray, extension: String): String {
        val resultado = AlmacenMediaPerfil.guardarImagen(bytes, esAvatar = false, extension = extension.ifBlank { "jpg" })
        val ruta = resultado.rutaArchivo
        actualizarPortada(id, ruta)
        return ruta
    }

    override suspend fun actualizarPortada(id: String, url: String?) {
        val miId = miIdProveedor()
        if (id == miId) {
            GestorSesionLocal.guardarUltimaPortada(url)
        }
        val actual = obtenerPorId(id) ?: Usuario(id = id, nombre = "Usuario")
        val modificado = actual.copy(portada = url)
        cacheUsuariosLocal.guardar(modificado)
    }

    private fun distanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

package com.renea.arete_social.domain.repository

import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.model.Visibilidad
import kotlinx.coroutines.flow.Flow

interface RepositorioUsuarios {
    suspend fun obtenerPorId(id: String): Usuario?
    fun observarUsuario(id: String): Flow<Usuario?>
    suspend fun actualizarPerfil(id: String, nombre: String, foto: String?, descripcion: String)
    suspend fun actualizarDatosPersonales(
        id: String, hobby: String?, deporte: String?, ritmoMusical: String?,
        serieFavorita: String?, libroFavorito: String?, signoZodiacal: String?
    )
    suspend fun actualizarVisibilidad(id: String, visibilidad: Visibilidad)

    /**
     * Cambia la privacidad del perfil: público (todo el mundo ve estados, fotos y librería)
     * o privado (solo las amistades). Lanza si no se pudo guardar, para que la UI revierta el cambio.
     */
    suspend fun actualizarPerfilPrivado(id: String, privado: Boolean)
    suspend fun actualizarParcheMichi(id: String, activo: Boolean)
    suspend fun actualizarEstadoMichi(id: String, estado: String?, venceEn: Long?)
    suspend fun actualizarUbicacion(id: String, ubicacion: Ubicacion)
    suspend fun actualizarClavePublicaE2ee(id: String, clavePublicaBase64: String)
    suspend fun actualizarClavePublicaFirma(id: String, clavePublicaBase64: String)
    suspend fun usuariosCercanos(origen: Ubicacion, radioMetros: Double): List<Usuario>

    /**
     * Sube [bytes] como la foto de perfil de [id] (bucket "fotos-perfil", ruta
     * "{id}/foto.{extension}", con upsert para reemplazar la anterior) y
     * devuelve la URL pública resultante. No actualiza la fila de "usuarios"
     * por sí sola — quien llama debe guardar la URL devuelta con
     * [actualizarPerfil].
     */
    suspend fun subirFoto(id: String, bytes: ByteArray, extension: String): String

    /**
     * Sube [bytes] como la portada del perfil de [id] (bucket "fotos-perfil", ruta
     * "{id}/portada.{extension}", con upsert) y devuelve su URL pública. Como [subirFoto], no toca la
     * fila de "usuarios": quien llama guarda la URL con [actualizarPortada]. Lanza si falla.
     */
    suspend fun subirPortada(id: String, bytes: ByteArray, extension: String): String

    /** Guarda (o quita, con null) la URL de la portada en el perfil. Lanza si no se pudo guardar. */
    suspend fun actualizarPortada(id: String, url: String?)
}
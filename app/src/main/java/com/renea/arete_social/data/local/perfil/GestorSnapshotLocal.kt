package com.renea.arete_social.data.local.perfil

import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.Usuario
import com.renea.arete_social.domain.util.SyncLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gestor reactivo para la invalidación y reconstrucción automática del Snapshot y Manifiesto Local (Fase 2.3).
 *
 * Elimina la dependencia de abrir pantallas manualmente para construir la copia P2P.
 * Siempre que cambien los datos del perfil propio, estados de bitácora, obras o intereses,
 * regenera inmediatamente la caché en disco y memoria para ser entregada por BLE.
 */
class GestorSnapshotLocal(
    private val cachePerfilLocal: CachePerfilLocal,
    private val miIdProveedor: () -> String?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val TAG = "GestorSnapshotLocal"

    /**
     * Sincroniza los datos del usuario propio hacia el snapshot local P2P.
     */
    fun onPerfilPropioModificado(usuario: Usuario, esPrivado: Boolean = false) {
        val miId = miIdProveedor() ?: usuario.id.ifBlank { return }
        scope.launch {
            runCatching {
                val fotoHash = usuario.foto?.let { f ->
                    if (f.startsWith("/") || f.startsWith("file:")) {
                        AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(f, esAvatar = true)
                    } else null
                }
                val portadaHash = usuario.portada?.let { p ->
                    if (p.startsWith("/") || p.startsWith("file:")) {
                        AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(p, esAvatar = false)
                    } else null
                }

                cachePerfilLocal.actualizarMiPerfil(
                    miId = miId,
                    nombre = usuario.nombre,
                    descripcion = usuario.descripcion,
                    privado = esPrivado,
                    foto = usuario.foto,
                    portada = usuario.portada,
                    fotoHash = fotoHash,
                    portadaHash = portadaHash
                )
                SyncLogger.d(TAG, "Snapshot de perfil actualizado automáticamente para $miId")
            }.onFailure {
                SyncLogger.w(TAG, "Error actualizando snapshot de perfil: ${it.message}")
            }
        }
    }

    /**
     * Sincroniza la lista de publicaciones de bitácora hacia el snapshot P2P.
     */
    fun onBitacoraModificada(publicaciones: List<PublicacionPerfil>) {
        val miId = miIdProveedor() ?: return
        scope.launch {
            runCatching {
                cachePerfilLocal.actualizarMisEstados(miId, publicaciones)
                SyncLogger.d(TAG, "Bitácora actualizada en snapshot (${publicaciones.size} estados)")
            }.onFailure {
                SyncLogger.w(TAG, "Error actualizando bitácora en snapshot: ${it.message}")
            }
        }
    }

    /**
     * Sincroniza la librería literaria hacia el snapshot P2P.
     */
    fun onLibreriaModificada(obras: List<ObraLibreria>) {
        val miId = miIdProveedor() ?: return
        scope.launch {
            runCatching {
                cachePerfilLocal.actualizarMisObras(miId, obras)
                SyncLogger.d(TAG, "Librería actualizada en snapshot (${obras.size} obras)")
            }.onFailure {
                SyncLogger.w(TAG, "Error actualizando librería en snapshot: ${it.message}")
            }
        }
    }
}

package com.renea.arete_social.data.mesh

import android.util.Log
import com.renea.arete_social.domain.model.FeatureFlags
import com.renea.arete_social.domain.repository.ResolverPerfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "SincronizadorCercanos"
private const val PAUSA_CHAT_ACTIVO_MS = 2_500L
private const val COOLDOWN_SINCRONIZACION_MS = 10 * 60_000L // 10 minutos por par
private const val RETENCION_DESCONOCIDOS_MS = 7L * 24 * 3600 * 1000L // 7 días de retención

/**
 * Coordinador proactivo de sincronización para pares cercanos (Fase 3).
 *
 * ## Responsabilidades
 * - Recolección automática de perfiles al detectar un nuevo par por BLE o Wi-Fi Local.
 * - Cola con prioridad: amigos primero; desconocidos después (solo perfil privado).
 * - Pausa la sincronización si el usuario está enviando o recibiendo mensajes de chat.
 * - Anti-spam: máximo una petición por par cada 10 minutos.
 * - Poda automática de perfiles de desconocidos tras 7 días de inactividad.
 */
class SincronizadorCercanos(
    private val gestorPerfilBle: GestorPerfilBle,
    private val resolverPerfiles: ResolverPerfiles,
    private val miIdProveedor: () -> String?,
    private val esAmigo: (String) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var podaJob: Job? = null
    private val ultimaPeticionPorPeer = ConcurrentHashMap<String, Long>()
    private val pedidosEnVuelo = ConcurrentHashMap<String, Boolean>()
    private val ultimoChatMs = AtomicLong(0L)

    fun iniciar() {
        if (podaJob?.isActive == true) return

        // Poda periódica cada 6 horas
        podaJob = scope.launch {
            while (isActive) {
                runCatching {
                    val limite = System.currentTimeMillis() - RETENCION_DESCONOCIDOS_MS
                    resolverPerfiles.podarDesconocidos(limite)
                }
                delay(6 * 3600 * 1000L)
            }
        }
    }

    fun detener() {
        podaJob?.cancel()
        podaJob = null
    }

    fun notificarActividadChat() {
        ultimoChatMs.set(System.currentTimeMillis())
    }

    /**
     * Llamado cuando un dispositivo cercano es descubierto por ProximityManager o conectado al router.
     */
    fun onParDetectado(peerId: String) {
        if (!FeatureFlags.SYNC_AUTO) return
        val miId = miIdProveedor() ?: return
        if (peerId.isBlank() || peerId == miId) return

        val ahora = System.currentTimeMillis()
        val previo = ultimaPeticionPorPeer[peerId]
        if (previo != null && ahora - previo < COOLDOWN_SINCRONIZACION_MS) return

        if (pedidosEnVuelo.putIfAbsent(peerId, true) == true) return

        scope.launch {
            try {
                // Pausa preventiva si el usuario está usando el chat
                while (System.currentTimeMillis() - ultimoChatMs.get() < PAUSA_CHAT_ACTIVO_MS) {
                    delay(500)
                }

                // Amigos tienen prioridad (delay menor antes de pedir)
                val amigo = esAmigo(peerId)
                if (!amigo) {
                    delay(1200) // Ceder el paso en la radio a posibles amigos
                }

                ultimaPeticionPorPeer[peerId] = System.currentTimeMillis()
                Log.d(TAG, "SYNC_AUTO: Solicitando perfil proactivamente a $peerId (esAmigo=$amigo)")
                gestorPerfilBle.solicitar(peerId)
            } catch (e: Exception) {
                Log.w(TAG, "Error en recolección automática de $peerId: ${e.message}")
            } finally {
                pedidosEnVuelo.remove(peerId)
            }
        }
    }
}

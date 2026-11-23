package com.renea.arete_social.data.local.cartas

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.NuevaCarta
import com.renea.arete_social.domain.repository.RepositorioCartas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Implementación Local-First y peer-to-peer de [RepositorioCartas].
 *
 * Permite redactar, enviar, recibir y leer cartas de forma soberana sobre la malla Bluetooth / Wi-Fi
 * sin depender de conexión a servidores centrales.
 */
class RepositorioCartasLocalFirst(
    context: Context,
    private val messageRouter: MessageRouter,
    private val remoteRepo: RepositorioCartas? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : RepositorioCartas {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "antired_cartas_local_v1",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        const val CHAT_ID = "cartas-mesh"
        private const val PREFIJO_CARTA = "[CARTA:v1:"
        private const val CLAVE_RECIBIDAS = "cartas_recibidas_"
        private const val CLAVE_ENVIADAS = "cartas_enviadas_"
    }

    @Serializable
    private data class CartaPayload(
        val id: String,
        val remitenteId: String,
        val remitenteNombre: String,
        val destinatarioId: String,
        val destinatarioNombre: String,
        val contenido: String,
        val creadaEn: Long
    )

    private val _cartasActualizadas = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    val cartasActualizadas: SharedFlow<Unit> = _cartasActualizadas.asSharedFlow()

    init {
        // Escuchar cartas entrantes desde el router mesh
        scope.launch {
            messageRouter.todosLosPaquetesEntrantes()
                .filter { it.chatId == CHAT_ID && it.contenido.startsWith(PREFIJO_CARTA) }
                .collect { paquete ->
                    runCatching { procesarPaqueteEntrante(paquete) }
                }
        }
    }

    private fun procesarPaqueteEntrante(paquete: PaqueteMesh) {
        if (!paquete.contenido.startsWith(PREFIJO_CARTA) || !paquete.contenido.endsWith("]")) return
        val crudo = paquete.contenido.removePrefix(PREFIJO_CARTA).removeSuffix("]")
        val payload = runCatching { json.decodeFromString<CartaPayload>(crudo) }.getOrNull() ?: return

        val carta = Carta(
            id = payload.id,
            remitenteId = payload.remitenteId,
            remitenteNombre = payload.remitenteNombre,
            destinatarioId = payload.destinatarioId,
            destinatarioNombre = payload.destinatarioNombre,
            contenido = payload.contenido,
            leida = false,
            creadaEn = payload.creadaEn
        )

        guardarCarta(CLAVE_RECIBIDAS + payload.destinatarioId, carta)
        _cartasActualizadas.tryEmit(Unit)
    }

    private fun obtenerCartas(clave: String): List<Carta> = synchronized(this) {
        val raw = prefs.getString(clave, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Carta>>(raw) }.getOrDefault(emptyList())
    }

    private fun guardarCarta(clave: String, carta: Carta) = synchronized(this) {
        val actuales = obtenerCartas(clave).filterNot { it.id == carta.id }
        val nuevaLista = listOf(carta) + actuales
        prefs.edit().putString(clave, json.encodeToString(nuevaLista)).apply()
    }

    override suspend fun enviar(
        remitenteId: String,
        remitenteNombre: String,
        nueva: NuevaCarta
    ): Carta {
        val cartaId = UUID.randomUUID().toString()
        val ahora = System.currentTimeMillis()

        val carta = Carta(
            id = cartaId,
            remitenteId = remitenteId,
            remitenteNombre = remitenteNombre,
            destinatarioId = nueva.destinatarioId,
            destinatarioNombre = nueva.destinatarioNombre,
            contenido = nueva.contenido,
            leida = true,
            creadaEn = ahora
        )

        // Guardar en enviadas locales
        guardarCarta(CLAVE_ENVIADAS + remitenteId, carta)
        _cartasActualizadas.tryEmit(Unit)

        // Difundir sobre mesh P2P dirigida al destinatario
        val payload = CartaPayload(
            id = carta.id,
            remitenteId = carta.remitenteId,
            remitenteNombre = carta.remitenteNombre,
            destinatarioId = carta.destinatarioId,
            destinatarioNombre = carta.destinatarioNombre,
            contenido = carta.contenido,
            creadaEn = carta.creadaEn
        )
        val contenidoMesh = PREFIJO_CARTA + json.encodeToString(payload) + "]"

        val paquete = PaqueteMesh(
            id = "carta-$cartaId",
            chatId = CHAT_ID,
            remitenteId = remitenteId,
            destinatarioId = nueva.destinatarioId,
            contenido = contenidoMesh,
            ttl = PaqueteMesh.TTL_POR_DEFECTO,
            timestamp = ahora
        )
        val enviadoDirecto = messageRouter.enviarADestino(nueva.destinatarioId, paquete)
        if (!enviadoDirecto) {
            messageRouter.reenviar(paquete)
        }

        // Si hay backend remoto configurado, intentar replicar en background
        remoteRepo?.let { repo ->
            scope.launch {
                runCatching { repo.enviar(remitenteId, remitenteNombre, nueva) }
            }
        }

        return carta
    }

    override suspend fun recibidas(usuarioId: String): List<Carta> {
        val locales = obtenerCartas(CLAVE_RECIBIDAS + usuarioId)
        val remotas = runCatching { remoteRepo?.recibidas(usuarioId) }.getOrNull() ?: emptyList()
        return (locales + remotas).distinctBy { it.id }.sortedByDescending { it.creadaEn }
    }

    override suspend fun enviadas(usuarioId: String): List<Carta> {
        val locales = obtenerCartas(CLAVE_ENVIADAS + usuarioId)
        val remotas = runCatching { remoteRepo?.enviadas(usuarioId) }.getOrNull() ?: emptyList()
        return (locales + remotas).distinctBy { it.id }.sortedByDescending { it.creadaEn }
    }

    override suspend fun marcarLeida(cartaId: String) {
        synchronized(this) {
            for (key in prefs.all.keys.filter { it.startsWith(CLAVE_RECIBIDAS) }) {
                val lista = obtenerCartas(key)
                if (lista.any { it.id == cartaId }) {
                    val actualizada = lista.map { if (it.id == cartaId) it.copy(leida = true) else it }
                    prefs.edit().putString(key, json.encodeToString(actualizada)).apply()
                }
            }
        }
        scope.launch { runCatching { remoteRepo?.marcarLeida(cartaId) } }
    }

    override suspend fun eliminar(cartaId: String) {
        synchronized(this) {
            for (key in prefs.all.keys.filter { it.startsWith(CLAVE_RECIBIDAS) || it.startsWith(CLAVE_ENVIADAS) }) {
                val lista = obtenerCartas(key)
                if (lista.any { it.id == cartaId }) {
                    val filtrada = lista.filterNot { it.id == cartaId }
                    prefs.edit().putString(key, json.encodeToString(filtrada)).apply()
                }
            }
        }
        scope.launch { runCatching { remoteRepo?.eliminar(cartaId) } }
    }
}

package com.renea.arete_social.data.mesh

import android.util.Log
import com.renea.arete_social.data.local.perfil.CachePerfilLocal
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.FeatureFlags
import com.renea.arete_social.domain.model.ManifiestoPerfil
import com.renea.arete_social.domain.model.ProtocoloPerfilBle
import com.renea.arete_social.domain.model.ProtocoloPerfilV2
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.domain.model.TipoSeccionPerfil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GestorPerfilBle"
private const val ESPERA_RESPUESTA_MS = 8_000L
private const val INTERVALO_MIN_RESPUESTA_MS = 15_000L
private const val TAMANO_CHUNK_SECCION = 10_000

sealed interface ResultadoPerfilBle {
    data class Recibido(val perfil: SnapshotPerfil) : ResultadoPerfilBle
    data class ManifiestoRecibido(val manifiesto: ManifiestoPerfil) : ResultadoPerfilBle
    /** No hay ningún dispositivo cercano conectado por Bluetooth. */
    data object SinDispositivos : ResultadoPerfilBle
    /** Hay dispositivos, pero el dueño no respondió (lejos, app cerrada o desconexión). */
    data object SinRespuesta : ResultadoPerfilBle
}

/**
 * Gestor de sincronización de perfiles por Bluetooth y Wi-Fi Local.
 *
 * ## Reglas de Privacidad y Bifurcación (Fase 2)
 * - **Amigos:** Reciben todo el perfil (nombre, descripción, foto, portada, gustos, bitácora y librería).
 * - **Desconocidos:** Reciben un perfil privado que contiene ÚNICAMENTE el nombre para el mapa y chat,
 *   sin fotos, descripciones, bitácora ni librería.
 *
 * ## Compatibilidad
 * - Soporta el protocolo v1 tradicional (`[PERFIL_PEDIR:v1:...]` / `[PERFIL_DATOS:v1:...]`).
 * - Soporta el protocolo modular v2 (`[PERFIL_MANIF_REQ:v2:...]`, `[SEC_REQ:v1:...]`) cuando `FeatureFlags.SYNC_V2` está activo.
 */
class GestorPerfilBle(
    private val router: MessageRouter,
    private val cache: CachePerfilLocal,
    private val miIdProveedor: () -> String?,
    /** ¿[otroId] es una amistad aceptada según la copia local de amistades? */
    private val esAmigo: (String) -> Boolean,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onPerfilRecibido: ((SnapshotPerfil) -> Unit)? = null,
    private val resolverIdentidad: com.renea.arete_social.domain.repository.ResolverIdentidad? = null
) {
    private var job: Job? = null
    private val ultimaRespuestaA = ConcurrentHashMap<String, Long>()
    private val bufferSecciones = ConcurrentHashMap<String, Array<String?>>()
    private val bufferObras = ConcurrentHashMap<String, Array<String?>>()

    fun iniciar() {
        if (job?.isActive == true) return
        job = scope.launch {
            router.todosLosPaquetesEntrantes()
                .filter {
                    it.chatId == ProtocoloPerfilBle.CHAT_ID ||
                    it.chatId == ProtocoloPerfilV2.CHAT_ID_SECCIONES ||
                    it.chatId == ProtocoloPerfilV2.CHAT_ID_MANIFEST
                }
                .collect { paquete ->
                    runCatching { procesar(paquete) }
                        .onFailure { avisar("Paquete de perfil ignorado: ${it.message}") }
                }
        }
    }

    /**
     * Emite el manifiesto de perfil actualizado a todos los peers conectados.
     * Se llama inmediatamente después de que el usuario guarda cambios en su perfil
     * (nombre, foto, gustos, privacidad) para que los dispositivos cercanos se
     * enteren sin esperar el cooldown de [SincronizadorCercanos].
     *
     * El manifiesto es ligero (~200 bytes JSON) — las secciones completas y la foto
     * sólo se transferirán si el peer las solicita al comparar los hashes.
     */
    fun emitirPerfilActualizado() {
        val miId = miIdProveedor() ?: return
        scope.launch {
            runCatching {
                // Limpiar la caché de "últimas respuestas" para que los peers puedan
                // solicitar secciones inmediatamente al recibir el manifiesto actualizado.
                ultimaRespuestaA.clear()

                val ahora = reloj()
                val snapshot = cache.miSnapshot(miId) ?: return@runCatching
                // Si el perfil es privado, en broadcast general emitimos versión restringida;
                // si es público, emitimos con secciones disponibles.
                val amigo = !snapshot.privado
                val manifiesto = cache.miManifiesto(miId, esAmigo = amigo) ?: return@runCatching

                val paquete = PaqueteMesh(
                    id = "perfil-upd-${UUID.randomUUID()}",
                    chatId = ProtocoloPerfilV2.CHAT_ID_MANIFEST,
                    remitenteId = miId,
                    destinatarioId = null, // broadcast a todos los peers
                    contenido = ProtocoloPerfilV2.codificarManifiesto(manifiesto),
                    ttl = 1,
                    timestamp = ahora
                )
                enviar(paquete)
                Log.d(TAG, "Perfil actualizado emitido por broadcast (manifiesto v2, peers=${router.peersConectados.value})")
            }.onFailure { e ->
                Log.w(TAG, "No se pudo emitir perfil actualizado: ${e.message}")
            }
        }
    }

    private fun avisar(mensaje: String) {
        runCatching { Log.w(TAG, mensaje) }
    }

    private fun coinciden(id1: String?, id2: String?): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        val s1 = id1.replace("-", "").trim()
        val s2 = id2.replace("-", "").trim()
        if (s1.equals(s2, ignoreCase = true)) return true
        val res = resolverIdentidad ?: return false
        if (res.esMio(id1) && res.esMio(id2)) return true
        val ik1 = res.ikDe(id1) ?: id1
        val ik2 = res.ikDe(id2) ?: id2
        if (ik1.equals(ik2, ignoreCase = true)) return true
        val u1 = res.usuarioDe(id1) ?: id1
        val u2 = res.usuarioDe(id2) ?: id2
        return u1.replace("-", "").trim().equals(u2.replace("-", "").trim(), ignoreCase = true)
    }

    private fun verificarEsAmigo(pedidor: String): Boolean {
        if (esAmigo(pedidor)) return true
        val res = resolverIdentidad ?: return false
        val u = res.usuarioDe(pedidor)
        if (u != null && esAmigo(u)) return true
        val ik = res.ikDe(pedidor)
        if (ik != null && esAmigo(ik)) return true
        return false
    }

    private suspend fun procesar(p: PaqueteMesh) {
        val miId = miIdProveedor() ?: return
        if (coinciden(p.remitenteId, miId)) return // Eco propio

        // --- PROTOCOLO V2 (Manifiesto y Secciones) ---
        if (FeatureFlags.SYNC_V2) {
            // 1. Petición de Manifiesto v2
            ProtocoloPerfilV2.parsearPedirManifiesto(p.contenido)?.let { duenoPedido ->
                if (coinciden(duenoPedido, miId)) responderManifiestoV2(miId, p.remitenteId)
                return
            }

            // 2. Respuesta de Manifiesto v2 (directa o broadcast a peers)
            ProtocoloPerfilV2.decodificarManifiesto(p.contenido)?.let { manifiesto ->
                val paraMi = p.destinatarioId == null || p.destinatarioId.isBlank() || coinciden(p.destinatarioId, miId)
                if (paraMi && (coinciden(manifiesto.usuarioId, p.remitenteId) || manifiesto.usuarioId == p.remitenteId)) {
                    procesarManifiestoEntrante(manifiesto, remitente = p.remitenteId)
                }
                return
            }

            // 3. Petición de Sección modular v2
            ProtocoloPerfilV2.parsearPedirSeccion(p.contenido)?.let { peticionSec ->
                if (coinciden(peticionSec.duenoId, miId)) {
                    responderSeccionV2(miId, peticionSec.tipoSeccion, remitente = p.remitenteId)
                }
                return
            }

            // 4. Recepción de Sección modular v2
            ProtocoloPerfilV2.parsearSeccion(p.contenido)?.let { seccion ->
                if (coinciden(p.destinatarioId, miId) && (coinciden(seccion.duenoId, p.remitenteId) || seccion.duenoId == p.remitenteId)) {
                    val payloadCompleto = reensamblarSeccion(seccion)
                    if (payloadCompleto != null) {
                        cache.guardarSeccionRecibida(seccion.duenoId, seccion.tipoSeccion, payloadCompleto)
                        cache.recibido(seccion.duenoId)?.let { onPerfilRecibido?.invoke(it) }
                    }
                }
                return
            }

            // 5. Petición de Obra individual bajo demanda (REQ_OBRA)
            ProtocoloPerfilV2.parsearPedirObra(p.contenido)?.let { peticionObra ->
                if (coinciden(peticionObra.duenoId, miId)) {
                    responderObraV2(miId, peticionObra.obraId, remitente = p.remitenteId)
                }
                return
            }

            // 6. Recepción de Fragmento de Obra individual
            ProtocoloPerfilV2.parsearObraChunk(p.contenido)?.let { chunk ->
                if (coinciden(p.destinatarioId, miId) && (coinciden(chunk.duenoId, p.remitenteId) || chunk.duenoId == p.remitenteId)) {
                    procesarObraChunkEntrante(chunk)
                }
                return
            }
        }

        // --- PROTOCOLO V1 (Legado / Compatibilidad) ---
        ProtocoloPerfilBle.parsearPedir(p.contenido)?.let { duenoPedido ->
            if (coinciden(duenoPedido, miId)) responderV1(miId, p.remitenteId)
            return
        }

        if (!coinciden(p.destinatarioId, miId)) return
        val datos = ProtocoloPerfilBle.decodificarDatos(p.contenido) ?: return
        if (!coinciden(datos.usuarioId, p.remitenteId)) {
            avisar("Perfil de ${datos.usuarioId} enviado por ${p.remitenteId}: descartado")
            return
        }
        cache.guardarRecibido(datos)
        onPerfilRecibido?.invoke(datos)
    }

    // ── Respuestas del dueño ──────────────────────────────────────────────────

    private suspend fun responderV1(miId: String, pedidor: String) {
        val snapshot = cache.miSnapshot(miId) ?: return
        val amigo = verificarEsAmigo(pedidor)

        val ahora = reloj()
        val previa = ultimaRespuestaA[pedidor]
        if (previa != null && ahora - previa < INTERVALO_MIN_RESPUESTA_MS) return
        ultimaRespuestaA[pedidor] = ahora

        // Bifurcación: Amigo vs Desconocido
        val snapshotAEnviar = if (snapshot.privado && !amigo) {
            // Perfil privado: solo entregamos el nombre para chat y mapa
            SnapshotPerfil(
                usuarioId = miId,
                nombre = snapshot.nombre,
                privado = true,
                actualizadoEn = ahora
            )
        } else {
            snapshot
        }

        enviar(
            PaqueteMesh(
                id = "perfil-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilBle.CHAT_ID,
                remitenteId = miId,
                destinatarioId = pedidor,
                contenido = ProtocoloPerfilBle.codificarDatos(snapshotAEnviar),
                ttl = 1,
                timestamp = ahora
            ),
            destinatarioDirecto = pedidor
        )
    }

    private suspend fun responderManifiestoV2(miId: String, pedidor: String) {
        val amigo = verificarEsAmigo(pedidor)
        val manifiesto = cache.miManifiesto(miId, esAmigo = amigo) ?: return

        val ahora = reloj()
        val previa = ultimaRespuestaA[pedidor]
        if (previa != null && ahora - previa < INTERVALO_MIN_RESPUESTA_MS) return
        ultimaRespuestaA[pedidor] = ahora

        enviar(
            PaqueteMesh(
                id = "manif-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilV2.CHAT_ID_MANIFEST,
                remitenteId = miId,
                destinatarioId = pedidor,
                contenido = ProtocoloPerfilV2.codificarManifiesto(manifiesto),
                ttl = 1,
                timestamp = ahora
            ),
            destinatarioDirecto = pedidor
        )
    }

    private suspend fun responderSeccionV2(miId: String, seccion: TipoSeccionPerfil, remitente: String) {
        val snapshot = cache.miSnapshot(miId) ?: return
        val amigo = verificarEsAmigo(remitente)
        val esPrivado = snapshot.privado && !amigo
        if (esPrivado) return
        if (seccion == TipoSeccionPerfil.CARTAS && !amigo) return

        val jsonPayload = cache.obtenerDatosSeccion(miId, seccion) ?: return
        val chunks = jsonPayload.chunked(TAMANO_CHUNK_SECCION)
        val total = chunks.size

        chunks.forEachIndexed { indice, chunk ->
            val codificado = ProtocoloPerfilV2.codificarSeccion(
                duenoId = miId,
                seccion = seccion,
                indice = indice,
                total = total,
                payloadJson = chunk
            )

            enviar(
                PaqueteMesh(
                    id = "sec-${UUID.randomUUID()}",
                    chatId = ProtocoloPerfilV2.CHAT_ID_SECCIONES,
                    remitenteId = miId,
                    destinatarioId = remitente,
                    contenido = codificado,
                    ttl = 1,
                    timestamp = reloj()
                ),
                destinatarioDirecto = remitente
            )
        }
    }

    private fun reensamblarSeccion(seccion: ProtocoloPerfilV2.PaqueteSeccion): String? {
        if (seccion.total == 1) return seccion.payloadJson

        val clave = "${seccion.duenoId}:${seccion.tipoSeccion.name}:${seccion.total}"
        val chunks = bufferSecciones.computeIfAbsent(clave) { arrayOfNulls(seccion.total) }
        if (chunks.size != seccion.total) {
            bufferSecciones[clave] = arrayOfNulls(seccion.total)
            return null
        }
        chunks[seccion.indice] = seccion.payloadJson
        if (chunks.all { it != null }) {
            bufferSecciones.remove(clave)
            return chunks.joinToString(separator = "") { it.orEmpty() }
        }
        return null
    }

    private suspend fun responderObraV2(miId: String, obraId: String, remitente: String) {
        val snapshot = cache.miSnapshot(miId) ?: return
        if (snapshot.privado && !esAmigo(remitente)) return
        val obra = snapshot.obras.firstOrNull { it.id == obraId } ?: return
        // Si es PDF, solo viaja por Wi-Fi Local (Fase 4.3)
        if (obra.tipo.equals("PDF", ignoreCase = true)) {
            com.renea.arete_social.domain.util.SyncLogger.d(TAG, "Obra $obraId es PDF: bloqueada por BLE, requiere Wi-Fi Local")
            return
        }

        val chunks = obra.contenido.chunked(2000)
        val total = chunks.size
        chunks.forEachIndexed { indice, chunk ->
            val codificado = ProtocoloPerfilV2.codificarObraChunk(
                duenoId = miId,
                obraId = obraId,
                indice = indice,
                total = total,
                chunkTexto = chunk
            )
            enviar(
                PaqueteMesh(
                    id = "obra-${UUID.randomUUID()}",
                    chatId = ProtocoloPerfilV2.CHAT_ID_SECCIONES,
                    remitenteId = miId,
                    destinatarioId = remitente,
                    contenido = codificado,
                    ttl = 1,
                    timestamp = reloj()
                ),
                destinatarioDirecto = remitente
            )
        }
    }

    private fun procesarObraChunkEntrante(chunk: ProtocoloPerfilV2.PaqueteObra) {
        val clave = "${chunk.duenoId}:${chunk.obraId}"
        val buffer = bufferObras.computeIfAbsent(clave) { arrayOfNulls(chunk.total) }
        if (buffer.size != chunk.total) {
            bufferObras[clave] = arrayOfNulls(chunk.total)
            return
        }
        buffer[chunk.indice] = chunk.chunkTexto
        if (buffer.all { it != null }) {
            bufferObras.remove(clave)
            val textoCompleto = buffer.joinToString(separator = "") { it.orEmpty() }
            cache.actualizarContenidoObraRecibida(chunk.duenoId, chunk.obraId, textoCompleto)
            com.renea.arete_social.domain.util.SyncLogger.i(TAG, "Obra ${chunk.obraId} recibida completa (${textoCompleto.length} caracteres)")
        }
    }

    /** Solicita bajo demanda una obra literaria individual a un par amigo */
    suspend fun solicitarObra(duenoId: String, obraId: String) {
        val miId = miIdProveedor() ?: return
        enviar(
            PaqueteMesh(
                id = "req-obra-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilV2.CHAT_ID_SECCIONES,
                remitenteId = miId,
                destinatarioId = duenoId,
                contenido = ProtocoloPerfilV2.pedirObra(duenoId, obraId),
                ttl = 1,
                timestamp = reloj()
            ),
            destinatarioDirecto = duenoId
        )
    }

    private suspend fun procesarManifiestoEntrante(manifiesto: ManifiestoPerfil, remitente: String) {
        cache.guardarManifiestoRecibido(manifiesto)
        cache.recibido(manifiesto.usuarioId)?.let { onPerfilRecibido?.invoke(it) }

        // Si el perfil es público, o es privado pero somos amigos, solicitar las secciones
        val amigo = esAmigo(manifiesto.usuarioId) || esAmigo(remitente)
        if (!manifiesto.esPrivado || amigo) {
            for ((nombreSec, _) in manifiesto.secciones) {
                val tipo = runCatching { TipoSeccionPerfil.valueOf(nombreSec) }.getOrNull() ?: continue
                if (tipo == TipoSeccionPerfil.CARTAS && !amigo) continue
                solicitarSeccion(manifiesto.usuarioId, tipo)
            }
        }
    }

    private suspend fun solicitarSeccion(duenoId: String, seccion: TipoSeccionPerfil) {
        val miId = miIdProveedor() ?: return
        enviar(
            PaqueteMesh(
                id = "req-sec-${UUID.randomUUID()}",
                chatId = ProtocoloPerfilV2.CHAT_ID_SECCIONES,
                remitenteId = miId,
                destinatarioId = duenoId,
                contenido = ProtocoloPerfilV2.pedirSeccion(duenoId, seccion, version = 1L),
                ttl = 1,
                timestamp = reloj()
            ),
            destinatarioDirecto = duenoId
        )
    }

    private suspend fun enviar(paquete: PaqueteMesh, destinatarioDirecto: String? = null) {
        if (destinatarioDirecto != null) {
            val enviadoDirecto = router.enviarADestino(destinatarioDirecto, paquete)
            if (!enviadoDirecto) {
                router.reenviar(paquete)
            }
        } else {
            router.reenviar(paquete)
        }
        router.enrutar(paquete)
    }

    /**
     * Pide por Bluetooth el perfil de [duenoId] y espera su respuesta unos segundos.
     * En v2 emite petición de manifiesto; en v1 emite petición tradicional.
     */
    suspend fun solicitar(duenoId: String): ResultadoPerfilBle {
        val miId = miIdProveedor() ?: return ResultadoPerfilBle.SinDispositivos
        if (router.peersConectados.value == 0) return ResultadoPerfilBle.SinDispositivos

        val inicio = reloj()
        val contenidoPeticion = if (FeatureFlags.SYNC_V2) {
            ProtocoloPerfilV2.pedirManifiesto(duenoId)
        } else {
            ProtocoloPerfilBle.pedir(duenoId)
        }
        val canal = if (FeatureFlags.SYNC_V2) {
            ProtocoloPerfilV2.CHAT_ID_MANIFEST
        } else {
            ProtocoloPerfilBle.CHAT_ID
        }

        enviar(
            PaqueteMesh(
                id = "perfil-${UUID.randomUUID()}",
                chatId = canal,
                remitenteId = miId,
                destinatarioId = null,
                contenido = contenidoPeticion,
                ttl = 1,
                timestamp = inicio
            )
        )

        val recibido = withTimeoutOrNull(ESPERA_RESPUESTA_MS) {
            cache.observarRecibido(duenoId).first { it != null && it.actualizadoEn >= inicio }
        }
        return if (recibido != null) ResultadoPerfilBle.Recibido(recibido) else ResultadoPerfilBle.SinRespuesta
    }
}

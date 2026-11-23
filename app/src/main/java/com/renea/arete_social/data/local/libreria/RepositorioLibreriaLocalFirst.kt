package com.renea.arete_social.data.local.libreria

import android.content.Context
import android.content.SharedPreferences
import com.renea.arete_social.data.local.perfil.CachePerfilLocal
import com.renea.arete_social.domain.model.LimitesLibreria
import com.renea.arete_social.domain.model.NuevaObra
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.ResultadoCompartirMapa
import com.renea.arete_social.domain.model.TipoObra
import com.renea.arete_social.domain.model.aObras
import com.renea.arete_social.domain.repository.RepositorioLibreria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Implementación Local-First de [RepositorioLibreria].
 *
 * Permite redactar, guardar, consultar, compartir en el mapa y sincronizar
 * obras literarias (cuentos y poemarios) de forma soberana en el dispositivo
 * y a través de la malla BLE / Wi-Fi sin depender de servidores centrales.
 */
class RepositorioLibreriaLocalFirst(
    private val context: Context,
    private val cachePerfil: CachePerfilLocal? = null,
    private val remoteRepo: RepositorioLibreria? = null
) : RepositorioLibreria {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "antired_libreria_local_v1",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val CLAVE_OBRAS_PREFIJO = "libreria_obras_"
    }

    @Serializable
    private data class ObraLibreriaPayload(
        val id: String,
        val autorId: String,
        val tipo: String,
        val titulo: String,
        val contenido: String,
        val enlace: String? = null,
        val pdfRuta: String? = null,
        val pdfNombre: String? = null,
        val creadaEn: Long,
        val compartidaMapaEn: Long? = null,
        val autorNombre: String? = null
    )

    private fun ObraLibreriaPayload.toDomain(): ObraLibreria = ObraLibreria(
        id = id,
        autorId = autorId,
        tipo = runCatching { TipoObra.valueOf(tipo) }.getOrDefault(TipoObra.CUENTO),
        titulo = titulo,
        contenido = contenido,
        enlace = enlace,
        pdfRuta = pdfRuta,
        pdfNombre = pdfNombre,
        creadaEn = creadaEn,
        compartidaMapaEn = compartidaMapaEn,
        autorNombre = autorNombre
    )

    private fun ObraLibreria.toPayload(): ObraLibreriaPayload = ObraLibreriaPayload(
        id = id,
        autorId = autorId,
        tipo = tipo.name,
        titulo = titulo,
        contenido = contenido,
        enlace = enlace,
        pdfRuta = pdfRuta,
        pdfNombre = pdfNombre,
        creadaEn = creadaEn,
        compartidaMapaEn = compartidaMapaEn,
        autorNombre = autorNombre
    )

    private fun obtenerObrasLocales(autorId: String): List<ObraLibreria> = synchronized(this) {
        val raw = prefs.getString(CLAVE_OBRAS_PREFIJO + autorId, null)
        if (raw != null) {
            val payloads = runCatching { json.decodeFromString<List<ObraLibreriaPayload>>(raw) }.getOrDefault(emptyList())
            val obras = payloads.map { it.toDomain() }
            if (obras.isNotEmpty()) return obras
        }
        // Fallback 1: revisar si cachePerfil tiene snapshot de mis obras
        val snapshotObras = cachePerfil?.miSnapshot(autorId)?.aObras()
        if (!snapshotObras.isNullOrEmpty()) {
            guardarObrasLocales(autorId, snapshotObras)
            return snapshotObras
        }
        // Fallback 2: revisar si cachePerfil tiene snapshot recibido de este autor vía Bluetooth/mesh
        val snapshotRecibido = cachePerfil?.recibido(autorId)?.aObras()
        if (!snapshotRecibido.isNullOrEmpty()) {
            return snapshotRecibido
        }
        return emptyList()
    }

    private fun guardarObrasLocales(autorId: String, obras: List<ObraLibreria>) = synchronized(this) {
        val payloads = obras.map { it.toPayload() }
        prefs.edit().putString(CLAVE_OBRAS_PREFIJO + autorId, json.encodeToString(payloads)).apply()
        // Mantener actualizado el caché de perfil para BLE solo si es el propio perfil
        if (cachePerfil?.miSnapshot(autorId) != null) {
            cachePerfil.actualizarMisObras(autorId, obras)
        }
    }

    override suspend fun listar(autorId: String): List<ObraLibreria> {
        val locales = obtenerObrasLocales(autorId)
        val remoto = remoteRepo
        if (remoto != null) {
            val remotas = runCatching {
                withTimeoutOrNull(1500L) { remoto.listar(autorId) }
            }.getOrNull()
            if (!remotas.isNullOrEmpty()) {
                val combinadas = (locales + remotas).distinctBy { it.id }.sortedByDescending { it.creadaEn }
                guardarObrasLocales(autorId, combinadas)
                return combinadas
            }
        }
        return locales
    }

    override suspend fun crear(autorId: String, nueva: NuevaObra): ObraLibreria {
        val id = UUID.randomUUID().toString()
        val ahora = System.currentTimeMillis()

        // Guardar archivo PDF en almacenamiento interno local si se adjuntó
        var pdfRutaLocal: String? = null
        if (nueva.pdf != null && nueva.pdf.bytes.isNotEmpty()) {
            val dirPdfs = File(context.filesDir, "libreria_pdfs/$autorId").apply { mkdirs() }
            val archivoPdf = File(dirPdfs, "$id.pdf")
            archivoPdf.writeBytes(nueva.pdf.bytes)
            pdfRutaLocal = archivoPdf.absolutePath
        }

        val obra = ObraLibreria(
            id = id,
            autorId = autorId,
            tipo = nueva.tipo,
            titulo = nueva.titulo,
            contenido = nueva.contenido,
            enlace = nueva.enlace,
            pdfRuta = pdfRutaLocal,
            pdfNombre = nueva.pdf?.nombre,
            creadaEn = ahora
        )

        val actuales = obtenerObrasLocales(autorId).filterNot { it.id == id }
        val nuevaLista = listOf(obra) + actuales
        guardarObrasLocales(autorId, nuevaLista)

        // Intento remoto opcional de respaldo
        remoteRepo?.let {
            runCatching { withTimeoutOrNull(2000L) { it.crear(autorId, nueva) } }
        }

        return obra
    }

    override suspend fun eliminar(obra: ObraLibreria) {
        val autorId = obra.autorId
        val actuales = obtenerObrasLocales(autorId).filterNot { it.id == obra.id }
        guardarObrasLocales(autorId, actuales)

        // Eliminar PDF físico local si existía
        obra.pdfRuta?.let { ruta ->
            runCatching {
                val archivo = File(ruta)
                if (archivo.exists()) archivo.delete()
            }
        }

        remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.eliminar(obra) } }
        }
    }

    override suspend fun urlTemporalPdf(obra: ObraLibreria): String {
        // Si el PDF existe en el almacenamiento local, devolver URI local
        obra.pdfRuta?.let { ruta ->
            val archivo = File(ruta)
            if (archivo.exists()) return archivo.absolutePath
        }
        // Fallback remoto
        return remoteRepo?.let {
            runCatching { withTimeoutOrNull(2000L) { it.urlTemporalPdf(obra) } }.getOrNull()
        } ?: (obra.pdfRuta ?: "")
    }

    override suspend fun compartirEnMapa(obraId: String, autorId: String): ResultadoCompartirMapa {
        val obras = obtenerObrasLocales(autorId)
        val obra = obras.firstOrNull { it.id == obraId }
            ?: return ResultadoCompartirMapa.Error("Obra no encontrada")

        val ahora = System.currentTimeMillis()
        val ultimaCompartida = obras.mapNotNull { it.compartidaMapaEn }.maxOrNull()
        if (ultimaCompartida != null) {
            val transcurrido = ahora - ultimaCompartida
            if (transcurrido < LimitesLibreria.INTERVALO_COMPARTIR_MAPA_MS) {
                val minutosRestantes = ((LimitesLibreria.INTERVALO_COMPARTIR_MAPA_MS - transcurrido) / 60000L).toInt() + 1
                return ResultadoCompartirMapa.EnEspera(minutosRestantes)
            }
        }

        val actualizada = obra.copy(compartidaMapaEn = ahora)
        val nuevasObras = obras.map { if (it.id == obraId) actualizada else it }
        guardarObrasLocales(autorId, nuevasObras)

        remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.compartirEnMapa(obraId, autorId) } }
        }

        return ResultadoCompartirMapa.Exito
    }

    override suspend fun listarObrasCercanasMapa(autoresIds: List<String>): List<ObraLibreria> {
        val resultado = mutableListOf<ObraLibreria>()
        val ahora = System.currentTimeMillis()
        for (autor in autoresIds) {
            val obras = obtenerObrasLocales(autor)
            for (o in obras) {
                if (o.compartidaMapaEn != null && (ahora - o.compartidaMapaEn) < LimitesLibreria.VIGENCIA_MAPA_MS) {
                    resultado.add(o)
                }
            }
        }
        val remotas = remoteRepo?.let {
            runCatching { withTimeoutOrNull(1500L) { it.listarObrasCercanasMapa(autoresIds) } }.getOrNull()
        } ?: emptyList()

        return (resultado + remotas).distinctBy { it.id }
    }

    override suspend fun registrarLecturaObra(obraId: String, autorId: String, lectorId: String) {
        remoteRepo?.let {
            runCatching { withTimeoutOrNull(1000L) { it.registrarLecturaObra(obraId, autorId, lectorId) } }
        }
    }

    override fun escucharLecturasObras(autorId: String): Flow<String> {
        return remoteRepo?.escucharLecturasObras(autorId) ?: emptyFlow()
    }
}

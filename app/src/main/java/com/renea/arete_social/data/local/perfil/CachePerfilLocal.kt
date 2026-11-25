package com.renea.arete_social.data.local.perfil

import android.content.SharedPreferences
import com.renea.arete_social.data.local.media.AlmacenMediaPerfil
import com.renea.arete_social.domain.model.CartaResumenP2P
import com.renea.arete_social.domain.model.ManifiestoPerfil
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.PerfilIntereses
import com.renea.arete_social.domain.model.PublicacionPerfil
import com.renea.arete_social.domain.model.SeccionBaseDatos
import com.renea.arete_social.domain.model.SeccionBitacoraDatos
import com.renea.arete_social.domain.model.SeccionCartasDatos
import com.renea.arete_social.domain.model.SeccionGustosDatos
import com.renea.arete_social.domain.model.SeccionHeader
import com.renea.arete_social.domain.model.SeccionLibreriaDatos
import com.renea.arete_social.domain.model.SnapshotPerfil
import com.renea.arete_social.domain.model.TipoSeccionPerfil
import com.renea.arete_social.domain.model.aObrasSnapshot
import com.renea.arete_social.data.local.room.dao.PeerSectionDao
import com.renea.arete_social.data.local.room.entity.PeerBitacoraEntity
import com.renea.arete_social.data.local.room.entity.PeerGustosEntity
import com.renea.arete_social.data.local.room.entity.PeerLibreriaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Copia local del perfil en TEXTO para la sincronía por Bluetooth.
 *
 *  - "mío": lo que mi teléfono entrega a quien me lo pida por Bluetooth. Se actualiza cada vez que
 *    mi perfil, mis estados o mi librería cambian con internet. Se entrega a cualquiera si mi perfil es público; si es privado ([SnapshotPerfil.privado]), solo a mis amistades.
 *  - "recibidos": perfiles de otras personas recibidos por Bluetooth, para verlos sin conexión.
 *    Se descartan a los 7 días y se conservan como máximo [MAX_RECIBIDOS].
 *
 * @param interesesProveedor Proveedor perezoso de los gustos/intereses del usuario actual.
 *   Se inyecta para evitar dependencia circular. Puede devolver null si no hay intereses cargados.
 * @param cartasEnviadasProveedor Proveedor perezoso de las últimas cartas enviadas para el perfil P2P.
 *   Solo se usa al construir la sección CARTAS del manifiesto (solo para amigos).
 */
class CachePerfilLocal(
    private val prefs: SharedPreferences,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val interesesProveedor: (() -> PerfilIntereses)? = null,
    private val cartasEnviadasProveedor: (() -> List<CartaResumenP2P>)? = null,
    private val peerSectionDao: PeerSectionDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        const val MAX_RECIBIDOS = 50
        const val VIGENCIA_RECIBIDO_MS = 7L * 24 * 60 * 60 * 1000L
        private const val PREFIJO_MIO = "mio_"
        private const val PREFIJO_RECIBIDO = "rec_"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val recibidos = MutableStateFlow<Map<String, SnapshotPerfil>>(emptyMap())

    init {
        recibidos.value = prefs.all
            .filterKeys { it.startsWith(PREFIJO_RECIBIDO) }
            .mapNotNull { (_, v) -> (v as? String)?.let(::decodificar) }
            .filterNot(::vencido)
            .associateBy { it.usuarioId }
    }

    private fun vencido(s: SnapshotPerfil) = reloj() - s.actualizadoEn > VIGENCIA_RECIBIDO_MS

    private fun decodificar(texto: String): SnapshotPerfil? =
        runCatching { json.decodeFromString(SnapshotPerfil.serializer(), texto) }.getOrNull()?.let { s ->
            val fotoMigrada = AlmacenMediaPerfil.migrarSiEsDataUrl(s.foto, esAvatar = true)
            val portadaMigrada = AlmacenMediaPerfil.migrarSiEsDataUrl(s.portada, esAvatar = false)
            s.copy(foto = fotoMigrada, portada = portadaMigrada)
        }

    private fun leer(clave: String): SnapshotPerfil? = prefs.getString(clave, null)?.let(::decodificar)

    private fun escribir(clave: String, s: SnapshotPerfil) {
        prefs.edit().putString(clave, json.encodeToString(SnapshotPerfil.serializer(), s)).apply()
    }

    // ── Mi perfil (lo que entrego por Bluetooth) ─────────────────────────────

    fun miSnapshot(miId: String): SnapshotPerfil? = leer(PREFIJO_MIO + miId)

    private fun modificarMio(miId: String, cambio: (SnapshotPerfil) -> SnapshotPerfil) = synchronized(this) {
        val actual = miSnapshot(miId) ?: SnapshotPerfil(usuarioId = miId, privado = true)
        escribir(PREFIJO_MIO + miId, cambio(actual).copy(actualizadoEn = reloj()))
    }

    /** [privado] decide a quién se entrega por Bluetooth: false = a cualquiera cercano, true = solo a amistades. */
    fun actualizarMiPerfil(
        miId: String,
        nombre: String,
        descripcion: String,
        privado: Boolean,
        foto: String? = null,
        portada: String? = null,
        fotoHash: String? = null,
        portadaHash: String? = null,
        hobby: String? = null,
        deporte: String? = null,
        ritmoMusical: String? = null,
        serieFavorita: String? = null,
        libroFavorito: String? = null,
        signoZodiacal: String? = null
    ) = modificarMio(miId) {
        it.copy(
            nombre = nombre,
            descripcion = descripcion,
            privado = privado,
            foto = foto ?: it.foto,
            portada = portada ?: it.portada,
            fotoHash = fotoHash ?: it.fotoHash,
            portadaHash = portadaHash ?: it.portadaHash,
            hobby = hobby ?: it.hobby,
            deporte = deporte ?: it.deporte,
            ritmoMusical = ritmoMusical ?: it.ritmoMusical,
            serieFavorita = serieFavorita ?: it.serieFavorita,
            libroFavorito = libroFavorito ?: it.libroFavorito,
            signoZodiacal = signoZodiacal ?: it.signoZodiacal
        )
    }

    /**
     * Asegura que el snapshot local esté inicializado con los datos de sesión/caché,
     * permitiendo responder inmediatamente por Bluetooth aunque nunca se haya abierto la pantalla de Perfil.
     */
    fun asegurarMiSnapshotInicializado(
        miId: String,
        nombre: String?,
        descripcion: String?,
        foto: String?,
        portada: String?,
        privado: Boolean = false
    ) {
        val existente = miSnapshot(miId)
        if (existente == null && !nombre.isNullOrBlank()) {
            val fotoHash = foto?.let { f ->
                if (f.startsWith("/") || f.startsWith("file:")) {
                    AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(f, esAvatar = true)
                } else null
            }
            val portadaHash = portada?.let { p ->
                if (p.startsWith("/") || p.startsWith("file:")) {
                    AlmacenMediaPerfil.obtenerOGenerarHashMiniatura(p, esAvatar = false)
                } else null
            }
            actualizarMiPerfil(
                miId = miId,
                nombre = nombre,
                descripcion = descripcion ?: "",
                privado = privado,
                foto = foto,
                portada = portada,
                fotoHash = fotoHash,
                portadaHash = portadaHash
            )
        }
    }

    fun actualizarMisEstados(miId: String, publicaciones: List<PublicacionPerfil>) =
        modificarMio(miId) { it.copy(estados = publicaciones.aEstadosSnapshot(reloj())) }

    fun actualizarMisObras(miId: String, obras: List<ObraLibreria>) =
        modificarMio(miId) { it.copy(obras = obras.aObrasSnapshot()) }

    // ── Protocolo v2: Manifiesto y Secciones ──────────────────────────────────

    /**
     * Construye el manifiesto según el nivel de amistad:
     * - Amigo: Manifiesto completo con hashes de foto, portada y secciones disponibles.
     * - Desconocido: Manifiesto privado que contiene ÚNICAMENTE el nombre para chat y mapa.
     */
    fun miManifiesto(miId: String, esAmigo: Boolean): ManifiestoPerfil? {
        val snapshot = miSnapshot(miId) ?: return null
        val esPrivado = snapshot.privado && !esAmigo
        if (esPrivado) {
            return ManifiestoPerfil(
                v = 2,
                usuarioId = miId,
                nombre = snapshot.nombre,
                versionGlobal = snapshot.perfilVer,
                esPrivado = true,
                fotoHash = null,
                portadaHash = null,
                secciones = emptyMap(),
                actualizadoEn = snapshot.actualizadoEn
            )
        }

        val secMap = mutableMapOf<String, SeccionHeader>()
        if (snapshot.descripcion.isNotBlank()) {
            val bytesDesc = snapshot.descripcion.toByteArray(Charsets.UTF_8)
            secMap[TipoSeccionPerfil.BASE.name] = SeccionHeader(
                version = 1L,
                hashSha256 = AlmacenMediaPerfil.calcularHashSha256(bytesDesc),
                tamanoBytes = bytesDesc.size
            )
        }
        if (snapshot.estados.isNotEmpty()) {
            val jsonEstados = json.encodeToString(SeccionBitacoraDatos.serializer(), SeccionBitacoraDatos(snapshot.estados))
            val b = jsonEstados.toByteArray(Charsets.UTF_8)
            secMap[TipoSeccionPerfil.BITACORA.name] = SeccionHeader(
                version = 1L,
                hashSha256 = AlmacenMediaPerfil.calcularHashSha256(b),
                tamanoBytes = b.size
            )
        }
        if (snapshot.obras.isNotEmpty()) {
            val jsonObras = json.encodeToString(SeccionLibreriaDatos.serializer(), SeccionLibreriaDatos(snapshot.obras))
            val b = jsonObras.toByteArray(Charsets.UTF_8)
            secMap[TipoSeccionPerfil.LIBRERIA.name] = SeccionHeader(
                version = 1L,
                hashSha256 = AlmacenMediaPerfil.calcularHashSha256(b),
                tamanoBytes = b.size
            )
        }

        // Sección GUSTOS: anunciada si el usuario tiene intereses o datos personales registrados
        val intereses = interesesProveedor?.invoke()
        val todasLasEtiquetas = buildList {
            intereses?.categoriasSeleccionadas?.let { addAll(it) }
            intereses?.subcategoriasSeleccionadas?.let { addAll(it) }
            intereses?.gustosPersonalizados?.let { addAll(it) }
        }
        val tieneGustos = todasLasEtiquetas.isNotEmpty() ||
            !snapshot.hobby.isNullOrBlank() || !snapshot.deporte.isNullOrBlank() ||
            !snapshot.ritmoMusical.isNullOrBlank() || !snapshot.serieFavorita.isNullOrBlank() ||
            !snapshot.libroFavorito.isNullOrBlank() || !snapshot.signoZodiacal.isNullOrBlank()
        if (tieneGustos) {
            val jsonGustos = json.encodeToString(
                SeccionGustosDatos.serializer(),
                SeccionGustosDatos(
                    intereses = todasLasEtiquetas,
                    categorias = intereses?.categoriasSeleccionadas?.toList() ?: emptyList(),
                    hobby = snapshot.hobby,
                    deporte = snapshot.deporte,
                    ritmoMusical = snapshot.ritmoMusical,
                    serieFavorita = snapshot.serieFavorita,
                    libroFavorito = snapshot.libroFavorito,
                    signoZodiacal = snapshot.signoZodiacal
                )
            )
            val b = jsonGustos.toByteArray(Charsets.UTF_8)
            secMap[TipoSeccionPerfil.GUSTOS.name] = SeccionHeader(
                version = 1L,
                hashSha256 = AlmacenMediaPerfil.calcularHashSha256(b),
                tamanoBytes = b.size
            )
        }

        // Sección CARTAS: anunciada si hay cartas disponibles para compartir con amigos (opt-in)
        if (esAmigo) {
            val cartasParaCompartir = cartasEnviadasProveedor?.invoke() ?: emptyList()
            if (cartasParaCompartir.isNotEmpty()) {
                val jsonCartas = json.encodeToString(
                    SeccionCartasDatos.serializer(),
                    SeccionCartasDatos(cartasParaCompartir)
                )
                val b = jsonCartas.toByteArray(Charsets.UTF_8)
                secMap[TipoSeccionPerfil.CARTAS.name] = SeccionHeader(
                    version = 1L,
                    hashSha256 = AlmacenMediaPerfil.calcularHashSha256(b),
                    tamanoBytes = b.size
                )
            }
        }

        return ManifiestoPerfil(
            v = 2,
            usuarioId = miId,
            nombre = snapshot.nombre,
            versionGlobal = snapshot.perfilVer,
            esPrivado = false,
            fotoHash = snapshot.fotoHash,
            portadaHash = snapshot.portadaHash,
            secciones = secMap,
            actualizadoEn = snapshot.actualizadoEn
        )
    }

    fun obtenerDatosSeccion(miId: String, seccion: TipoSeccionPerfil): String? {
        val snapshot = miSnapshot(miId) ?: return null
        return when (seccion) {
            TipoSeccionPerfil.BASE -> json.encodeToString(SeccionBaseDatos.serializer(), SeccionBaseDatos(snapshot.descripcion))
            TipoSeccionPerfil.BITACORA -> json.encodeToString(SeccionBitacoraDatos.serializer(), SeccionBitacoraDatos(snapshot.estados))
            TipoSeccionPerfil.LIBRERIA -> json.encodeToString(SeccionLibreriaDatos.serializer(), SeccionLibreriaDatos(snapshot.obras))
            TipoSeccionPerfil.GUSTOS -> {
                val intereses = interesesProveedor?.invoke()
                val etiquetas = buildList {
                    intereses?.categoriasSeleccionadas?.let { addAll(it) }
                    intereses?.subcategoriasSeleccionadas?.let { addAll(it) }
                    intereses?.gustosPersonalizados?.let { addAll(it) }
                }
                val tieneGustos = etiquetas.isNotEmpty() ||
                    !snapshot.hobby.isNullOrBlank() || !snapshot.deporte.isNullOrBlank() ||
                    !snapshot.ritmoMusical.isNullOrBlank() || !snapshot.serieFavorita.isNullOrBlank() ||
                    !snapshot.libroFavorito.isNullOrBlank() || !snapshot.signoZodiacal.isNullOrBlank()
                if (!tieneGustos) return null
                json.encodeToString(
                    SeccionGustosDatos.serializer(),
                    SeccionGustosDatos(
                        intereses = etiquetas,
                        categorias = intereses?.categoriasSeleccionadas?.toList() ?: emptyList(),
                        hobby = snapshot.hobby,
                        deporte = snapshot.deporte,
                        ritmoMusical = snapshot.ritmoMusical,
                        serieFavorita = snapshot.serieFavorita,
                        libroFavorito = snapshot.libroFavorito,
                        signoZodiacal = snapshot.signoZodiacal
                    )
                )
            }
            TipoSeccionPerfil.CARTAS -> {
                val cartas = cartasEnviadasProveedor?.invoke() ?: return null
                if (cartas.isEmpty()) return null
                json.encodeToString(SeccionCartasDatos.serializer(), SeccionCartasDatos(cartas))
            }
            else -> null
        }
    }

    fun guardarManifiestoRecibido(m: ManifiestoPerfil) = synchronized(this) {
        val previo = recibido(m.usuarioId)
        val nuevo = if (m.esPrivado) {
            (previo ?: SnapshotPerfil(usuarioId = m.usuarioId)).copy(
                nombre = m.nombre,
                privado = true,
                descripcion = "",
                foto = null,
                fotoHash = null,
                portada = null,
                portadaHash = null,
                estados = emptyList(),
                obras = emptyList(),
                intereses = null, // Los desconocidos no exponen gustos
                actualizadoEn = reloj()
            )
        } else {
            (previo ?: SnapshotPerfil(usuarioId = m.usuarioId)).copy(
                nombre = m.nombre,
                privado = false,
                fotoHash = m.fotoHash ?: previo?.fotoHash,
                portadaHash = m.portadaHash ?: previo?.portadaHash,
                actualizadoEn = reloj()
                // intereses: se preservan del previo o se actualizan vía guardarSeccionRecibida(GUSTOS)
            )
        }
        guardarRecibido(nuevo)
    }

    fun guardarSeccionRecibida(usuarioId: String, seccion: TipoSeccionPerfil, jsonDatos: String) = synchronized(this) {
        val previo = recibido(usuarioId) ?: SnapshotPerfil(usuarioId = usuarioId)
        val actualizado = when (seccion) {
            TipoSeccionPerfil.BASE -> {
                val d = runCatching { json.decodeFromString(SeccionBaseDatos.serializer(), jsonDatos) }.getOrNull()
                if (d != null) previo.copy(descripcion = d.descripcion) else previo
            }
            TipoSeccionPerfil.BITACORA -> {
                val d = runCatching { json.decodeFromString(SeccionBitacoraDatos.serializer(), jsonDatos) }.getOrNull()
                if (d != null) {
                    peerSectionDao?.let { dao ->
                        scope.launch {
                            val entidades = d.estados.map { est ->
                                PeerBitacoraEntity(
                                    estadoId = est.id,
                                    usuarioId = usuarioId,
                                    texto = est.texto,
                                    fotoHash = est.fotoHash,
                                    fecha = est.ts
                                )
                            }
                            dao.insertOrUpdateBitacora(entidades)
                        }
                    }
                    previo.copy(estados = d.estados)
                } else previo
            }
            TipoSeccionPerfil.LIBRERIA -> {
                val d = runCatching { json.decodeFromString(SeccionLibreriaDatos.serializer(), jsonDatos) }.getOrNull()
                if (d != null) {
                    peerSectionDao?.let { dao ->
                        scope.launch {
                            val entidades = d.obras.map { o ->
                                PeerLibreriaEntity(
                                    obraId = o.id,
                                    usuarioId = usuarioId,
                                    titulo = o.titulo,
                                    sinopsis = "",
                                    tipo = o.tipo,
                                    caracteresTotal = o.contenido.length,
                                    contenido = o.contenido,
                                    actualizadoEn = o.ts
                                )
                            }
                            dao.insertOrUpdateLibreria(entidades)
                        }
                    }
                    previo.copy(obras = d.obras)
                } else previo
            }
            TipoSeccionPerfil.GUSTOS -> {
                val d = runCatching { json.decodeFromString(SeccionGustosDatos.serializer(), jsonDatos) }.getOrNull()
                if (d != null) {
                    peerSectionDao?.let { dao ->
                        scope.launch {
                            dao.insertOrUpdateGustos(
                                PeerGustosEntity(
                                    usuarioId = usuarioId,
                                    interesesJson = json.encodeToString(ListSerializer(String.serializer()), d.intereses),
                                    categoriasJson = json.encodeToString(ListSerializer(String.serializer()), d.categorias)
                                )
                            )
                        }
                    }
                    // Guardamos los intereses y gustos personales en el snapshot recibido
                    previo.copy(
                        intereses = d.intereses,
                        hobby = d.hobby ?: previo.hobby,
                        deporte = d.deporte ?: previo.deporte,
                        ritmoMusical = d.ritmoMusical ?: previo.ritmoMusical,
                        serieFavorita = d.serieFavorita ?: previo.serieFavorita,
                        libroFavorito = d.libroFavorito ?: previo.libroFavorito,
                        signoZodiacal = d.signoZodiacal ?: previo.signoZodiacal
                    )
                } else previo
            }
            TipoSeccionPerfil.CARTAS -> {
                // Las cartas recibidas vía manifiesto se almacenan separadamente; aquí solo
                // actualizamos el timestamp para indicar que el intercambio fue exitoso.
                previo.copy(actualizadoEn = reloj())
            }
            else -> previo
        }
        guardarRecibido(actualizado)
    }

    // ── Perfiles recibidos por Bluetooth ─────────────────────────────────────

    /** Guarda el perfil recibido con la hora de recepción de ESTE dispositivo (no la del emisor). */
    fun guardarRecibido(snapshot: SnapshotPerfil) = synchronized(this) {
        val guardado = snapshot.copy(actualizadoEn = reloj())
        escribir(PREFIJO_RECIBIDO + guardado.usuarioId, guardado)
        recibidos.update { it + (guardado.usuarioId to guardado) }
        podar()
    }

    fun recibido(duenoId: String): SnapshotPerfil? = recibidos.value[duenoId]?.takeUnless(::vencido)

    fun observarRecibido(duenoId: String): Flow<SnapshotPerfil?> =
        recibidos.map { it[duenoId] }.distinctUntilChanged()

    fun actualizarRutaMedia(usuarioId: String, hashSha256: String, rutaArchivo: String) = synchronized(this) {
        val actual = recibido(usuarioId) ?: return@synchronized
        var foto = actual.foto
        var portada = actual.portada
        var modificado = false
        if (actual.fotoHash == hashSha256) {
            foto = rutaArchivo
            modificado = true
        } else if (actual.portadaHash == hashSha256) {
            portada = rutaArchivo
            modificado = true
        } else if (actual.fotoHash == null && actual.portadaHash == null) {
            foto = rutaArchivo
            modificado = true
        }
        if (actual.estados.any { it.fotoHash == hashSha256 }) {
            modificado = true
        }
        if (modificado) {
            guardarRecibido(actual.copy(foto = foto, portada = portada))
        }
    }

    /**
     * Actualiza el contenido completo de una obra individual recibida bajo demanda (REQ_OBRA).
     */
    fun actualizarContenidoObraRecibida(duenoId: String, obraId: String, contenido: String) = synchronized(this) {
        val actual = recibido(duenoId) ?: return@synchronized
        val obrasActualizadas = actual.obras.map { o ->
            if (o.id == obraId) o.copy(contenido = contenido, recortada = false) else o
        }
        guardarRecibido(actual.copy(obras = obrasActualizadas))
        peerSectionDao?.let { dao ->
            scope.launch {
                dao.updateContenidoObra(obraId, contenido)
            }
        }
    }

    private fun podar() {
        val vigentes = recibidos.value.values.filterNot(::vencido).sortedByDescending { it.actualizadoEn }
        val conservar = vigentes.take(MAX_RECIBIDOS).map { it.usuarioId }.toSet()
        val sobrantes = recibidos.value.keys - conservar
        if (sobrantes.isEmpty()) return
        prefs.edit().also { ed -> sobrantes.forEach { ed.remove(PREFIJO_RECIBIDO + it) } }.apply()
        recibidos.update { it - sobrantes }
    }
}

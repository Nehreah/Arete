package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.model.Descubrimiento
import com.renea.arete_social.domain.model.ElementoVisible
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.EstadoDescubrimiento
import com.renea.arete_social.domain.model.Evento
import com.renea.arete_social.domain.model.Lugar
import com.renea.arete_social.domain.model.OrigenVisibilidad
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.model.aSnapshot
import com.renea.arete_social.domain.model.claveEntidad
import com.renea.arete_social.domain.repository.AlmacenDescubrimientos
import com.renea.arete_social.domain.util.Reloj
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Recuerda los lugares y eventos que descubres y decide cuáles ves en cada momento.
 *
 *  - [registrarCercanos] se llama cada vez que el mapa pregunta qué hay a 500 m: anota
 *    «última vez que estuve cerca».
 *  - [refrescar] se llama por temporizador (~1 min) para que lo vencido desaparezca aunque
 *    la persona no se mueva.
 *  - [visibles] es lo que se ve ahora (cerca, recientes y, con Fase 2, por pase).
 *
 * Las reglas viven en [PoliticaDescubrimiento]; aquí solo se coordina memoria, disco y tiempo.
 * [usuarioIdProveedor] permite cambiar de cuenta sin reconstruir el servicio: al detectar otro
 * usuario se vuelve a cargar lo suyo.
 */
class ServicioDescubrimiento(
    private val almacen: AlmacenDescubrimientos,
    private val usuarioIdProveedor: () -> String?,
    private val politica: PoliticaDescubrimiento = PoliticaDescubrimiento(),
    private val reloj: Reloj = Reloj.Sistema,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val registros = HashMap<String, Descubrimiento>()
    private val ultimaPersistencia = HashMap<String, Long>()
    private var usuarioCargado: String? = null
    private var ubicacion: Ubicacion? = null

    /** Usuario fijado por la sesión actual (incluye el de la sesión local sin cuenta). Tiene prioridad sobre el proveedor. */
    @Volatile
    private var usuarioVinculado: String? = null

    private val _visibles = MutableStateFlow<List<ElementoVisible>>(emptyList())

    /** Lo que ves ahora por descubrimiento físico o por pase. */
    val visibles: StateFlow<List<ElementoVisible>> = _visibles.asStateFlow()

    /**
     * Fija la persona de la sesión actual. Hace falta porque con una sesión local (sin cuenta) el
     * proveedor de la autenticación no conoce el id; quien sí lo conoce es la pantalla del mapa.
     */
    fun vincularUsuario(usuarioId: String) {
        usuarioVinculado = usuarioId
    }

    /** Carga lo guardado de la persona actual. Es seguro llamarlo varias veces. */
    suspend fun iniciar() {
        mutex.withLock {
            val uid = asegurarUsuario() ?: return
            recomponer(uid)
        }
    }

    /**
     * Registra lo que hay cerca en [ubicacion]. [lugares] y [eventos] son lo que el servidor
     * devolvió dentro del radio; igualmente se vuelve a comprobar la distancia aquí.
     */
    suspend fun registrarCercanos(ubicacion: Ubicacion, lugares: List<Lugar>, eventos: List<Evento>) {
        mutex.withLock {
            val uid = asegurarUsuario() ?: return
            this.ubicacion = ubicacion
            val ahora = reloj.ahora()
            val cercanos = lugares.map { it.aSnapshot() } + eventos.map { it.aSnapshot() }
            val pendientesDeGuardar = ArrayList<Descubrimiento>()

            // Reconciliación solo para EVENTOS: si el servidor ya no devuelve un evento
            // que estaba en caché dentro del radio actual, es porque fue eliminado.
            // Los lugares se omiten porque el servidor puede devolver listas parciales.
            val clavesEventosCercanos = eventos.map { it.aSnapshot().clave }.toSet()
            val eventosDesaparecidos = ArrayList<String>()
            for ((clave, registro) in registros) {
                if (registro.snapshot.tipo != TipoEntidadMapa.EVENTO) continue
                if (distanciaMetros(ubicacion, registro.snapshot.ubicacion) <= politica.radioMetros &&
                    clave !in clavesEventosCercanos
                ) {
                    eventosDesaparecidos += clave
                }
            }
            if (eventosDesaparecidos.isNotEmpty()) {
                eventosDesaparecidos.forEach {
                    registros.remove(it)
                    ultimaPersistencia.remove(it)
                }
                withContext(dispatcher) {
                    eventosDesaparecidos.forEach { almacen.eliminar(uid, it) }
                }
            }

            for (snapshot in cercanos) {
                if (distanciaMetros(ubicacion, snapshot.ubicacion) > politica.radioMetros) continue
                val actualizado = politica.marcarVistoCerca(registros[snapshot.clave] ?: Descubrimiento(snapshot), snapshot)
                registros[snapshot.clave] = actualizado
                // En memoria siempre; en disco no más de una vez cada 30 s por elemento.
                val ultima = ultimaPersistencia[snapshot.clave]
                if (ultima == null || ahora - ultima >= UMBRAL_PERSISTENCIA_MS) {
                    ultimaPersistencia[snapshot.clave] = ahora
                    pendientesDeGuardar += actualizado
                }
            }
            if (pendientesDeGuardar.isNotEmpty()) {
                withContext(dispatcher) { pendientesDeGuardar.forEach { almacen.guardar(uid, it) } }
            }
            recomponer(uid)
        }
    }

    /** Reevalúa vencimientos con la hora actual. */
    suspend fun refrescar() {
        mutex.withLock {
            val uid = asegurarUsuario() ?: return
            recomponer(uid)
        }
    }

    /**
     * Lo que esta persona puede compartir ahora: descubierto físicamente (cerca o reciente) y público.
     * Ordenado por prioridad: primero lo que tiene cerca y luego lo reciente, de más a menos fresco.
     */
    fun compartiblesPriorizados(): List<ElementoVisible> =
        _visibles.value
            .filter { it.origen == OrigenVisibilidad.CERCA || it.origen == OrigenVisibilidad.RECIENTE }
            .filter { politica.esCompartible(it.snapshot) }
            .sortedWith(compareBy<ElementoVisible>({ it.origen.ordinal }, { -(it.venceEn ?: Long.MAX_VALUE) }))

    /** Igual que [compartiblesPriorizados] pero solo las copias. */
    fun compartibles(): List<EntidadSnapshot> = compartiblesPriorizados().map { it.snapshot }

    /** Estado actual de una cosa concreta para esta persona. */
    suspend fun estadoDe(clave: String): EstadoDescubrimiento = mutex.withLock {
        val registro = registros[clave]
        val distancia = registro?.let { r -> ubicacion?.let { distanciaMetros(it, r.snapshot.ubicacion) } }
        politica.estadoDe(registro, distancia)
    }

    /**
     * Guarda un descubrimiento ya validado (lo usa la Fase 2, pases). Devuelve false si no se
     * pudo (sin sesión).
     */
    suspend fun registrar(descubrimiento: Descubrimiento): Boolean = mutex.withLock {
        val uid = asegurarUsuario() ?: return@withLock false
        registros[descubrimiento.snapshot.clave] = descubrimiento
        withContext(dispatcher) { almacen.guardar(uid, descubrimiento) }
        recomponer(uid)
        true
    }

    /** Estado de [clave] con lo que se sabe ahora, sin bloquear (solo lectura de memoria). */
    fun estadoActualSinBloqueo(clave: String): EstadoDescubrimiento {
        val registro = registros[clave]
        val distancia = registro?.let { r -> ubicacion?.let { distanciaMetros(it, r.snapshot.ubicacion) } }
        return politica.estadoDe(registro, distancia)
    }

    /** Olvida un descubrimiento concreto (p. ej. un pase que resultó no ser verdadero). */
    suspend fun olvidar(clave: String) {
        mutex.withLock {
            val uid = asegurarUsuario() ?: return
            registros.remove(clave)
            ultimaPersistencia.remove(clave)
            withContext(dispatcher) { almacen.eliminar(uid, clave) }
            recomponer(uid)
        }
    }

    /** Olvida un evento por su id (p. ej. al ser eliminado). */
    suspend fun olvidarEvento(eventoId: String) {
        olvidar(claveEntidad(TipoEntidadMapa.EVENTO, eventoId))
    }

    /** Olvida todo lo de la persona actual (cerrar sesión). */
    suspend fun limpiar() {
        mutex.withLock {
            val uid = usuarioCargado
            usuarioVinculado = null
            registros.clear()
            ultimaPersistencia.clear()
            ubicacion = null
            usuarioCargado = null
            _visibles.value = emptyList()
            if (uid != null) withContext(dispatcher) { almacen.limpiar(uid) }
        }
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    /** Devuelve el usuario actual y, si cambió desde la última vez, recarga lo suyo. */
    private suspend fun asegurarUsuario(): String? {
        val uid = usuarioVinculado ?: usuarioIdProveedor()
        if (uid == null) {
            if (usuarioCargado != null) {
                registros.clear(); ultimaPersistencia.clear(); ubicacion = null
                usuarioCargado = null
                _visibles.value = emptyList()
            }
            return null
        }
        if (uid != usuarioCargado) {
            registros.clear(); ultimaPersistencia.clear(); ubicacion = null
            usuarioCargado = uid
            val guardados = withContext(dispatcher) { almacen.cargar(uid) }
            guardados.forEach { registros[it.snapshot.clave] = it }
        }
        return uid
    }

    /** Recalcula lo visible, y de paso borra lo que ya venció del todo. */
    private suspend fun recomponer(uid: String) {
        val visibles = ArrayList<ElementoVisible>()
        val vencidos = ArrayList<String>()
        for ((clave, registro) in registros) {
            val distancia = ubicacion?.let { distanciaMetros(it, registro.snapshot.ubicacion) }
            val estado = politica.estadoDe(registro, distancia)
            val origen = politica.origenDe(estado)
            if (origen == null) {
                vencidos += clave
            } else {
                visibles += ElementoVisible(registro.snapshot, origen, politica.venceEnDe(estado))
            }
        }
        if (vencidos.isNotEmpty()) {
            vencidos.forEach { registros.remove(it); ultimaPersistencia.remove(it) }
            withContext(dispatcher) { vencidos.forEach { almacen.eliminar(uid, it) } }
        }
        _visibles.value = visibles.sortedWith(compareBy({ it.snapshot.tipo }, { it.snapshot.nombre.lowercase() }))
    }

    private companion object {
        const val UMBRAL_PERSISTENCIA_MS = 30_000L
    }
}

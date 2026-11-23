package com.renea.arete_social.feature.descubrimiento

import com.renea.arete_social.domain.descubrimiento.PoliticaDescubrimiento
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.EstadoDescubrimiento
import com.renea.arete_social.domain.model.PaseDescubrimiento
import com.renea.arete_social.domain.model.ProtocoloPaseBle
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.util.Reloj
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Qué pasó con un pase recibido. */
sealed interface ResultadoPase {
    /** Se aceptó. [nuevos] son los elementos que pasan a verse 24 h; [ignorados] ya los veías o no eran válidos. */
    data class Aceptado(val nuevos: List<EntidadSnapshot>, val ignorados: Int) : ResultadoPase

    /** El pase entero se descartó (firma inválida, no era para ti, muy viejo…). */
    data class Rechazado(val motivo: String) : ResultadoPase
}

/**
 * Crea y valida pases de descubrimiento. Lógica sin Bluetooth ni Android: la firma y la verificación
 * se inyectan (en la app son los de GestorIdentidadEcdsa) para poder probarlo.
 *
 * Al EMITIR ([crear]): solo lo descubierto físicamente (cerca o reciente), público, hasta 20 elementos,
 * lo más fresco primero, sin repetir lo ya ofrecido a esa persona.
 *
 * Al RECIBIR ([aceptar]) se exige, en este orden:
 *  1. el pase va dirigido a mí y su emisor es quien lo envió por la malla (nadie reenvía pases ajenos);
 *  2. la firma es válida;
 *  3. no es viejo ni «del futuro»; no trae más de 20 elementos;
 *  4. cada elemento es válido, público y no lo veo ya por ningún origen (un pase NO se renueva).
 * Después, con internet, [verificarPendientes] comprueba que lo recibido existe de verdad y olvida lo que no.
 */
class ServicioPases(
    private val descubrimiento: ServicioDescubrimiento,
    private val politica: PoliticaDescubrimiento = PoliticaDescubrimiento(),
    private val reloj: Reloj = Reloj.Sistema,
    private val firmar: (String) -> String?,
    private val miClavePublica: () -> String?,
    private val verificarFirma: (datos: String, firma: String, clavePublica: String) -> Boolean,
    private val verificador: VerificadorEntidades? = null
) {
    private val pendientesDeVerificar = ConcurrentHashMap<String, EntidadSnapshot>()

    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Avisos discretos para la interfaz, p. ej. «Alguien cercano te compartió 3 lugares y 1 evento por 24 h». */
    val avisos: SharedFlow<String> = _avisos.asSharedFlow()

    /**
     * Pase para [receptorId] con lo que puedo compartir, o null si no hay nada nuevo que ofrecerle o no
     * se pudo firmar. [yaOfrecidos] son las claves de lo que ya se le ofreció y sigue vigente.
     */
    fun crear(miId: String, receptorId: String, yaOfrecidos: Set<String> = emptySet()): PaseDescubrimiento? {
        var candidatos = descubrimiento.compartiblesPriorizados()
            .filter { it.snapshot.clave !in yaOfrecidos }
            .map { recortar(it.snapshot) }
            .take(PaseDescubrimiento.MAX_ENTIDADES)
        if (candidatos.isEmpty()) return null

        val clave = miClavePublica() ?: return null
        val base = PaseDescubrimiento(
            id = "pase-${UUID.randomUUID()}",
            emisorId = miId,
            receptorId = receptorId,
            emitidoEn = reloj.ahora(),
            entidades = candidatos
        )
        // Si el JSON no cabe en un paquete, se ofrecen menos elementos (los más frescos primero).
        var pase = base
        while (candidatos.size > 1 && ProtocoloPaseBle.codificar(pase.copy(firma = FIRMA_RESERVA, clavePublicaEmisor = clave)).length > ProtocoloPaseBle.MAX_JSON_CHARS) {
            candidatos = candidatos.dropLast(1)
            pase = base.copy(entidades = candidatos)
        }
        val firma = firmar(pase.datosFirmados()) ?: return null
        return pase.copy(firma = firma, clavePublicaEmisor = clave)
    }

    /** Valida y aplica un pase recibido. [remitenteTransporte] es quien lo envió por la malla (ya autenticado por el router). */
    suspend fun aceptar(pase: PaseDescubrimiento, remitenteTransporte: String, miId: String): ResultadoPase {
        val ahora = reloj.ahora()
        if (pase.receptorId != miId) return ResultadoPase.Rechazado("no va dirigido a mí")
        if (pase.emisorId != remitenteTransporte) return ResultadoPase.Rechazado("el emisor no es quien lo envió")
        if (pase.entidades.isEmpty()) return ResultadoPase.Rechazado("vacío")
        if (pase.entidades.size > PaseDescubrimiento.MAX_ENTIDADES) return ResultadoPase.Rechazado("demasiados elementos")
        if (pase.firma.isBlank() || pase.clavePublicaEmisor.isBlank() ||
            !runCatching { verificarFirma(pase.datosFirmados(), pase.firma, pase.clavePublicaEmisor) }.getOrDefault(false)
        ) return ResultadoPase.Rechazado("firma inválida")
        if (pase.emitidoEn > ahora + PaqueteMesh.TOLERANCIA_FUTURO_MS) return ResultadoPase.Rechazado("fecha futura")
        if (ahora - pase.emitidoEn > MAX_EDAD_PASE_MS) return ResultadoPase.Rechazado("demasiado viejo")

        val nuevos = ArrayList<EntidadSnapshot>()
        var ignorados = 0
        for (snapshot in pase.entidades.distinctBy { it.clave }) {
            if (!esValido(snapshot)) { ignorados++; continue }
            // No se renueva: si ya la veo por cualquier origen (cerca, reciente o pase vigente), se ignora.
            if (!politica.puedeAceptarPase(descubrimiento.estadoDe(snapshot.clave))) { ignorados++; continue }
            val registro = politica.registrarPase(existente = null, snapshot = snapshot, emisorId = pase.emisorId)
            // Un evento ya terminado hace más de un día no se recuerda.
            if (politica.estadoDe(registro, distanciaMetros = null) !is EstadoDescubrimiento.PorPase) { ignorados++; continue }
            if (descubrimiento.registrar(registro)) {
                nuevos += snapshot
                pendientesDeVerificar[snapshot.clave] = snapshot
            } else {
                ignorados++
            }
        }
        if (nuevos.isNotEmpty()) _avisos.tryEmit(mensajeDeAviso(nuevos))
        return ResultadoPase.Aceptado(nuevos, ignorados)
    }

    /**
     * Con internet, comprueba lo recibido por pase y olvida lo que no existe o no coincide. Sin internet no
     * hace nada (se vuelve a intentar luego). Devuelve cuántos elementos se olvidaron.
     */
    suspend fun verificarPendientes(): Int {
        val verificador = verificador ?: return 0
        var olvidados = 0
        for ((clave, snapshot) in pendientesDeVerificar.entries.toList()) {
            when (verificador.existe(snapshot)) {
                true -> pendientesDeVerificar.remove(clave)
                false -> {
                    pendientesDeVerificar.remove(clave)
                    descubrimiento.olvidar(clave)
                    olvidados++
                }
                null -> Unit // sin internet: se reintenta más tarde
            }
        }
        return olvidados
    }

    /** Cuántos elementos recibidos esperan comprobación (para tests y diagnóstico). */
    val cantidadPendientes: Int get() = pendientesDeVerificar.size

    // ── Internos ─────────────────────────────────────────────────────────────

    private fun recortar(s: EntidadSnapshot) = s.copy(
        nombre = s.nombre.take(ProtocoloPaseBle.MAX_NOMBRE),
        descripcion = s.descripcion.take(ProtocoloPaseBle.MAX_DESCRIPCION)
    )

    private fun esValido(s: EntidadSnapshot): Boolean =
        s.id.isNotBlank() && s.id.length <= ProtocoloPaseBle.MAX_ID &&
            s.nombre.isNotBlank() && s.nombre.length <= ProtocoloPaseBle.MAX_NOMBRE &&
            s.descripcion.length <= ProtocoloPaseBle.MAX_DESCRIPCION &&
            s.chatId.length <= ProtocoloPaseBle.MAX_CHAT_ID &&
            s.latitud in -90.0..90.0 && s.longitud in -180.0..180.0 &&
            politica.esCompartible(s) // solo lugares y eventos públicos

    private fun mensajeDeAviso(nuevos: List<EntidadSnapshot>): String {
        val lugares = nuevos.count { it.tipo == TipoEntidadMapa.LUGAR }
        val eventos = nuevos.count { it.tipo == TipoEntidadMapa.EVENTO }
        val partes = buildList {
            if (lugares > 0) add(if (lugares == 1) "1 lugar" else "$lugares lugares")
            if (eventos > 0) add(if (eventos == 1) "1 evento" else "$eventos eventos")
        }
        return "Alguien cercano te compartió ${partes.joinToString(" y ")} por 24 h"
    }

    private companion object {
        /** Un pase se ofrece en vivo: pasados 10 minutos desde su emisión ya no vale. */
        const val MAX_EDAD_PASE_MS = 10 * 60_000L
        val FIRMA_RESERVA = "x".repeat(100)
    }
}


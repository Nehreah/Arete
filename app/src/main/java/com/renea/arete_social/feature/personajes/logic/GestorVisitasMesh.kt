package com.renea.arete_social.feature.personajes.logic

import android.util.Log
import com.renea.arete_social.data.crypto.GestorIdentidadEcdsa
import com.renea.arete_social.domain.mesh.MessageRouter
import com.renea.arete_social.domain.mesh.PaqueteMesh
import com.renea.arete_social.feature.personajes.data.RepositorioCasa
import com.renea.arete_social.feature.personajes.data.RepositorioPersonajes
import com.renea.arete_social.feature.personajes.model.Recuerdo
import com.renea.arete_social.feature.personajes.model.Relacion
import com.renea.arete_social.feature.personajes.model.TipoRecuerdo
import com.renea.arete_social.feature.personajes.model.escena.AccionObjeto
import com.renea.arete_social.feature.personajes.model.visita.ProtocoloVisita
import com.renea.arete_social.feature.personajes.model.visita.ReciboVisita
import com.renea.arete_social.feature.personajes.model.visita.VisitaResultado
import com.renea.arete_social.feature.personajes.model.visita.VisitaSolicitud
import com.renea.arete_social.feature.tamagotchi.model.PetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private const val TAG = "GestorVisitasMesh"

sealed interface ResultadoVisitaSolicitud {
    data class Concedida(val resultado: VisitaResultado) : ResultadoVisitaSolicitud
    data object RechazadaOInvisible : ResultadoVisitaSolicitud
    data object SinRespuesta : ResultadoVisitaSolicitud
    data class Error(val motivo: String) : ResultadoVisitaSolicitud
}

/**
 * Gestor del ciclo de vida de visitas peer-to-peer sobre la red mesh.
 *
 * Garantiza:
 * 1. Acceso restringido a amistades confirmadas (`esAmigo`).
 * 2. Descarte sin respuesta si el nodo está en modo `INVISIBLE`.
 * 3. Intercambio de instantánea de casa firmada del anfitrión.
 * 4. Recibo mutuo criptográfico indispensable para persistir recuerdos y confianza.
 */
class GestorVisitasMesh(
    private val router: MessageRouter,
    private val repositorioCasa: RepositorioCasa,
    private val repositorioPersonajes: RepositorioPersonajes,
    private val gestorIdentidad: GestorIdentidadEcdsa?,
    private val miIdProveedor: () -> String?,
    private val miPetProveedor: () -> PetEntity?,
    private val esAmigo: (String) -> Boolean,
    private val esInvisible: () -> Boolean = { false },
    private val estaBloqueado: (String) -> Boolean = { false },
    private val reloj: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var jobEscucha: Job? = null
    private val _resultadosEntrantes = MutableSharedFlow<VisitaResultado>(extraBufferCapacity = 8)
    val resultadosEntrantes: SharedFlow<VisitaResultado> = _resultadosEntrantes.asSharedFlow()

    fun iniciar() {
        if (jobEscucha?.isActive == true) return
        jobEscucha = scope.launch {
            router.todosLosPaquetesEntrantes()
                .filter { it.chatId == ProtocoloVisita.CHAT_ID }
                .collect { paquete ->
                    runCatching { procesarPaquete(paquete) }
                        .onFailure { error -> runCatching { Log.w(TAG, "Error procesando visita: ${error.message}") } }
                }
        }
    }

    fun detener() {
        jobEscucha?.cancel()
        jobEscucha = null
    }

    private suspend fun procesarPaquete(p: PaqueteMesh) {
        val miId = miIdProveedor() ?: return
        if (p.remitenteId == miId) return // Eco propio

        // 1. Solicitud de Visita (soy el anfitrión)
        ProtocoloVisita.parsearSolicitud(p.contenido)?.let { sol ->
            if (p.destinatarioId == miId) {
                responderSolicitud(sol, p.remitenteId)
            }
            return
        }

        // 2. Resultado de Visita (soy el visitante recibiendo la casa del anfitrión)
        ProtocoloVisita.parsearResultado(p.contenido)?.let { res ->
            if (p.destinatarioId == miId && res.anfitrionOwnerId == p.remitenteId) {
                _resultadosEntrantes.emit(res)
            }
            return
        }

        // 3. Recibo de Visita (soy el anfitrión recibiendo el recibo firmado por el visitante)
        ProtocoloVisita.parsearRecibo(p.contenido)?.let { recibo ->
            if (p.destinatarioId == miId && recibo.anfitrionOwnerId == miId) {
                procesarReciboEntrante(recibo, p.remitenteId)
            }
            return
        }
    }

    private suspend fun responderSolicitud(sol: VisitaSolicitud, solicitanteId: String) {
        val miId = miIdProveedor() ?: return

        // Reglas de seguridad: amistades confirmadas, no invisible y no bloqueado
        if (esInvisible() || estaBloqueado(solicitanteId) || !esAmigo(solicitanteId)) {
            // Silencio intencional para no revelar presencia ni estado en modo invisible
            return
        }

        val miPet = miPetProveedor() ?: return
        val miCasa = repositorioCasa.obtenerCasa(miId, miPet.personajeId)

        val resultado = VisitaResultado(
            visitaId = sol.visitaId,
            anfitrionOwnerId = miId,
            anfitrionPersonajeId = miPet.personajeId,
            anfitrionNombre = miPet.name,
            casaSnapshot = miCasa,
            timestamp = reloj()
        )

        val firma = gestorIdentidad?.firmar(miCasa.datosFirmados())
        val resultadoFirmado = resultado.copy(firmaAnfitrion = firma)

        val paquete = PaqueteMesh(
            id = "visita-res-${UUID.randomUUID()}",
            chatId = ProtocoloVisita.CHAT_ID,
            remitenteId = miId,
            destinatarioId = solicitanteId,
            contenido = ProtocoloVisita.codificarResultado(resultadoFirmado),
            ttl = 2,
            timestamp = reloj()
        )

        router.reenviar(paquete)
        router.enrutar(paquete)
    }

    private fun procesarReciboEntrante(recibo: ReciboVisita, remitenteId: String) {
        val miPet = miPetProveedor() ?: return

        // Actualizar relación local del anfitrión
        val relacion = repositorioPersonajes.obtenerRelacion(remitenteId) ?: Relacion(
            otroPersonajeId = "",
            otroOwnerId = remitenteId,
            nombreOtro = "Amigo"
        )

        val resInteraccion = ReglasAfinidad.aplicarInteraccion(
            relacion = relacion,
            actividad = TipoActividad.JUGAR_CASA,
            miTemperamento = miPet.temperamento,
            otroTemperamento = com.renea.arete_social.feature.personajes.model.Temperamento.CURIOSO,
            ahoraMillis = reloj()
        )

        repositorioPersonajes.guardarRelacion(resInteraccion.relacionActualizada)

        // Registrar recuerdo en el anfitrión
        val recuerdo = Recuerdo(
            id = recibo.visitaId,
            fechaMillis = recibo.timestamp,
            tipo = TipoRecuerdo.VISITA_CASA,
            conQuienId = remitenteId,
            conQuienNombre = relacion.nombreOtro,
            lugar = "Mi Casa",
            textoCorto = recibo.notaVisitante ?: "Visita recibida en casa",
            firmaMutua = recibo.firmaVisitante
        )
        repositorioPersonajes.guardarRecuerdo(recuerdo)
    }

    /**
     * Solicita visitar la casa de un amigo por la red mesh.
     */
    suspend fun solicitarVisita(
        anfitrionId: String,
        nota: String? = null,
        timeoutMs: Long = 8000L
    ): ResultadoVisitaSolicitud {
        val miId = miIdProveedor() ?: return ResultadoVisitaSolicitud.Error("Sin sesión de usuario activa")
        if (esInvisible()) return ResultadoVisitaSolicitud.Error("No se pueden realizar visitas en modo Invisible")
        if (estaBloqueado(anfitrionId)) return ResultadoVisitaSolicitud.Error("El usuario se encuentra bloqueado")
        if (!esAmigo(anfitrionId)) return ResultadoVisitaSolicitud.Error("Solo puedes visitar a amistades confirmadas")

        val miPet = miPetProveedor() ?: return ResultadoVisitaSolicitud.Error("No tienes mascota activa")
        val visitaId = UUID.randomUUID().toString()

        val solicitud = VisitaSolicitud(
            visitaId = visitaId,
            remitenteOwnerId = miId,
            remitentePersonajeId = miPet.personajeId,
            remitenteNombre = miPet.name,
            timestamp = reloj(),
            nonce = UUID.randomUUID().toString(),
            nota = nota
        )

        val paquete = PaqueteMesh(
            id = "visita-req-${UUID.randomUUID()}",
            chatId = ProtocoloVisita.CHAT_ID,
            remitenteId = miId,
            destinatarioId = anfitrionId,
            contenido = ProtocoloVisita.codificarSolicitud(solicitud),
            ttl = 2,
            timestamp = reloj()
        )

        router.reenviar(paquete)
        router.enrutar(paquete)

        val resultado = withTimeoutOrNull(timeoutMs) {
            _resultadosEntrantes.filter { it.visitaId == visitaId && it.anfitrionOwnerId == anfitrionId }.first()
        }

        return if (resultado != null) {
            ResultadoVisitaSolicitud.Concedida(resultado)
        } else {
            ResultadoVisitaSolicitud.SinRespuesta
        }
    }

    /**
     * Concluye la visita ejecutando la interacción del visitante y emitiendo el recibo firmado.
     */
    suspend fun completarVisita(
        resultado: VisitaResultado,
        accion: AccionObjeto,
        objetoCatalogoId: String? = null,
        nota: String? = null
    ): ReciboVisita? {
        val miId = miIdProveedor() ?: return null
        val miPet = miPetProveedor() ?: return null

        val reciboBase = ReciboVisita(
            visitaId = resultado.visitaId,
            visitanteOwnerId = miId,
            anfitrionOwnerId = resultado.anfitrionOwnerId,
            accionRealizada = accion,
            objetoCatalogoId = objetoCatalogoId,
            notaVisitante = nota,
            timestamp = reloj()
        )

        val firma = gestorIdentidad?.firmar(reciboBase.datosFirmados())
        val reciboFirmado = reciboBase.copy(firmaVisitante = firma)

        // Enviar recibo firmado al anfitrión
        val paquete = PaqueteMesh(
            id = "visita-recibo-${UUID.randomUUID()}",
            chatId = ProtocoloVisita.CHAT_ID,
            remitenteId = miId,
            destinatarioId = resultado.anfitrionOwnerId,
            contenido = ProtocoloVisita.codificarRecibo(reciboFirmado),
            ttl = 2,
            timestamp = reloj()
        )
        router.reenviar(paquete)
        router.enrutar(paquete)

        // Guardar relación local del visitante
        val relacion = repositorioPersonajes.obtenerRelacion(resultado.anfitrionOwnerId) ?: Relacion(
            otroPersonajeId = resultado.anfitrionPersonajeId,
            otroOwnerId = resultado.anfitrionOwnerId,
            nombreOtro = resultado.anfitrionNombre
        )

        val resInteraccion = ReglasAfinidad.aplicarInteraccion(
            relacion = relacion,
            actividad = TipoActividad.JUGAR_CASA,
            miTemperamento = miPet.temperamento,
            otroTemperamento = com.renea.arete_social.feature.personajes.model.Temperamento.CURIOSO,
            ahoraMillis = reloj()
        )
        repositorioPersonajes.guardarRelacion(resInteraccion.relacionActualizada)

        // Guardar recuerdo local en el visitante
        val recuerdo = Recuerdo(
            id = resultado.visitaId,
            fechaMillis = reciboFirmado.timestamp,
            tipo = TipoRecuerdo.VISITA_CASA,
            conQuienId = resultado.anfitrionOwnerId,
            conQuienNombre = resultado.anfitrionNombre,
            lugar = "Casa de ${resultado.anfitrionNombre}",
            textoCorto = nota ?: "Visita realizada a casa de amigo",
            firmaMutua = firma
        )
        repositorioPersonajes.guardarRecuerdo(recuerdo)

        return reciboFirmado
    }
}

package com.renea.arete_social.feature.miausnake.integration

import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la sesión de Miau Snake abierta desde JUGAR (la UI solo lo dibuja). */
sealed interface SnakeSesionUi {
    /** Menú previo a la partida. Aquí «cancelar» no consume nada ni crea ninguna sesión. */
    data object Inactiva : SnakeSesionUi

    data object Abriendo : SnakeSesionUi

    /**
     * Partida en curso. [sesionId] es null en práctica (sin recompensa y sin tocar a Michi).
     * [partida] cambia en cada partida nueva para reiniciar la pantalla de juego.
     */
    data class Jugando(val sesionId: String?, val partida: Int) : SnakeSesionUi

    /** Partida terminada y resultado pendiente de guardarse en Michi. */
    data class Cerrando(val sesionId: String, val partida: Int) : SnakeSesionUi

    /** Resultado final. [cierre] es null en práctica. */
    data class Terminada(val cierre: CierreSnake?, val partida: Int) : SnakeSesionUi

    data class NoDisponible(val motivo: ResultadoCuidado) : SnakeSesionUi

    /** No se pudo abrir (o cargar el mapa). Se informa y se puede volver: NO se ejecuta Automático. */
    data class ErrorCarga(val causa: Throwable) : SnakeSesionUi
}

/**
 * Máquina de estados de una partida de Miau Snake. Sin Android: se prueba en JVM.
 * Garantías (las comprueban los tests):
 *  - Abrir no consume nada; volver/cancelar antes de jugar tampoco.
 *  - Doble toque: una acción repetida mientras ya está en curso se ignora.
 *  - Un resultado se envía al puente una sola vez por partida, y un cierre fallido se puede reintentar.
 *  - Si el jugador se va mientras se abría la sesión, esa sesión se descarta (sin recompensa).
 */
class SnakeSessionCoordinator(
    private val bridge: MichiPlayBridge,
    private val scope: CoroutineScope
) {
    private val _estado = MutableStateFlow<SnakeSesionUi>(SnakeSesionUi.Inactiva)
    val estado: StateFlow<SnakeSesionUi> = _estado.asStateFlow()

    private var partida = 0

    /** Sube cada vez que el jugador abandona/vuelve: invalida aperturas que estaban en vuelo. */
    private var generacion = 0

    /** Resultado pendiente de cerrarse (para reintentar si el guardado falló). */
    private var pendiente: Pair<String, ResultadoSnake.Terminada>? = null

    private fun puedeEmpezar(): Boolean = when (_estado.value) {
        SnakeSesionUi.Inactiva, is SnakeSesionUi.Terminada, is SnakeSesionUi.NoDisponible, is SnakeSesionUi.ErrorCarga -> true
        SnakeSesionUi.Abriendo, is SnakeSesionUi.Jugando, is SnakeSesionUi.Cerrando -> false
    }

    /** Empieza una partida que cuenta para Michi. Primero abre la sesión; si Michi no puede jugar, no empieza. */
    fun jugarConMichi() {
        if (!puedeEmpezar()) return
        val miGeneracion = ++generacion
        _estado.value = SnakeSesionUi.Abriendo
        scope.launch {
            val apertura = bridge.abrir()
            if (miGeneracion != generacion) {
                // El jugador se fue mientras se abría: la sesión queda descartada y no se muestra nada.
                if (apertura is AperturaSnake.Lista) bridge.cerrar(apertura.sesionId, ResultadoSnake.Abandonada)
                return@launch
            }
            _estado.value = when (apertura) {
                is AperturaSnake.Lista -> SnakeSesionUi.Jugando(apertura.sesionId, ++partida)
                is AperturaSnake.NoDisponible -> SnakeSesionUi.NoDisponible(apertura.motivo)
                is AperturaSnake.Error -> SnakeSesionUi.ErrorCarga(apertura.causa)
            }
        }
    }

    /** Partida de práctica: sin recompensa, sin coste y sin tocar a Michi. Funciona aunque ella no pueda jugar. */
    fun jugarPractica() {
        if (!puedeEmpezar()) return
        generacion++
        _estado.value = SnakeSesionUi.Jugando(sesionId = null, partida = ++partida)
    }

    /** La UI no pudo preparar el juego (p. ej. el mapa no cargó). Informa y deja volver. */
    fun errorDeCarga(causa: Throwable) {
        if (!puedeEmpezar()) return
        generacion++
        _estado.value = SnakeSesionUi.ErrorCarga(causa)
    }

    /** La partida llegó a victoria o choque. Se envía al puente UNA vez; repeticiones del evento se ignoran. */
    fun partidaTerminada(resultado: ResultadoSnake.Terminada) {
        val actual = _estado.value as? SnakeSesionUi.Jugando ?: return
        val id = actual.sesionId
        if (id == null) {
            _estado.value = SnakeSesionUi.Terminada(cierre = null, partida = actual.partida)
            return
        }
        _estado.value = SnakeSesionUi.Cerrando(id, actual.partida)
        pendiente = id to resultado
        cerrar(id, resultado, actual.partida)
    }

    /** El guardado falló (SnakeSesionUi.Terminada con CierreSnake.Error): reintenta con el mismo resultado. */
    fun reintentarCierre() {
        val actual = _estado.value as? SnakeSesionUi.Terminada ?: return
        if (actual.cierre !is CierreSnake.Error) return
        val (id, resultado) = pendiente ?: return
        _estado.value = SnakeSesionUi.Cerrando(id, actual.partida)
        cerrar(id, resultado, actual.partida)
    }

    private fun cerrar(id: String, resultado: ResultadoSnake, numPartida: Int) {
        scope.launch {
            val cierre = bridge.cerrar(id, resultado)
            if (cierre !is CierreSnake.Error) pendiente = null
            // Solo se publica si seguimos esperando ESTE cierre (el jugador pudo haberse ido).
            val actual = _estado.value
            if (actual is SnakeSesionUi.Cerrando && actual.sesionId == id && actual.partida == numPartida) {
                _estado.value = SnakeSesionUi.Terminada(cierre, numPartida)
            }
        }
    }

    /** El jugador sale a mitad de partida: la sesión se cierra SIN recompensa ni coste. */
    fun abandonar() {
        generacion++
        val actual = _estado.value
        if (actual is SnakeSesionUi.Jugando) {
            _estado.value = SnakeSesionUi.Inactiva
            actual.sesionId?.let { id -> scope.launch { bridge.cerrar(id, ResultadoSnake.Abandonada) } }
        } else {
            volverAlMenu()
        }
    }

    /** Vuelve al menú (o cierra). No toca a Michi: lo ya cerrado no se reabre y lo no abierto no se abre. */
    fun volver() {
        if (_estado.value is SnakeSesionUi.Jugando) abandonar() else volverAlMenu()
    }

    private fun volverAlMenu() {
        generacion++ // invalida una apertura en vuelo (si llega, se descarta)
        // Cerrando: el resultado ya está en camino hacia el puente y se queda como está.
        if (_estado.value !is SnakeSesionUi.Cerrando) _estado.value = SnakeSesionUi.Inactiva
    }

    /**
     * «Reiniciar» o «Jugar otra vez»: la partida actual (si la hay) se abandona sin recompensa y se
     * empieza otra. Con Michi se vuelve a comprobar si puede jugar; si no puede (p. ej. en espera tras
     * recibir la recompensa) se muestra el motivo y se ofrece la práctica.
     */
    fun reiniciar(conMichi: Boolean) {
        val actual = _estado.value
        if (actual is SnakeSesionUi.Cerrando || actual is SnakeSesionUi.Abriendo) return
        if (actual is SnakeSesionUi.Jugando) {
            generacion++
            _estado.value = SnakeSesionUi.Inactiva
            actual.sesionId?.let { id -> scope.launch { bridge.cerrar(id, ResultadoSnake.Abandonada) } }
        }
        if (conMichi) jugarConMichi() else jugarPractica()
    }
}

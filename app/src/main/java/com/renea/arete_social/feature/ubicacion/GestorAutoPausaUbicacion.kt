package com.renea.arete_social.feature.ubicacion

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.renea.arete_social.data.local.ajustes.AjustesInactividadUbicacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AutoPausaUbicacion"

/**
 * Supervisa el ciclo de vida de la aplicación para detectar inactividad prolongada en segundo plano
 * (por defecto 30 minutos). Si el usuario no vuelve a la app en ese plazo, pausa automáticamente
 * la compartición de ubicación para ahorrar batería y proteger su privacidad.
 */
class GestorAutoPausaUbicacion(
    private val context: Context,
    private val scope: CoroutineScope,
    private val proveedorMinutos: (() -> Int)? = null,
    private val notificador: ((Int) -> Unit)? = null,
    private val canceladorNotificacion: (() -> Unit)? = null,
    private val onPausarUbicacion: (suspend () -> Unit)? = null,
    private val onReanudarUbicacion: (suspend () -> Unit)? = null
) : DefaultLifecycleObserver {

    private val _estaPausado = MutableStateFlow(false)
    val estaPausadoFlow: StateFlow<Boolean> = _estaPausado.asStateFlow()

    @Volatile
    private var temporizadorJob: Job? = null

    private var registrado = false

    fun estaPausado(): Boolean = _estaPausado.value

    fun iniciar() {
        if (!registrado) {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                registrado = true
                Log.d(TAG, "GestorAutoPausaUbicacion registrado en el ciclo de vida del proceso.")
            }.onFailure {
                Log.w(TAG, "No se pudo registrar observer de ciclo de vida: ${it.message}")
            }
        }
    }

    fun detener() {
        if (registrado) {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
                registrado = false
            }
        }
        cancelarTemporizador()
    }

    override fun onStop(owner: LifecycleOwner) {
        val minutos = proveedorMinutos?.invoke() ?: AjustesInactividadUbicacion.minutosInactividad(context)
        if (minutos <= 0) {
            Log.d(TAG, "Auto-pausa de ubicación desactivada por configuración del usuario.")
            return
        }

        cancelarTemporizador()
        Log.d(TAG, "App pasó a segundo plano. Iniciando temporizador de auto-pausa de $minutos min.")
        temporizadorJob = scope.launch {
            delay(minutos * 60_000L)
            activarPausa(minutos)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        cancelarTemporizador()
        if (canceladorNotificacion != null) {
            canceladorNotificacion.invoke()
        } else {
            UbicacionInactividadNotificationHelper.cancelarNotificacion(context)
        }

        if (_estaPausado.value) {
            Log.d(TAG, "App regresó a primer plano. Reanudando compartición de ubicación.")
            _estaPausado.value = false
            scope.launch {
                runCatching { onReanudarUbicacion?.invoke() }
            }
        }
    }

    /** Permite forzar la pausa (utilizado en tests o flujos manuales) */
    internal suspend fun activarPausa(minutos: Int) {
        _estaPausado.value = true
        Log.i(TAG, "Ubicación pausada automáticamente tras $minutos min de inactividad.")
        if (notificador != null) {
            notificador.invoke(minutos)
        } else {
            UbicacionInactividadNotificationHelper.notificarUbicacionPausada(context, minutos)
        }
        runCatching { onPausarUbicacion?.invoke() }
    }

    private fun cancelarTemporizador() {
        temporizadorJob?.cancel()
        temporizadorJob = null
    }
}

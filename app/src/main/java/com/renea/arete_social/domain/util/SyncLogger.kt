package com.renea.arete_social.domain.util

import android.util.Log

/**
 * Logger unificado para diagnóstico y telemetría del plano de sincronización P2P (Fase 0).
 * Utiliza siempre el prefijo "SYNC:" para facilitar filtrado por logcat.
 * En entornos de prueba unitaria sin mock de Android, amortigua de forma segura.
 */
object SyncLogger {
    private const val TAG = "SYNC"

    fun d(componente: String, mensaje: String) {
        runCatching { Log.d(TAG, "[$componente] $mensaje") }
    }

    fun i(componente: String, mensaje: String) {
        runCatching { Log.i(TAG, "[$componente] $mensaje") }
    }

    fun w(componente: String, mensaje: String, causa: Throwable? = null) {
        runCatching {
            if (causa != null) {
                Log.w(TAG, "[$componente] $mensaje", causa)
            } else {
                Log.w(TAG, "[$componente] $mensaje")
            }
        }
    }

    fun e(componente: String, mensaje: String, causa: Throwable? = null) {
        runCatching {
            if (causa != null) {
                Log.e(TAG, "[$componente] $mensaje", causa)
            } else {
                Log.e(TAG, "[$componente] $mensaje")
            }
        }
    }

    /** Registro de paquetes enviados, descartados o reintentados */
    fun metrica(tipo: String, peerId: String, canal: String, detalle: String = "") {
        runCatching { Log.i(TAG, "[METRICA] tipo=$tipo peer=$peerId canal=$canal $detalle".trim()) }
    }
}

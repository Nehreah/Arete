package com.renea.arete_social.feature.tamagotchi.mensajes

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Almacena el historial persistente de avisos y mensajes que Michi le da a su dueño.
 * Solo lectura para el usuario: Michi es el único que escribe aquí.
 */
class GestorHistorialMichi(
    context: Context,
    private val ownerId: String
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "antired_michi_chat_$ownerId",
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _mensajes = MutableStateFlow<List<MensajeMichiLocal>>(emptyList())
    val mensajes: StateFlow<List<MensajeMichiLocal>> = _mensajes.asStateFlow()

    init {
        cargarMensajes()
    }

    private fun cargarMensajes() {
        val raw = prefs.getString(CLAVE_MENSAJES, null)
        if (!raw.isNullOrBlank()) {
            val lista = runCatching {
                json.decodeFromString<List<MensajeMichiLocal>>(raw)
            }.getOrDefault(emptyList())
            _mensajes.value = lista.sortedByDescending { it.timestamp }
        } else {
            _mensajes.value = emptyList()
        }
    }

    @Synchronized
    fun agregarMensaje(
        emisorNombre: String,
        texto: String,
        tipo: TipoMensajeMichi = TipoMensajeMichi.GENERAL
    ) {
        val nuevo = MensajeMichiLocal(
            id = UUID.randomUUID().toString(),
            emisorNombre = emisorNombre,
            texto = texto.trim(),
            timestamp = System.currentTimeMillis(),
            tipo = tipo
        )
        val listaActualizada = (listOf(nuevo) + _mensajes.value).take(MAX_MENSAJES)
        _mensajes.value = listaActualizada
        runCatching {
            prefs.edit().putString(CLAVE_MENSAJES, json.encodeToString(listaActualizada)).apply()
        }
    }

    private companion object {
        private const val CLAVE_MENSAJES = "historial_mensajes"
        private const val MAX_MENSAJES = 120
    }
}

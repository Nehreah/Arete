package com.renea.arete_social.feature.auth

import com.renea.arete_social.domain.model.Carta
import com.renea.arete_social.domain.model.ObraLibreria
import com.renea.arete_social.domain.model.Usuario

/** Estado de la pantalla de perfil de OTRA persona (solo lectura, sin edición). */
sealed interface PerfilPublicoUiState {
    data object Cargando : PerfilPublicoUiState
    data class Listo(
        val usuario: Usuario,
        val esPropio: Boolean = false,
        val abriendoChat: Boolean = false,
        val esAmigo: Boolean = false,
        val solicitudEnviada: Boolean = false,
        val solicitudRecibida: Boolean = false,
        val estaBloqueado: Boolean = false,
        /** Aviso breve cuando falla una acción (solicitud, aceptar, abrir PDF…). */
        val error: String? = null,
        /** Aviso de éxito en moderación (reporte enviado, usuario bloqueado). */
        val avisoModeracion: String? = null
    ) : PerfilPublicoUiState {
        /**
         * Perfil público: lo ve cualquiera. Perfil privado: solo su dueña y sus amistades.
         * Si el usuario está bloqueado, el contenido se oculta.
         * Es solo la decisión de la pantalla; quien de verdad lo impide es el servidor (RLS).
         */
        val contenidoVisible: Boolean get() = !estaBloqueado && (esPropio || !usuario.perfilPrivado || esAmigo)
    }
    data class Error(val mensaje: String) : PerfilPublicoUiState
}

/**
 * Correspondencia entre la persona que mira el perfil y la dueña del perfil.
 * Privacidad: solo contiene cartas de AMBAS personas entre sí, nunca cartas de terceros.
 */
data class CartasPerfilUiState(
    val cargando: Boolean = false,
    val enviando: Boolean = false,
    /** False sin cuenta (invitado) o sin repositorio: las cartas se guardan en el servidor. */
    val disponible: Boolean = true,
    /** Más recientes primero; mezcla las que escribí yo y las que me escribió esta persona. */
    val cartas: List<Carta> = emptyList(),
    val mensaje: String? = null,
    val errorCarga: Boolean = false
)


/**
 * Ver el perfil público de alguien cercano por Bluetooth, sin internet y solo en texto
 * (sin fotos ni PDF; esos requieren internet).
 */
data class BluetoothPerfilUiState(
    val buscando: Boolean = false,
    /** Cuándo se recibió la copia que se está mostrando (null = no se muestra copia Bluetooth). */
    val copiaRecibidaEn: Long? = null,
    val mensaje: String? = null
)

/** Librería (cuentos y poemarios) de la persona cuyo perfil se está mirando. Solo lectura. */
data class LibreriaPerfilUiState(
    val cargando: Boolean = false,
    val obras: List<ObraLibreria> = emptyList(),
    val error: Boolean = false
)

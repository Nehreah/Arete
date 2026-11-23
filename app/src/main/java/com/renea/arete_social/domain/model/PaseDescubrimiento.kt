package com.renea.arete_social.domain.model

import kotlinx.serialization.Serializable

/**
 * Pase de descubrimiento: lo que alguien que descubrió lugares y eventos estando cerca le comparte,
 * por Bluetooth, a quien se cruza con él. El receptor lo ve 24 h desde que lo recibe.
 *
 * Reglas (ver PoliticaDescubrimiento y ServicioPases):
 *  - Solo comparte quien descubrió DIRECTAMENTE (cerca o recientemente): nunca lo recibido por pase
 *    ni lo visto por el Modo Súper.
 *  - Solo lugares y eventos públicos. Hasta [MAX_ENTIDADES] por pase.
 *  - No se renueva: un elemento que el receptor ya ve por cualquier origen se ignora.
 *  - Va dirigido a una persona concreta ([receptorId]) y firmado por el emisor.
 *
 * IDEA FUTURA (S4) — clave por lugar y por día.
 * Hoy el pase solo habilita el chat a nivel de interfaz y de chats seguidos. Los mensajes de difusión
 * viajan por la malla sin cifrado específico del lugar, así que un teléfono modificado podría leerlos.
 * Para que el pase entregue acceso real, cada lugar o evento tendría una clave simétrica que rota cada
 * día. Los mensajes de su chat se cifrarían con ella. Quien lo descubre directamente la obtiene (del
 * servidor o de otro con descubrimiento directo) y el pase la incluye, cifrada para el receptor con su
 * clave E2EE. Al vencer el día, la clave deja de servir y no se renueva sin un nuevo descubrimiento.
 * Requiere definir rotación, distribución offline y qué pasa con los mensajes de días anteriores.
 */
@Serializable
data class PaseDescubrimiento(
    val id: String,
    val emisorId: String,
    val receptorId: String,
    val emitidoEn: Long,
    val entidades: List<EntidadSnapshot>,
    val firma: String = "",
    val clavePublicaEmisor: String = ""
) {
    /**
     * Bytes que se firman: todos los campos, cada uno con prefijo de longitud para que no se puedan
     * desplazar fronteras entre campos (mismo criterio que PaqueteMesh.datosFirmados). La firma y la
     * clave pública quedan fuera, claro.
     */
    fun datosFirmados(): String = buildString {
        append("antired-pase-v1|")
        fun campo(valor: String) = append(valor.length).append(':').append(valor).append('|')
        campo(id); campo(emisorId); campo(receptorId); campo(emitidoEn.toString())
        campo(entidades.size.toString())
        for (e in entidades) {
            campo(e.tipo.name); campo(e.id); campo(e.nombre); campo(e.descripcion)
            campo(e.latitud.toString()); campo(e.longitud.toString()); campo(e.chatId)
            campo(e.fechaEvento?.toString().orEmpty()); campo(e.visibilidad.name)
        }
    }

    companion object {
        /** Máximo de lugares/eventos por pase. */
        const val MAX_ENTIDADES = 20
    }
}

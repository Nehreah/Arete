package com.renea.arete_social.feature.mensajeria

import org.json.JSONObject

/**
 * Convierte el contenido interno de un mensaje (JSON de protocolo, señales, cifrado) en un
 * texto claro y amable para la persona. Se usa tanto en la burbuja del chat como en la
 * previsualización de la lista de chats, para que nunca se vean códigos como
 * `{"type":"REGALO_MASCOTA",...}` o `ENC:v3:...`.
 */
object TextoMensaje {

    private val regexCifrado = Regex("^ENC:v\\d+:")

    /** Texto mostrado cuando un mensaje cifrado no se puede abrir (todavía o en este teléfono). */
    const val CIFRADO_NO_DISPONIBLE = "🔒 Mensaje cifrado"

    /** ¿Es un paquete interno de la app (JSON con "type") y no algo que la persona escribió? */
    fun esProtocolo(contenido: String): Boolean =
        contenido.trimStart().startsWith("{\"type\":\"")

    /** ¿Tiene forma de mensaje cifrado, de cualquier versión? */
    fun parecePayloadCifrado(contenido: String): Boolean = regexCifrado.containsMatchIn(contenido)

    /**
     * Paquetes de protocolo que tiene sentido dejar como mensaje visible en el chat
     * (regalos y avisos de amistad). El resto (encuentros, audio, ubicación…) son datos
     * técnicos y no deben guardarse como mensajes.
     */
    fun protocoloVisibleEnChat(contenido: String): Boolean {
        if (!esProtocolo(contenido)) return false
        val tipo = runCatching { JSONObject(contenido).optString("type") }.getOrDefault("")
        return tipo in TIPOS_VISIBLES
    }

    private val TIPOS_VISIBLES = setOf("REGALO_MASCOTA", "SOLICITUD_AMISTAD", "RESPUESTA_AMISTAD")

    /**
     * Texto amable para un paquete de protocolo, o null si [contenido] no lo es.
     * [esPropio] cambia la redacción: "Te ha enviado…" vs "Enviaste…".
     */
    fun traducirProtocolo(contenido: String, esPropio: Boolean = false): String? {
        if (!esProtocolo(contenido)) return null
        val json = runCatching { JSONObject(contenido) }.getOrNull()
            ?: return "Mensaje especial"
        return when (json.optString("type")) {
            "REGALO_MASCOTA" -> textoRegalo(json, esPropio)
            "SOLICITUD_AMISTAD" ->
                if (esPropio) "👋 Enviaste una solicitud de amistad" else "👋 Te ha enviado una solicitud de amistad"
            "RESPUESTA_AMISTAD" -> when (json.optString("accion")) {
                "ACEPTADA" -> if (esPropio) "🤝 Aceptaste su solicitud de amistad" else "🤝 Ha aceptado tu solicitud de amistad"
                "RECHAZADA" -> if (esPropio) "Respondiste a su solicitud de amistad" else "Ha respondido a tu solicitud de amistad"
                else -> "Respuesta a solicitud de amistad"
            }
            "ENCUENTRO_SOLICITUD" -> if (esPropio) "📍 Propusiste un encuentro" else "📍 Te ha propuesto un encuentro"
            "ENCUENTRO_RESPUESTA" -> "📍 Respuesta a un encuentro"
            "ENCUENTRO_TERMINAR" -> "📍 El encuentro ha terminado"
            "ENCUENTRO_UBICACION" -> "📍 Ubicación compartida"
            else -> "Mensaje especial"
        }
    }

    private fun textoRegalo(json: JSONObject, esPropio: Boolean): String {
        val cantidad = json.optInt("cantidad", 1).coerceAtLeast(1)
        val (emoji, singular, plural) = when (json.optString("regalo")) {
            "CHISPA" -> Triple("✨", "una chispa", "chispas")
            "MEDICINA" -> Triple("💊", "una medicina", "medicinas")
            else -> Triple("🎁", "un regalo", "regalos")
        }
        val objeto = if (cantidad == 1) singular else "$cantidad $plural"
        return if (esPropio) "$emoji Enviaste $objeto a su mascota" else "$emoji Te ha enviado $objeto"
    }

    /**
     * Texto para la previsualización de la lista de chats (una sola línea).
     * [descifrado] es el texto ya descifrado si el contenido venía cifrado y se pudo abrir.
     */
    fun vistaPrevia(contenido: String, esPropio: Boolean = false, descifrado: String? = null): String {
        val base = descifrado ?: contenido
        traducirProtocolo(base, esPropio)?.let { return it }
        return when {
            base.startsWith("[VOZ_PTT:") -> "🎙️ Mensaje de voz"
            base.startsWith("[SENAL_") -> "📹 Videollamada"
            base.startsWith("[NOTIF_") -> "Notificación"
            descifrado == null && parecePayloadCifrado(base) -> CIFRADO_NO_DISPONIBLE
            else -> base.replace("\n", " ").trim().take(60)
        }
    }

    /** Texto para la burbuja del chat. Mismo criterio que la vista previa, sin recortar. */
    fun textoBurbuja(visible: String, esPropio: Boolean): String {
        traducirProtocolo(visible, esPropio)?.let { return it }
        return when {
            visible.startsWith("[VOZ_PTT:") -> "🎙️ Transmisión de voz (Walkie-Talkie)"
            visible.startsWith("[SENAL_LLAMADA_INICIAR:") -> "📹 Videollamada 1-a-1 iniciada"
            visible.startsWith("[SENAL_LLAMADA_ACEPTADA:") -> "📹 Videollamada conectada"
            visible.startsWith("[SENAL_LLAMADA_COLGADA:") -> "📵 Videollamada finalizada"
            parecePayloadCifrado(visible) -> CIFRADO_NO_DISPONIBLE
            else -> visible
        }
    }
}

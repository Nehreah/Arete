package com.renea.arete_social.feature.tamagotchi.regalos

/** Lo que una persona puede enviarle a la mascota de otra desde el mapa. */
enum class TipoRegalo { CHISPA, MEDICINA }

/** Regalo que llegó a este teléfono (por internet, Wi-Fi local o Bluetooth). */
data class RegaloMascotaEntrante(
    /** Identificador único del regalo: evita aplicarlo dos veces si llega por dos canales. */
    val regaloId: String,
    val remitenteId: String,
    val remitenteNombre: String,
    val tipo: TipoRegalo,
    val cantidad: Int
)

/** Resultado de intentar enviar un regalo. Siempre hay un texto claro para mostrar. */
sealed interface ResultadoEnvioRegalo {
    data object Enviado : ResultadoEnvioRegalo
    data object SinSesion : ResultadoEnvioRegalo
    data object ATiMismo : ResultadoEnvioRegalo
    data object SinMascota : ResultadoEnvioRegalo
    data object SinChispas : ResultadoEnvioRegalo
    data class EsperaMedicina(val segundos: Long) : ResultadoEnvioRegalo

    val exito: Boolean get() = this is Enviado

    fun texto(tipo: TipoRegalo, nombreDestino: String): String {
        val quien = nombreDestino.ifBlank { "esa persona" }
        return when (this) {
            Enviado -> when (tipo) {
                TipoRegalo.CHISPA -> "✦ Chispa enviada a $quien."
                TipoRegalo.MEDICINA -> "💊 Medicina enviada a $quien."
            }
            SinSesion -> "Inicia sesión para enviar regalos."
            ATiMismo -> "No puedes enviarte regalos a ti."
            SinMascota -> "Todavía no tienes mascota. Abre su pantalla una vez para que nazca."
            SinChispas -> "No tienes chispas. Se ganan al encontrarte con amigos."
            is EsperaMedicina -> {
                val min = (segundos + 59) / 60
                "Ya le enviaste medicina a $quien. Espera ${if (min <= 1) "1 minuto" else "$min minutos"}."
            }
        }
    }
}

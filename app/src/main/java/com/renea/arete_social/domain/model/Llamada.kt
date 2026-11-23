package com.renea.arete_social.domain.model

enum class EstadoLlamada {
    IDLE,
    ENTRANTE,
    LLAMANDO,
    EN_LLAMADA,
    FINALIZADA
}

enum class TipoLlamada {
    VIDEO,
    AUDIO
}

data class ContactoLlamada(
    val id: String,
    val nombre: String,
    val foto: String? = null
)

data class LlamadaInfo(
    val id: String,
    val remoto: ContactoLlamada,
    val tipo: TipoLlamada = TipoLlamada.VIDEO,
    val estado: EstadoLlamada = EstadoLlamada.IDLE,
    val timestampInicio: Long = System.currentTimeMillis(),
    val duracionSegundos: Int = 0,
    val microfonoSilenciado: Boolean = false,
    val camaraActiva: Boolean = true,
    val camaraFrontal: Boolean = true
)

data class HablanteVoz(
    val usuarioId: String,
    val nombre: String,
    val foto: String? = null,
    val nivelAudio: Float = 0f,
    val hablando: Boolean = true
)

data class EstadoAudioChat(
    val chatAudioActivo: Boolean = true,
    val transmitiendoVoz: Boolean = false,
    val canalActivoId: String = "general",
    val canalActivoNombre: String = "Chat Aréte",
    val hablantes: List<HablanteVoz> = emptyList()
)

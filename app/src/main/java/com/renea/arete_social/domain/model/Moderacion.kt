package com.renea.arete_social.domain.model

/**
 * Tipos de entidad que pueden ser objeto de reporte por un usuario.
 */
enum class TipoReporte {
    USUARIO,
    MENSAJE,
    PUBLICACION,
    COMENTARIO,
    COMUNIDAD
}

/**
 * Motivos estandarizados para reportar contenido o usuarios inapropiados (normas de convivencia y seguridad comunitaria).
 */
enum class MotivoReporte(val etiqueta: String, val descripcion: String) {
    SPAM(
        etiqueta = "Spam o publicidad",
        descripcion = "Contenido repetitivo, enlaces sospechosos o publicidad no deseada."
    ),
    ACOSO(
        etiqueta = "Acoso o intimidación",
        descripcion = "Insultos, amenazas, intimidación o persecución dirigida."
    ),
    CONTENIDO_INAPROPIADO(
        etiqueta = "Contenido inapropiado o sexual",
        descripcion = "Material sexual explícito, desnudez o contenido obsceno."
    ),
    ODIO(
        etiqueta = "Discurso de odio o violencia",
        descripcion = "Discriminación, incitación al odio, racismo o fomento a la violencia."
    ),
    FRAUDE(
        etiqueta = "Suplantación o engaño",
        descripcion = "Perfiles falsos, usurpación de identidad o estafas."
    ),
    ABUSO_INFANTIL(
        etiqueta = "Abuso infantil / CSAM",
        descripcion = "Pornografía infantil, explotación o abuso sexual infantil, acoso a menores o riesgo infantil."
    ),
    OTRO(
        etiqueta = "Otro motivo",
        descripcion = "Conducta que vulnera las normas de convivencia de Aréte."
    )
}

/**
 * Representación de un reporte de moderación enviado.
 */
data class ReporteModeracion(
    val id: String = "",
    val reportadorId: String,
    val reportadoId: String?,
    val tipo: TipoReporte,
    val referenciaId: String?,
    val motivo: MotivoReporte,
    val descripcion: String? = null,
    val creadoEn: Long = System.currentTimeMillis()
)

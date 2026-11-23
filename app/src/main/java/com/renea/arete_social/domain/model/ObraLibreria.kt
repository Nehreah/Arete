package com.renea.arete_social.domain.model

/** Las dos pestañas de la Librería de cada persona. */
enum class TipoObra(val etiqueta: String, val etiquetaPlural: String) {
    CUENTO("Cuento", "Cuentos"),
    POEMARIO("Poemario", "Poemarios")
}

/** PDF elegido por la persona, ya leído en memoria y validado en tamaño. */
class ArchivoPdf(val nombre: String, val bytes: ByteArray)

/** Obra ya guardada. El PDF vive en Storage; aquí solo se conoce su ruta. */
data class ObraLibreria(
    val id: String,
    val autorId: String,
    val tipo: TipoObra,
    val titulo: String,
    val contenido: String,
    val enlace: String?,
    val pdfRuta: String?,
    val pdfNombre: String?,
    val creadaEn: Long,
    val compartidaMapaEn: Long? = null,
    val autorNombre: String? = null
) {
    val tienePdf: Boolean get() = !pdfRuta.isNullOrBlank()
    val estaActivaEnMapa: Boolean
        get() = compartidaMapaEn != null && (System.currentTimeMillis() - compartidaMapaEn) < 24 * 3600 * 1000L
}

/** Obra validada y lista para guardar. Se construye solo con [com.renea.arete_social.domain.util.validarNuevaObra]. */
data class NuevaObra(
    val tipo: TipoObra,
    val titulo: String,
    val contenido: String,
    val enlace: String?,
    val pdf: ArchivoPdf?
)

object LimitesLibreria {
    const val MAX_TITULO = 120
    const val MAX_CONTENIDO = 20_000
    const val MAX_ENLACE = 500
    const val MAX_PDF_BYTES = 15 * 1024 * 1024
    const val INTERVALO_COMPARTIR_MAPA_MS = 60 * 60 * 1000L // 1 hora
    const val VIGENCIA_MAPA_MS = 24 * 60 * 60 * 1000L // 24 horas
}

sealed interface ResultadoCompartirMapa {
    data object Exito : ResultadoCompartirMapa
    data class EnEspera(val minutosRestantes: Int) : ResultadoCompartirMapa
    data class Error(val mensaje: String) : ResultadoCompartirMapa
}

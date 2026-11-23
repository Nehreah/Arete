package com.renea.arete_social.gui.libreria

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.renea.arete_social.domain.model.ArchivoPdf
import com.renea.arete_social.domain.model.LimitesLibreria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ResultadoLecturaPdf {
    data class Listo(val archivo: ArchivoPdf) : ResultadoLecturaPdf
    data class Fallo(val mensaje: String) : ResultadoLecturaPdf
}

/** Lee el PDF elegido fuera del hilo principal y corta si supera el tamaño máximo. */
suspend fun leerPdf(resolver: ContentResolver, uri: Uri): ResultadoLecturaPdf = withContext(Dispatchers.IO) {
    runCatching {
        val nombre = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "documento.pdf"
        val limite = LimitesLibreria.MAX_PDF_BYTES
        val bytes = resolver.openInputStream(uri)?.use { entrada ->
            // Se lee un byte de más para detectar que se pasó del límite sin cargar todo el archivo.
            leerHasta(entrada, limite + 1)
        } ?: return@runCatching ResultadoLecturaPdf.Fallo("No se pudo abrir el archivo.")
        if (bytes.size > limite) {
            ResultadoLecturaPdf.Fallo("El PDF supera los ${limite / (1024 * 1024)} MB.")
        } else {
            ResultadoLecturaPdf.Listo(ArchivoPdf(nombre, bytes))
        }
    }.getOrElse { ResultadoLecturaPdf.Fallo("No se pudo leer el PDF.") }
}

/** InputStream.readNBytes exige API 33; esta lectura acotada funciona en todas las versiones. */
private fun leerHasta(entrada: java.io.InputStream, maximo: Int): ByteArray {
    val salida = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var restante = maximo
    while (restante > 0) {
        val leidos = entrada.read(buffer, 0, minOf(buffer.size, restante))
        if (leidos < 0) break
        salida.write(buffer, 0, leidos)
        restante -= leidos
    }
    return salida.toByteArray()
}

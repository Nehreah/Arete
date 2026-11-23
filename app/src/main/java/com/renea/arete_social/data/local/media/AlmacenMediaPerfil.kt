package com.renea.arete_social.data.local.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import com.renea.arete_social.domain.util.toHex

/**
 * Gestiona el almacenamiento local persistente en disco de medios de perfil (avatares y portadas).
 * Evita almacenar cadenas Base64 en SharedPreferences o SQLite, eliminando problemas de
 * consumo de memoria y cuellos de botella en Coil.
 */
object AlmacenMediaPerfil {

    private var directorioBase: File? = null

    data class ResultadoMedia(
        val rutaArchivo: String,
        val hashSha256: String,
        val rutaMiniatura: String,
        val hashMiniatura: String,
        val miniaturaBytes: ByteArray
    )

    fun inicializar(context: Context) {
        if (directorioBase != null) return
        val dir = File(context.applicationContext.filesDir, "media/perfil")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        directorioBase = dir
    }

    internal fun inicializarConDirectorio(directorioParaPruebas: File) {
        directorioBase = directorioParaPruebas
        if (!directorioParaPruebas.exists()) {
            directorioParaPruebas.mkdirs()
        }
    }

    private fun obtenerDirectorio(): File {
        return directorioBase ?: File("/tmp/antired_media_fallback").apply { mkdirs() }
    }

    /**
     * Calcula el hash SHA-256 de [bytes] devolviendo la cadena hexadecimal.
     */
    fun calcularHashSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).toHex()
    }

    /**
     * Guarda la imagen original en disco y genera su miniatura correspondiente:
     * - Avatar: máx 96×96 px, JPEG calidad 70 (~3–5 KB)
     * - Portada: máx 320×120 px, JPEG calidad 65 (~8–12 KB)
     */
    fun guardarImagen(bytes: ByteArray, esAvatar: Boolean, extension: String = "jpg"): ResultadoMedia {
        val dir = obtenerDirectorio()
        val hashOriginal = calcularHashSha256(bytes)
        val hashCorto = hashOriginal.take(12)
        val prefijo = if (esAvatar) "avatar" else "portada"

        val extLimpia = if (extension.isBlank() || extension.contains("/")) "jpg" else extension.removePrefix(".")
        val archivoOriginal = File(dir, "${prefijo}_${hashCorto}.$extLimpia")

        // Escritura atómica del archivo original
        escribirAtomicamente(archivoOriginal, bytes)

        // Generar miniatura
        val maxAncho = if (esAvatar) 96 else 320
        val maxAlto = if (esAvatar) 96 else 120
        val calidad = if (esAvatar) 70 else 65

        val miniaturaBytes = generarMiniaturaBytes(bytes, maxAncho, maxAlto, calidad)
        val hashMiniatura = calcularHashSha256(miniaturaBytes)
        val archivoMiniatura = File(dir, "thumb_${prefijo}_${hashMiniatura.take(12)}.jpg")

        escribirAtomicamente(archivoMiniatura, miniaturaBytes)

        return ResultadoMedia(
            rutaArchivo = archivoOriginal.absolutePath,
            hashSha256 = hashOriginal,
            rutaMiniatura = archivoMiniatura.absolutePath,
            hashMiniatura = hashMiniatura,
            miniaturaBytes = miniaturaBytes
        )
    }

    /**
     * Guarda directamente los bytes asociados a un hash recibido por la red (BLE o Wi-Fi).
     */
    fun guardarBytesConHash(bytes: ByteArray, hashSha256: String, esMiniatura: Boolean = false, extension: String = "jpg"): File {
        val dir = obtenerDirectorio()
        val prefijo = if (esMiniatura) "thumb_" else "media_"
        val extLimpia = if (extension.isBlank() || extension.contains("/")) "jpg" else extension.removePrefix(".")
        val archivo = File(dir, "${prefijo}${hashSha256.take(12)}.$extLimpia")
        escribirAtomicamente(archivo, bytes)
        return archivo
    }

    /**
     * Busca en disco un archivo existente cuyo nombre coincida con el hash dado.
     */
    fun obtenerArchivoPorHash(hashSha256: String): File? {
        val dir = obtenerDirectorio()
        val hashCorto = hashSha256.take(12)
        return dir.listFiles { _, name -> name.contains(hashCorto) }?.firstOrNull()
    }

    /**
     * Busca en disco un archivo de miniatura seguro para envío por Bluetooth (<= 50 KB).
     * Prioriza archivos con prefijo "thumb_".
     */
    fun obtenerMediaSeguraBle(hashSha256: String): File? {
        val dir = obtenerDirectorio()
        val hashCorto = hashSha256.take(12)
        val candidatos = dir.listFiles { _, name -> name.contains(hashCorto) } ?: return null
        val miniatura = candidatos.firstOrNull { it.name.startsWith("thumb_") }
        if (miniatura != null) return miniatura
        return candidatos.firstOrNull { it.length() <= 50 * 1024L }
    }

    /**
     * Obtiene el hash SHA-256 de la miniatura correspondiente al archivo dado.
     * Si el archivo ya es miniatura o si no existe, la genera en disco y devuelve su hash.
     */
    fun obtenerOGenerarHashMiniatura(rutaArchivo: String, esAvatar: Boolean): String? {
        return runCatching {
            val f = File(rutaArchivo.removePrefix("file://"))
            if (!f.exists()) return@runCatching null
            val dir = obtenerDirectorio()

            if (f.name.startsWith("thumb_")) {
                return@runCatching calcularHashSha256(f.readBytes())
            }

            val bytesOriginal = f.readBytes()
            if (bytesOriginal.isEmpty()) return@runCatching null

            val maxAncho = if (esAvatar) 96 else 320
            val maxAlto = if (esAvatar) 96 else 120
            val calidad = if (esAvatar) 70 else 65
            val prefijo = if (esAvatar) "avatar" else "portada"

            val miniaturaBytes = generarMiniaturaBytes(bytesOriginal, maxAncho, maxAlto, calidad)
            val hashMiniatura = calcularHashSha256(miniaturaBytes)
            val archivoMiniatura = File(dir, "thumb_${prefijo}_${hashMiniatura.take(12)}.jpg")
            escribirAtomicamente(archivoMiniatura, miniaturaBytes)
            hashMiniatura
        }.getOrNull()
    }

    /**
     * Si la cadena recibida es una data URL en Base64 (`data:image/...;base64,...`),
     * la decodifica, la persiste en disco y devuelve la ruta absoluta del archivo local.
     * Si ya es una ruta local existente o URL web, la retorna sin modificaciones.
     */
    fun migrarSiEsDataUrl(dataUrlOPath: String?, esAvatar: Boolean): String? {
        if (dataUrlOPath.isNullOrBlank()) return null
        if (!dataUrlOPath.startsWith("data:image/")) return dataUrlOPath

        return runCatching {
            val comaIdx = dataUrlOPath.indexOf(",")
            if (comaIdx == -1) return@runCatching null
            val base64Str = dataUrlOPath.substring(comaIdx + 1)
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            if (bytes == null || bytes.isEmpty()) return@runCatching null
            val ext = if (dataUrlOPath.contains("png")) "png" else "jpg"
            val resultado = guardarImagen(bytes, esAvatar, ext)
            resultado.rutaArchivo
        }.getOrNull() ?: dataUrlOPath
    }

    private fun escribirAtomicamente(destino: File, bytes: ByteArray) {
        if (destino.exists() && destino.length() == bytes.size.toLong()) {
            return // Ya existe exactamente con el mismo tamaño
        }
        val temp = File(destino.parentFile, "${destino.name}.tmp")
        FileOutputStream(temp).use { it.write(bytes) }
        if (!temp.renameTo(destino)) {
            temp.copyTo(destino, overwrite = true)
            temp.delete()
        }
    }

    private fun generarMiniaturaBytes(
        bytesOriginales: ByteArray,
        maxAncho: Int,
        maxAlto: Int,
        calidad: Int
    ): ByteArray {
        return runCatching {
            val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytesOriginales, 0, bytesOriginales.size, opciones)

            var ancho = opciones.outWidth
            var alto = opciones.outHeight
            if (ancho <= 0 || alto <= 0) return@runCatching bytesOriginales

            var inSampleSize = 1
            while (ancho / 2 >= maxAncho && alto / 2 >= maxAlto) {
                ancho /= 2
                alto /= 2
                inSampleSize *= 2
            }

            val opcionesDecod = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytesOriginales, 0, bytesOriginales.size, opcionesDecod)
                ?: return@runCatching bytesOriginales

            val ratio = minOf(maxAncho.toFloat() / bitmap.width, maxAlto.toFloat() / bitmap.height)
            val destinoW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val destinoH = (bitmap.height * ratio).toInt().coerceAtLeast(1)

            val redimensionado = if (bitmap.width != destinoW || bitmap.height != destinoH) {
                Bitmap.createScaledBitmap(bitmap, destinoW, destinoH, true)
            } else {
                bitmap
            }

            val stream = ByteArrayOutputStream()
            redimensionado.compress(Bitmap.CompressFormat.JPEG, calidad, stream)
            if (redimensionado != bitmap) {
                redimensionado.recycle()
            }
            bitmap.recycle()
            stream.toByteArray()
        }.getOrDefault(bytesOriginales)
    }
}

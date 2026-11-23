package com.renea.arete_social.data.network.ruta

import com.renea.arete_social.domain.model.ModoRuta
import com.renea.arete_social.domain.model.RutaEncuentro
import com.renea.arete_social.domain.model.Ubicacion
import com.renea.arete_social.domain.ruta.CalculadorRuta
import com.renea.arete_social.domain.util.distanciaMetros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Ruta por calles con OSRM sobre datos de OpenStreetMap (instancias de routing.openstreetmap.de:
 * una por modo, a pie / bici / auto). No necesita clave de API. Es un servicio comunitario de uso
 * razonable: para producción conviene alojar OSRM propio o cambiar esta clase por otra que
 * implemente [CalculadorRuta] (por ejemplo Google Directions); nada más depende de ella.
 *
 * Sin red o con el servicio caído devuelve una línea recta, marcada con porCalles = false.
 */
class CalculadorRutaOsrm : CalculadorRuta {

    override suspend fun calcular(origen: Ubicacion, destino: Ubicacion, modo: ModoRuta): RutaEncuentro =
        withContext(Dispatchers.IO) {
            runCatching { consultar(origen, destino, modo) }.getOrNull()
                ?: lineaRecta(origen, destino)
        }

    private fun consultar(origen: Ubicacion, destino: Ubicacion, modo: ModoRuta): RutaEncuentro? {
        val instancia = when (modo) {
            ModoRuta.WALKING -> "routed-foot"
            ModoRuta.BICYCLING -> "routed-bike"
            ModoRuta.DRIVING -> "routed-car"
        }
        // OSRM recibe lon,lat (no lat,lon). Estas instancias usan siempre "driving" en la ruta.
        val coordenadas = String.format(
            Locale.US, "%.6f,%.6f;%.6f,%.6f",
            origen.longitud, origen.latitud, destino.longitud, destino.latitud
        )
        val url = URL("https://routing.openstreetmap.de/$instancia/route/v1/driving/$coordenadas?overview=full&geometries=geojson")
        val conexion = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIEMPO_CONEXION_MS
            readTimeout = TIEMPO_LECTURA_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AntiRed-Android/1.0")
        }
        return try {
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) return null
            val cuerpo = conexion.inputStream.bufferedReader().use { it.readText() }
            parsear(cuerpo)
        } finally {
            conexion.disconnect()
        }
    }

    companion object {
        private const val TIEMPO_CONEXION_MS = 5_000
        private const val TIEMPO_LECTURA_MS = 6_000

        /** Interpreta la respuesta de OSRM; null si no trae una ruta utilizable. Es pura, sin red. */
        internal fun parsear(cuerpo: String): RutaEncuentro? {
            val raiz = Json.parseToJsonElement(cuerpo).jsonObject
            if (raiz["code"]?.jsonPrimitive?.content != "Ok") return null
            val ruta = raiz["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val distancia = ruta["distance"]?.jsonPrimitive?.double ?: return null
            val ahora = System.currentTimeMillis()
            val puntos = ruta["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                ?.map { par ->
                    val lonLat = par.jsonArray
                    Ubicacion(
                        latitud = lonLat[1].jsonPrimitive.double,
                        longitud = lonLat[0].jsonPrimitive.double,
                        timestamp = ahora
                    )
                }
                .orEmpty()
            if (puntos.size < 2) return null
            return RutaEncuentro(puntos = puntos, distanciaMetros = distancia, porCalles = true)
        }

        /** Respaldo sin red: línea recta entre las dos personas. */
        internal fun lineaRecta(origen: Ubicacion, destino: Ubicacion): RutaEncuentro =
            RutaEncuentro(
                puntos = listOf(origen, destino),
                distanciaMetros = distanciaMetros(origen, destino),
                porCalles = false
            )
    }
}

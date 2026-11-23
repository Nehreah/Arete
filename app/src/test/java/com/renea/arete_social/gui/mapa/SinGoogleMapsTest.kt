package com.renea.arete_social.gui.mapa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Red de seguridad de la migración a OSMDroid (Fase 7): revisa el código fuente para que no vuelva a
 * colarse Google Maps ni la clave de API, y para que OSMDroid siga confinado a `gui/mapa/osm`.
 * Gradle ejecuta los tests con el directorio del módulo (`app/`) como directorio de trabajo; si se
 * lanzan desde otro sitio, las pruebas se omiten en vez de fallar.
 */
class SinGoogleMapsTest {
    private val raiz = File("src/main/java")
    private val manifiesto = File("src/main/AndroidManifest.xml")

    private fun fuentes(): List<File> =
        raiz.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun ruta(f: File) = f.path.replace('\\', '/')

    @Test
    fun noQuedanImportsDeGooglePlayServicesNiMaps() {
        assumeTrue(raiz.isDirectory)
        val prohibidos = listOf("com.google.android.gms", "com.google.maps.android", "com.google.mlkit")
        val culpables = fuentes().filter { f -> prohibidos.any { it in f.readText() } }.map(::ruta)
        assertTrue("Referencias a código privativo de Google en: $culpables", culpables.isEmpty())
    }

    @Test
    fun osmdroidSoloVivePorDentroDeGuiMapaOsmYAreteApp() {
        assumeTrue(raiz.isDirectory)
        val culpables = fuentes()
            .filter { "org.osmdroid" in it.readText() }
            .map(::ruta)
            .filterNot { "/gui/mapa/osm/" in it || it.endsWith("/AreteApp.kt") || it.endsWith("/AntiRedApp.kt") }
        assertTrue("OSMDroid fuera de gui/mapa/osm: $culpables", culpables.isEmpty())
    }

    @Test
    fun elManifiestoNoTieneClaveDeGoogleMaps() {
        assumeTrue(manifiesto.isFile)
        val texto = manifiesto.readText()
        assertFalse(texto.contains("com.google.android.geo.API_KEY"))
        assertFalse(texto.contains("MAPS_API_KEY"))
    }
}

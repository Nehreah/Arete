package com.renea.arete_social

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Punto de composición de dependencias de la aplicación.
 *
 * Arquitectura 100% soberana y descentralizada (local-first). Toda la persistencia,
 * enrutamiento Mesh P2P y criptografía operan directamente en el dispositivo sin
 * depender de servidores centrales ni servicios propietarios.
 */
class AreteApp : Application() {
    var contenedor: ContenedorApp? = null
        private set

    var errorConfiguracion: String? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // OSMDroid se configura al inicio: el mapa no depende de servidores
        configurarOsmdroid()

        // Inicialización de sesiones e intereses locales antes de componer servicios
        com.renea.arete_social.feature.auth.GestorSesionLocal.inicializar(this)
        com.renea.arete_social.data.local.perfil.GestorInteresesUsuario.inicializar(this)

        // Arquitectura 100% Descentralizada / Local-First soberana
        contenedor = ContenedorApp(this)
    }

    /**
     * Configuración global de OSMDroid. El userAgent es obligatorio: los servidores de teselas
     * de OpenStreetMap bloquean los clientes anónimos. La caché vive en cacheDir, así que no se
     * necesita permiso de almacenamiento y el sistema puede liberarla si falta espacio.
     *
     * Rendimiento y offline:
     * - 300 MB de caché en disco para que amplias áreas queden disponibles sin red.
     * - 4 hilos de descarga paralela + cola de 40 teselas: reduce el tiempo de carga al hacer
     *   zoom out o panear hacia zonas nuevas.
     * - expirationExtendedDuration = Long.MAX_VALUE / 2: las teselas en disco nunca "caducan",
     *   así el modo offline siempre usa lo que haya aunque lleve meses descargado.
     */
    private fun configurarOsmdroid() {
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            // Caché en disco: 300 MB activos, recortar a 250 MB cuando se supere el límite.
            tileFileSystemCacheMaxBytes  = 300L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
            // Descargas más rápidas: 4 hilos paralelos y cola más amplia.
            tileDownloadThreads      = 4
            tileDownloadMaxQueueSize = 40
            // Las teselas guardadas no caducan → modo offline siempre las sirve.
            expirationExtendedDuration = Long.MAX_VALUE / 2
        }
    }
}

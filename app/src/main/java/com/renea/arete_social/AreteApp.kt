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
     * de OpenStreetMap bloquean los clientes anónimos.
     *
     * Persistencia y resiliencia offline:
     * - Las teselas se almacenan en `filesDir/osmdroid` para que el sistema operativo o limpiadores
     *   de Xiaomi/Samsung no las borren al limpiar la caché temporal.
     * - Parámetros adaptativos: en dispositivos de gama baja (<= 3GB RAM o Android Go) se reducen
     *   los hilos a 2 y la cola a 16 para prevenir picos de memoria (OOM).
     */
    private fun configurarOsmdroid() {
        val esGamaBaja = com.renea.arete_social.util.GestorCompatibilidadFabricantes.esDispositivoGamaBaja(this)

        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(filesDir, "osmdroid/tiles")

            if (esGamaBaja) {
                tileFileSystemCacheMaxBytes  = 150L * 1024 * 1024
                tileFileSystemCacheTrimBytes = 120L * 1024 * 1024
                tileDownloadThreads          = 2
                tileDownloadMaxQueueSize     = 16
            } else {
                tileFileSystemCacheMaxBytes  = 350L * 1024 * 1024
                tileFileSystemCacheTrimBytes = 300L * 1024 * 1024
                tileDownloadThreads          = 4
                tileDownloadMaxQueueSize     = 40
            }

            // Las teselas guardadas no caducan → modo offline siempre las sirve.
            expirationExtendedDuration = Long.MAX_VALUE / 2
        }
    }
}

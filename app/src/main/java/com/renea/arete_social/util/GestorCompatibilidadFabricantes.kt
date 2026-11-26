package com.renea.arete_social.util

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.location.LocationManagerCompat

/**
 * Utilidades para resolver peculiaridades de fabricantes específicos (Xiaomi, Poco,
 * Samsung, Motorola) y perfiles de hardware (gama baja/alta), garantizando que el
 * radar táctico y los enlaces P2P operen de forma confiable.
 */
object GestorCompatibilidadFabricantes {

    fun coincideFabricante(fabricante: String, marca: String, objetivo: String): Boolean {
        val m = fabricante.lowercase()
        val b = marca.lowercase()
        val o = objetivo.lowercase()
        return m.contains(o) || b.contains(o)
    }

    fun esXiaomiOPoco(fabricante: String = Build.MANUFACTURER, marca: String = Build.BRAND): Boolean {
        return coincideFabricante(fabricante, marca, "xiaomi") ||
                coincideFabricante(fabricante, marca, "poco") ||
                coincideFabricante(fabricante, marca, "redmi")
    }

    fun esSamsung(fabricante: String = Build.MANUFACTURER, marca: String = Build.BRAND): Boolean {
        return coincideFabricante(fabricante, marca, "samsung")
    }

    fun esMotorola(fabricante: String = Build.MANUFACTURER, marca: String = Build.BRAND): Boolean {
        return coincideFabricante(fabricante, marca, "motorola")
    }

    fun esVivo(fabricante: String = Build.MANUFACTURER, marca: String = Build.BRAND): Boolean {
        return coincideFabricante(fabricante, marca, "vivo") || coincideFabricante(fabricante, marca, "iqoo")
    }

    fun esChipsetMediaTek(): Boolean {
        val hw = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.lowercase() else ""
        return hw.contains("mt") || board.contains("mt") || soc.contains("mediatek")
    }

    /**
     * Identifica dispositivos de recursos limitados (Android Go, memoria RAM <= 3 GB),
     * donde se deben usar configuraciones reducidas de hilos y memoria de teselas.
     */
    fun esDispositivoGamaBaja(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        // Menos o igual a 3.2 GB de RAM física total
        return memInfo.totalMem <= 3.2 * 1024 * 1024 * 1024
    }

    /**
     * Verifica si el switch global de ubicación (GPS) del sistema está encendido.
     * En MIUI (Xiaomi/Poco) y One UI (Samsung), el escáner BLE NO entrega paquetes si
     * este interruptor está apagado, aun teniendo concedidos los permisos de la app.
     */
    fun estaUbicacionHabilitada(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Verifica si la aplicación está exenta de la optimización de batería (Doze mode).
     */
    fun tieneIgnorarOptimizacionBateria(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent para solicitar al usuario desactivar la optimización de batería.
     */
    fun crearIntentOptimizacionBateria(packageName: String): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    /**
     * Intent para abrir los ajustes de ubicación del sistema para que el usuario encienda el GPS.
     */
    fun crearIntentAjustesUbicacion(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    /**
     * Intent para abrir la pantalla de Autoinicio en MIUI / HyperOS (Xiaomi / Poco / Redmi).
     * Si no está disponible en el dispositivo, devuelve null.
     */
    fun crearIntentAutoinicioXiaomi(context: Context): Intent? {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
        )
        for (intent in intents) {
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                return intent
            }
        }
        return null
    }
}

package com.renea.arete_social.data.network.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Ayudante optativo de descubrimiento Wi-Fi Direct. Requiere permiso runtime adecuado y que
 * la pantalla/servicio propietario registre y cierre el controlador. El descubrimiento no crea
 * automáticamente una conexión; el usuario/la lógica de producto debe seleccionar un peer.
 */
@Suppress("unused")
class WifiDirectController(
    context: Context,
    private val onPeers: (List<WifiP2pDevice>) -> Unit,
    private val onState: (String) -> Unit = {}
) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = manager?.initialize(app, app.mainLooper, null)
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    onState(if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "Wi-Fi Direct disponible" else "Wi-Fi Direct desactivado")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> onState("Cambió el estado de conexión Wi-Fi Direct; consulta ConnectionInfo")
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> Unit
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasPermission()) {
            onState("Falta permiso para descubrir dispositivos Wi-Fi Direct")
            return
        }
        val m = manager ?: run { onState("Wi-Fi Direct no es compatible con este dispositivo"); return }
        val c = channel ?: run { onState("No se pudo inicializar Wi-Fi Direct"); return }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    app.registerReceiver(receiver, filter)
                }
                registered = true
            } catch (e: Exception) {
                onState("No se pudo registrar el receptor Wi-Fi Direct: ${e.localizedMessage}")
                return
            }
        }
        try {
            m.discoverPeers(c, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { onState("Buscando pares Wi-Fi Direct") }
                override fun onFailure(reason: Int) { onState("Falló búsqueda Wi-Fi Direct (código $reason)") }
            })
        } catch (_: SecurityException) {
            onState("Android denegó el permiso Wi-Fi Direct")
        }
    }

    @SuppressLint("MissingPermission")
    fun requestPeers() {
        if (!hasPermission()) return
        val m = manager ?: return
        val c = channel ?: return
        try {
            m.requestPeers(c) { list: WifiP2pDeviceList -> onPeers(list.deviceList.toList()) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para consultar pares", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        if (!hasPermission()) {
            onState("Falta permiso para conectar por Wi-Fi Direct")
            return
        }
        val m = manager ?: return
        val c = channel ?: return
        try {
            // Compatible con minSdk 26+ (no requiere API 29 ni MacAddress object)
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
            }
            m.connect(c, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { onState("Solicitud de conexión Wi-Fi Direct enviada") }
                override fun onFailure(reason: Int) { onState("No se pudo conectar por Wi-Fi Direct (código $reason)") }
            })
        } catch (e: Exception) {
            onState("Error de conexión Wi-Fi Direct: ${e.localizedMessage}")
        }
    }

    private fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(app, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun close() {
        if (registered) runCatching { app.unregisterReceiver(receiver) }
        registered = false
        channel = null
    }

    companion object { private const val TAG = "AntiRedWifiDirect" }
}

package com.renea.arete_social.data.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.renea.arete_social.MainActivity
import com.renea.arete_social.R

/**
 * Servicio en primer plano (Foreground Service) que mantiene activo el nodo de red BLE Mesh
 * y el descubrimiento de proximidad incluso cuando la pantalla está apagada o la aplicación
 * está en segundo plano.
 */
class MeshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        val notification = crearNotificacion()
        // Android 14+ (API 34) requiere que el tipo de FGS se pase explícitamente a
        // startForeground(). ServiceCompat gestiona la compatibilidad hacia atrás.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aréte Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activa la red Mesh BLE y el radar táctico en segundo plano"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun crearNotificacion(): Notification {
        val intentToMain = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intentToMain,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aréte Social — Red Mesh Activa")
            .setContentText("Enlace BLE y radar táctico funcionando en segundo plano.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "antired_mesh_foreground_channel"
        const val ACTION_START = "com.renea.arete_social.START_MESH_SERVICE"
        const val ACTION_STOP = "com.renea.arete_social.STOP_MESH_SERVICE"

        fun iniciar(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun detener(context: Context) {
            runCatching {
                val intent = Intent(context, MeshForegroundService::class.java)
                context.stopService(intent)
            }
        }
    }
}

package com.renea.arete_social.feature.ubicacion

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.renea.arete_social.MainActivity
import com.renea.arete_social.R

/**
 * Notifica al usuario cuando su ubicación se ha pausado automáticamente por inactividad
 * para proteger su privacidad y ahorrar batería.
 */
@SuppressLint("MissingPermission")
object UbicacionInactividadNotificationHelper {

    private const val CHANNEL_ID = "arete_privacidad_ubicacion_channel"
    private const val CHANNEL_NAME = "Privacidad y Ubicación"
    private const val CHANNEL_DESC = "Avisos sobre el estado de la compartición de ubicación y privacidad"
    private const val NOTIFICATION_ID_AUTO_PAUSA = 9001

    @Volatile
    private var channelCreated = false

    fun asegurarCanal(context: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
        channelCreated = true
    }

    fun notificarUbicacionPausada(context: Context, minutos: Int) {
        asegurarCanal(context)
        val intentToMain = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intentToMain,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Ubicación en pausa por inactividad")
            .setContentText("Hace más de $minutos min no usas la app. Se detuvo la compartición de ubicación para ahorrar batería y proteger tu privacidad.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Hace más de $minutos min no usas la app. Se detuvo la compartición de ubicación en el radar táctico y BLE Mesh para ahorrar batería y proteger tu privacidad. Abre Aréte para reanudar.")
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AUTO_PAUSA, notificacion)
        }
    }

    fun cancelarNotificacion(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_AUTO_PAUSA)
        }
    }
}

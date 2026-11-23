package com.renea.arete_social.feature.llamadas

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.renea.arete_social.R
import com.renea.arete_social.domain.model.NotificacionTactica
import com.renea.arete_social.domain.model.TipoNotificacionTactica
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor especializado en notificaciones tácticas en UI (banner superior) y
 * notificaciones push locales en la barra de estado de Android.
 */
class TacticalNotificationManager(
    private val context: Context?,
    private val onSonarTono: ((tipo: Int, duracionMs: Int) -> Unit)? = null
) {
    private val _notificacionActiva = MutableStateFlow<NotificacionTactica?>(null)
    val notificacionActiva: StateFlow<NotificacionTactica?> = _notificacionActiva.asStateFlow()

    @Volatile var chatVisibleId: String? = null
    private var ultimoPitidoMs = 0L

    private val vibrator: Vibrator? by lazy {
        context?.let { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }

    fun mostrarNotificacion(notif: NotificacionTactica) {
        if (notif.chatId != null && notif.chatId == chatVisibleId) return
        if (System.currentTimeMillis() - notif.timestamp > 120_000L) return
        if (_notificacionActiva.value?.id == notif.id) return
        _notificacionActiva.value = notif

        val ahora = SystemClock.elapsedRealtime()
        val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (ahora - ultimoPitidoMs > 1_500L && (audio == null || audio.ringerMode == AudioManager.RINGER_MODE_NORMAL)) {
            ultimoPitidoMs = ahora
            onSonarTono?.invoke(ToneGenerator.TONE_PROP_BEEP, 120)
        }

        // Háptica / Vibración
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        }

        // Notificación en la barra de estado de Android (System Notification)
        context?.let { ctx ->
            lanzarNotificacionSistema(ctx, notif)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lanzarNotificacionSistema(ctx: Context, notif: NotificacionTactica) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        ctx,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@runCatching
                }
            }

            val channelId = "antired_tactico"
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
                val channel = NotificationChannel(
                    channelId,
                    "Mensajes y Notificaciones AntiRed",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos de nuevos mensajes y solicitudes de la red táctica"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    ctx,
                    notif.id.hashCode(),
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val titulo = when (notif.tipo) {
                TipoNotificacionTactica.MENSAJE -> "Mensaje de ${notif.remitenteNombre}"
                TipoNotificacionTactica.SOLICITUD_AMISTAD -> "Solicitud de amistad"
                TipoNotificacionTactica.AMIGO_AGREGADO -> "¡Nuevo amigo!"
                TipoNotificacionTactica.AMISTAD_ACEPTADA -> "¡Solicitud aceptada!"
                TipoNotificacionTactica.REGALO_MASCOTA -> "Regalo para tu mascota"
                TipoNotificacionTactica.LLAMADA -> "Llamada entrante"
            }

            val builder = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo)
                .setContentText(notif.subtitulo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notif.subtitulo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }

            NotificationManagerCompat.from(ctx).notify(
                notif.id.hashCode(),
                builder.build()
            )
        }.onFailure {
            android.util.Log.w("TacticalNotification", "No se pudo emitir notificación de sistema: ${it.message}")
        }
    }

    fun descartarNotificacion() {
        _notificacionActiva.value = null
    }
}

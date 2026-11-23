package com.renea.arete_social.feature.tamagotchi.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.renea.arete_social.MainActivity
import com.renea.arete_social.R

/**
 * Notificaciones locales de la mascota. Avisa cuando detecta:
 *  1. Un amigo cerca de ti.
 *  2. Un evento que coincide con tus gustos.
 */
@SuppressLint("MissingPermission")
object PetNotificationHelper {

    private const val CHANNEL_ID = "antired_mimi_radar_channel"
    private const val CHANNEL_NAME = "Avisos de tu mascota"
    private const val CHANNEL_DESC = "Tu mascota te avisa cuando hay amigos o eventos de tu interés cerca"

    private const val NOTIFICATION_ID_AMIGO = 7001
    private const val NOTIFICATION_ID_EVENTO = 7002
    private const val NOTIFICATION_ID_SUPER = 7003
    private const val NOTIFICATION_ID_LECTURA = 7004
    private const val NOTIFICATION_ID_OBRA_COMPARTIDA = 7005

    @Volatile
    private var channelCreated = false

    fun asegurarCanal(context: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
        channelCreated = true
    }

    /** Texto del aviso de amigo cercano (lo comparten la notificación y el globo de la pantalla). */
    fun textoAmigoCercano(mascota: String, amigoNombre: String, distanciaMetros: Int?): String =
        if (distanciaMetros != null) {
            "$mascota encontró a $amigoNombre a unos ${distanciaMetros} m. ¡Salúdalo!"
        } else {
            "$mascota encontró a $amigoNombre cerca de ti. ¡Salúdalo!"
        }

    @SuppressLint("MissingPermission")
    fun notificarAmigoCercano(context: Context, mascota: String, amigoNombre: String, distanciaMetros: Int? = null) {
        asegurarCanal(context)
        despacharNotificacion(
            context = context,
            notificationId = NOTIFICATION_ID_AMIGO,
            titulo = "$mascota encontró a un amigo",
            mensaje = textoAmigoCercano(mascota, amigoNombre, distanciaMetros)
        )
    }

    @SuppressLint("MissingPermission")
    fun notificarEventoInteres(context: Context, mascota: String, eventoTitulo: String, detalle: String) {
        asegurarCanal(context)
        val extra = if (detalle.isBlank()) "" else " $detalle"
        despacharNotificacion(
            context = context,
            notificationId = NOTIFICATION_ID_EVENTO,
            titulo = "$mascota encontró un plan para ti",
            mensaje = "«$eventoTitulo» va con tus gustos.$extra"
        )
    }

    /** Aviso de que el Modo Súper se activó (se usa cuando la app no está a la vista). */
    @SuppressLint("MissingPermission")
    fun notificarSuperPoderes(context: Context, mascota: String) {
        asegurarCanal(context)
        despacharNotificacion(
            context = context,
            notificationId = NOTIFICATION_ID_SUPER,
            titulo = "¡$mascota despertó sus súper poderes!",
            mensaje = "Durante unas horas ves los lugares y eventos públicos de tu zona regional, aunque estén lejos."
        )
    }

    @SuppressLint("MissingPermission")
    fun notificarAlguienLeyendo(context: Context, mascota: String, titulo: String? = null) {
        asegurarCanal(context)
        val detalle = if (!titulo.isNullOrBlank()) " tu obra «$titulo» en el mapa 📖" else " lo que compartiste en el mapa 📖"
        despacharNotificacion(
            context = context,
            notificationId = NOTIFICATION_ID_LECTURA,
            titulo = "Alguien está leyendo",
            mensaje = "$mascota te avisa: Alguien está leyendo$detalle"
        )
    }

    @SuppressLint("MissingPermission")
    fun notificarAlguienCompartioObra(context: Context, mascota: String, titulo: String, autor: String? = null) {
        asegurarCanal(context)
        val por = if (!autor.isNullOrBlank()) " de $autor" else ""
        despacharNotificacion(
            context = context,
            notificationId = NOTIFICATION_ID_OBRA_COMPARTIDA,
            titulo = "Alguien compartió una obra",
            mensaje = "$mascota te avisa: Alguien compartió una obra$por: «$titulo» 📜"
        )
    }


    @SuppressLint("MissingPermission")
    private fun despacharNotificacion(
        context: Context,
        notificationId: Int,
        titulo: String,
        mensaje: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val concedido = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!concedido) return
        }

        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Permiso de notificaciones denegado en tiempo de ejecución
            }
        }
    }
}

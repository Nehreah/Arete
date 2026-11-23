package com.renea.arete_social.gui.mapa.osm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.renea.arete_social.R
import kotlin.math.roundToInt

/**
 * Fábrica de iconos del mapa (sustituye a `BitmapDescriptorFactory`). Cada color se dibuja UNA vez
 * en un bitmap y se reutiliza en todos los marcadores: no se crea un Drawable por marcador ni por
 * recomposición. Crear una instancia por `OsmMapa`; hay que usarla desde el hilo principal.
 */
class IconosMapa(private val context: Context) {

    private val densidad = context.resources.displayMetrics.density
    private val pines = HashMap<ColorPin, Drawable>()
    private var puntoUbicacion: Drawable? = null

    /** Pin teñido con el color del tipo. Ancla recomendada: centro-abajo. */
    fun pin(color: ColorPin): Drawable = pines.getOrPut(color) {
        val vector = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_pin_mapa)) {
            "Falta res/drawable/ic_pin_mapa.xml"
        }.mutate()
        vector.setTint(color.argb)
        val ancho = (PIN_ANCHO_DP * densidad).roundToInt()
        val alto = (PIN_ALTO_DP * densidad).roundToInt()
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        vector.setBounds(0, 0, ancho, alto)
        vector.draw(Canvas(bitmap))
        BitmapDrawable(context.resources, bitmap)
    }

    /** Punto azul de "mi ubicación" con borde blanco. Ancla recomendada: centro. */
    fun puntoUbicacion(): Drawable = puntoUbicacion ?: run {
        val lado = (PUNTO_DP * densidad).roundToInt()
        val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = lado / 2f
        pintura.color = 0x33000000
        canvas.drawCircle(c, c + densidad, c - densidad, pintura)   // sombra suave
        pintura.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(c, c, c - densidad, pintura)
        pintura.color = ColorPin.AMIGO.argb
        canvas.drawCircle(c, c, c - 3.5f * densidad, pintura)
        BitmapDrawable(context.resources, bitmap).also { puntoUbicacion = it }
    }

    private companion object {
        const val PIN_ANCHO_DP = 30f
        const val PIN_ALTO_DP = 40f
        const val PUNTO_DP = 22f
    }
}

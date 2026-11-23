package com.renea.arete_social.feature.tamagotchi.regalos

import android.content.SharedPreferences
import com.renea.arete_social.feature.tamagotchi.data.PetRepository
import com.renea.arete_social.feature.tamagotchi.model.PetBalance
import com.renea.arete_social.feature.tamagotchi.model.PetType
import com.renea.arete_social.feature.tamagotchi.model.ResultadoCuidado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Envío y recepción de chispas y medicina entre mascotas. Todo es local al teléfono:
 *  - ENVIAR: desde el mapa (gato cercano o lista de Cercanos). Una chispa se descuenta de las
 *    tuyas; la medicina es gratis pero con una pausa por persona.
 *  - RECIBIR: el regalo llega por el canal de señales de la app (internet, Wi-Fi local o malla
 *    Bluetooth) y se aplica a la mascota de este teléfono. La mascota enferma solo se cura
 *    si alguien le envió medicina antes.
 *
 * El transporte y el aviso en pantalla se inyectan (lambdas) para poder probar la lógica sin Android.
 */
class GestorRegalosMascota(
    private val repositorio: PetRepository,
    private val prefs: SharedPreferences,
    private val miIdProveedor: () -> String?,
    private val miNombreProveedor: () -> String?,
    private val transporte: (
        destinatarioId: String, regaloId: String, tipo: TipoRegalo, cantidad: Int, remitenteNombre: String
    ) -> Unit,
    private val alNotificar: (regalo: RegaloMascotaEntrante, texto: String) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ahora: () -> Long = System::currentTimeMillis
) {
    private val ultimaMedicinaEnviada = ConcurrentHashMap<String, Long>()
    private val lock = Any()

    // ── Enviar ───────────────────────────────────────────────────────────────

    suspend fun enviar(destinatarioId: String, tipo: TipoRegalo): ResultadoEnvioRegalo {
        val miId = miIdProveedor()?.takeIf { it.isNotBlank() } ?: return ResultadoEnvioRegalo.SinSesion
        if (destinatarioId.isBlank() || destinatarioId == miId) return ResultadoEnvioRegalo.ATiMismo

        if (tipo == TipoRegalo.MEDICINA) {
            val ultimo = ultimaMedicinaEnviada[destinatarioId]
            if (ultimo != null) {
                val faltan = PetBalance.MEDICINE_SEND_COOLDOWN_MILLIS - (ahora() - ultimo)
                if (faltan > 0) return ResultadoEnvioRegalo.EsperaMedicina(faltan / 1000L)
            }
        } else {
            // La chispa cuesta una de las tuyas. Si no alcanza, no se envía nada.
            when (repositorio.giftSparks(miId, CHISPAS_POR_ENVIO)) {
                ResultadoCuidado.OK -> Unit
                ResultadoCuidado.SIN_MASCOTA -> return ResultadoEnvioRegalo.SinMascota
                else -> return ResultadoEnvioRegalo.SinChispas
            }
        }

        if (tipo == TipoRegalo.MEDICINA) ultimaMedicinaEnviada[destinatarioId] = ahora()
        val cantidad = if (tipo == TipoRegalo.CHISPA) CHISPAS_POR_ENVIO else 1
        transporte(destinatarioId, UUID.randomUUID().toString(), tipo, cantidad, miNombreProveedor().orEmpty())
        return ResultadoEnvioRegalo.Enviado
    }

    // ── Recibir ──────────────────────────────────────────────────────────────

    /** Llamado por el transporte cuando llega un regalo. Se puede invocar desde cualquier hilo. */
    fun recibir(regalo: RegaloMascotaEntrante) {
        scope.launch {
            val miId = miIdProveedor()?.takeIf { it.isNotBlank() } ?: return@launch
            if (regalo.remitenteId == miId) return@launch
            if (!registrarSiEsNuevo(regalo.regaloId)) return@launch
            if (superaLimiteDiario(regalo.remitenteId)) return@launch

            // Si la pantalla de la mascota nunca se abrió todavía, se crea ahora para no perder el regalo.
            repositorio.ensurePetExists(miId, PetBalance.DEFAULT_NAME, PetType.GATO)
            val cantidadSegura = regalo.cantidad.coerceIn(1, CHISPAS_POR_ENVIO)
            val resultado = when (regalo.tipo) {
                TipoRegalo.CHISPA -> repositorio.receiveSparks(miId, cantidadSegura)
                TipoRegalo.MEDICINA -> repositorio.receiveMedicine(miId)
            }
            if (!resultado.exito) return@launch

            val mascota = repositorio.observePet(miId).first()?.name ?: PetBalance.DEFAULT_NAME
            val quien = regalo.remitenteNombre.ifBlank { "Alguien" }
            val texto = when (regalo.tipo) {
                TipoRegalo.CHISPA -> "$quien le envió una chispa ✦ a $mascota"
                TipoRegalo.MEDICINA -> "$quien le envió medicina 💊 a $mascota"
            }
            alNotificar(regalo, texto)
        }
    }

    /** Limita el número de regalos diarios que un mismo remitente puede enviar. */
    private fun superaLimiteDiario(remitenteId: String): Boolean {
        if (remitenteId.isBlank()) return false
        synchronized(lock) {
            val hoy = ahora() / (24 * 60 * 60 * 1000L)
            val clave = "regalos_dia_${remitenteId}_$hoy"
            val conteo = prefs.getInt(clave, 0)
            if (conteo >= MAX_REGALOS_POR_REMITENTE_DIA) return true
            prefs.edit().putInt(clave, conteo + 1).apply()
            return false
        }
    }

    /** True si el regalo no se había visto. Recuerda los últimos ids aunque se cierre la app. */
    private fun registrarSiEsNuevo(regaloId: String): Boolean {
        if (regaloId.isBlank()) return false
        synchronized(lock) {
            val vistos = prefs.getString(CLAVE_VISTOS, "").orEmpty().split(',').filter { it.isNotBlank() }
            if (regaloId in vistos) return false
            val nuevos = (vistos + regaloId).takeLast(MAX_VISTOS)
            prefs.edit().putString(CLAVE_VISTOS, nuevos.joinToString(",")).apply()
            return true
        }
    }

    private companion object {
        const val CHISPAS_POR_ENVIO = 1
        const val MAX_VISTOS = 1000
        const val MAX_REGALOS_POR_REMITENTE_DIA = 10
        const val CLAVE_VISTOS = "regalos_vistos"
    }
}

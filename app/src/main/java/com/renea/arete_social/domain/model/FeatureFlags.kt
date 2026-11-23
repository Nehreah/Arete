package com.renea.arete_social.domain.model

/**
 * Flags de activación progresiva para las fases de sincronización P2P local.
 * Permiten habilitar o deshabilitar subsistemas para pruebas de latencia o mitigación de regresiones.
 */
object FeatureFlags {
    /** Fase 2: Protocolo de perfil por secciones y manifiesto v2. */
    var SYNC_V2: Boolean = true

    /** Fase 3: Recolección proactiva de perfiles de pares cercanos por BLE. */
    var SYNC_AUTO: Boolean = true

    /** Fase 1: Despacho dirigido de trozos a un único peer destino en el router mesh. */
    var SYNC_ENVIO_DIRIGIDO: Boolean = true

    /** Fase 5: Operación y reconciliación de comunidades 100% P2P local. */
    var COMUNIDADES_LOCAL: Boolean = true

    /** Fase 6: Reconciliación y almacenamiento de eventos 100% P2P local. */
    var EVENTOS_LOCAL: Boolean = true

    /**
     * Miau Snake · Fase 6 (duelo con un teléfono cercano). APAGADO por defecto: se enciende solo cuando la
     * medición del transporte en teléfonos reales (spike) cumple los umbrales. Aun encendido, cada teléfono
     * exige además una medición propia APTA y reciente antes de poder invitar.
     */
    var MIAUSNAKE_CERCANIA: Boolean = false
}

/**
 * Canales dedicados al plano de control y sincronización de datos P2P.
 * La mensajería y llamadas deben ignorar estos canales de forma temprana para evitar
 * contaminación en la base de datos de chat y falsas notificaciones.
 */
object ProtocoloCanalesMesh {
    const val CANAL_PERFIL_SYNC = "perfil-sync"
    const val CANAL_PERFIL_MEDIA = "perfil-media"
    const val CANAL_PERFIL_SEC = "perfil-sec"
    const val CANAL_EVENT_SYNC = "event-sync"
    const val CANAL_CARTAS_MESH = "cartas-mesh"
    const val CANAL_PASE_DESCUBRIMIENTO = "pase-descubrimiento"
    const val CANAL_IDENT_SYNC = "ident-sync"

    /** Miau Snake · duelo cercano. Plano de control del juego: nunca es un chat. */
    const val CANAL_MIAUSNAKE_MATCH = "miausnake-match"

    /**
     * Prefijo de los canales de sincronización de comunidades P2P (community:sync:<communityIdHex>).
     * Son canales dinámicos — no se pueden añadir a un Set fijo.
     */
    const val CANAL_COMMUNITY_SYNC_PREFIX = "community:sync:"

    /** Conjunto de canales de control P2P que no son mensajes de chat. */
    val CANALES_CONTROL_P2P = setOf(
        CANAL_PERFIL_SYNC,
        CANAL_PERFIL_MEDIA,
        CANAL_PERFIL_SEC,
        CANAL_EVENT_SYNC,
        CANAL_CARTAS_MESH,
        CANAL_PASE_DESCUBRIMIENTO,
        CANAL_IDENT_SYNC,
        CANAL_MIAUSNAKE_MATCH
    )

    /**
     * Devuelve true si el chatId corresponde a un canal de control P2P (nunca chat visible).
     * Incluye canales fijos y el prefijo dinámico de sincronización de comunidades.
     */
    fun esCanalP2P(chatId: String): Boolean =
        chatId in CANALES_CONTROL_P2P || chatId.startsWith(CANAL_COMMUNITY_SYNC_PREFIX)
}


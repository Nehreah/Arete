package com.renea.arete_social.data.local.outbox

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "GestorOutboxLocal"
private const val DB_NAME = "antired_outbox.db"
private const val DB_VERSION = 4
private const val TABLA_OUTBOX = "mensajes_outbox"
private const val TABLA_DIRECTOS = "chats_directos_local"
private const val TABLA_PAQUETES_VISTOS = "paquetes_vistos"

/** Máximo de reintentos hacia la red Mesh antes de dejar de insistir con un mensaje. */
const val MAX_INTENTOS_OUTBOX = 20

/**
 * Cola de salida + historial local de mensajes (SQLite).
 */
class GestorOutboxLocal(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val _outboxCambios = MutableStateFlow(System.currentTimeMillis())
    val outboxCambios: Flow<Long> = _outboxCambios.asStateFlow()

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLA_OUTBOX (
                id TEXT PRIMARY KEY,
                chat_id TEXT NOT NULL,
                remitente_id TEXT NOT NULL,
                destinatario_id TEXT,
                contenido TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                estado TEXT NOT NULL DEFAULT 'PENDIENTE',
                intentos INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        crearIndices(db)
        crearTablaDirectos(db)
        crearTablaPaquetesVistos(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            crearIndices(db)
            crearTablaDirectos(db)
        }
        if (oldVersion < 3) {
            val columnExists = runCatching {
                db.rawQuery("PRAGMA table_info($TABLA_DIRECTOS)", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    var exists = false
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && cursor.getString(nameIdx) == "creado_en") {
                            exists = true
                            break
                        }
                    }
                    exists
                }
            }.getOrDefault(false)

            if (!columnExists) {
                db.execSQL("ALTER TABLE $TABLA_DIRECTOS ADD COLUMN creado_en INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE $TABLA_DIRECTOS SET creado_en = ${System.currentTimeMillis()}")
            }
        }
        if (oldVersion < 4) {
            crearTablaPaquetesVistos(db)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "onDowngrade detectado de v$oldVersion a v$newVersion. Verificando integridad de tablas críticas.")
        crearIndices(db)
        crearTablaDirectos(db)
        crearTablaPaquetesVistos(db)
    }

    private fun crearTablaPaquetesVistos(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLA_PAQUETES_VISTOS (
                id TEXT PRIMARY KEY,
                visto_en INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_paquetes_vistos_tiempo ON $TABLA_PAQUETES_VISTOS(visto_en)")
    }

    private fun crearTablaDirectos(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLA_DIRECTOS (
                chat_id TEXT NOT NULL,
                mi_id TEXT NOT NULL,
                otro_id TEXT NOT NULL,
                creado_en INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (chat_id, mi_id)
            )
            """.trimIndent()
        )
    }

    private fun crearIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_chat ON $TABLA_OUTBOX(chat_id, timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_estado ON $TABLA_OUTBOX(estado, timestamp)")
    }

    private fun notificarCambio() {
        _outboxCambios.value = System.nanoTime()
    }

    private fun ContentValues.desde(msg: MensajeOutbox) = apply {
        put("id", msg.id)
        put("chat_id", msg.chatId)
        put("remitente_id", msg.remitenteId)
        put("destinatario_id", msg.destinatarioId)
        put("contenido", msg.contenido)
        put("timestamp", msg.timestamp)
        put("estado", msg.estado.name)
        put("intentos", msg.intentos)
    }

    private fun Cursor.aMensaje() = MensajeOutbox(
        id = getString(getColumnIndexOrThrow("id")),
        chatId = getString(getColumnIndexOrThrow("chat_id")),
        remitenteId = getString(getColumnIndexOrThrow("remitente_id")),
        destinatarioId = getString(getColumnIndexOrThrow("destinatario_id")),
        contenido = getString(getColumnIndexOrThrow("contenido")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        estado = runCatching { EstadoEnvioOutbox.valueOf(getString(getColumnIndexOrThrow("estado"))) }
            .getOrDefault(EstadoEnvioOutbox.PENDIENTE),
        intentos = getInt(getColumnIndexOrThrow("intentos"))
    )

    private fun consultar(
        seleccion: String?,
        args: Array<String>?,
        orden: String = "timestamp ASC",
        limite: String? = null
    ): List<MensajeOutbox> {
        val lista = mutableListOf<MensajeOutbox>()
        try {
            readableDatabase.query(TABLA_OUTBOX, null, seleccion, args, null, null, orden, limite).use { c ->
                while (c.moveToNext()) lista.add(c.aMensaje())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo outbox ($seleccion): ${e.message}", e)
        }
        return lista
    }

    /** Guarda un mensaje PROPIO (nuevo o reintento). Reemplaza si el id ya existe. */
    suspend fun guardarMensaje(msg: MensajeOutbox): Unit = withContext(Dispatchers.IO) {
        try {
            writableDatabase.insertWithOnConflict(
                TABLA_OUTBOX, null, ContentValues().desde(msg), SQLiteDatabase.CONFLICT_REPLACE
            )
            notificarCambio()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando mensaje ${msg.id}: ${e.message}", e)
        }
    }

    /**
     * Guarda un mensaje RECIBIDO (Mesh / Wi-Fi). Si el id ya existe NO lo toca.
     * @return true si se insertó una fila nueva.
     */
    suspend fun guardarRecibido(msg: MensajeOutbox): Boolean = withContext(Dispatchers.IO) {
        try {
            val fila = writableDatabase.insertWithOnConflict(
                TABLA_OUTBOX, null,
                ContentValues().desde(msg.copy(estado = EstadoEnvioOutbox.ENVIADO)),
                SQLiteDatabase.CONFLICT_IGNORE
            )
            if (fila != -1L) notificarCambio()
            fila != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando mensaje recibido ${msg.id}: ${e.message}", e)
            false
        }
    }

    suspend fun obtenerPendientes(maxIntentos: Int = MAX_INTENTOS_OUTBOX): List<MensajeOutbox> =
        withContext(Dispatchers.IO) {
            consultar("estado = ? AND intentos < ?", arrayOf(EstadoEnvioOutbox.PENDIENTE.name, maxIntentos.toString()))
        }

    suspend fun obtenerMensajesPorChat(chatId: String): List<MensajeOutbox> =
        withContext(Dispatchers.IO) { consultar("chat_id = ?", arrayOf(chatId)) }

    suspend fun obtenerUltimoMensaje(chatId: String): MensajeOutbox? =
        withContext(Dispatchers.IO) {
            consultar("chat_id = ?", arrayOf(chatId), orden = "timestamp DESC", limite = "1").firstOrNull()
        }

    suspend fun obtenerTodosLosMensajes(): List<MensajeOutbox> =
        withContext(Dispatchers.IO) { consultar(null, null) }

    /**
     * Devuelve el "otro" participante de un chat directo a partir del historial local.
     * Solo mira filas con destinatario_id != null: los mensajes de grupo/sala se guardan con
     * destinatario NULL, así que un chat grupal NUNCA se confunde con un DM.
     */
    suspend fun obtenerDestinatarioDirecto(chatId: String, miId: String): String? =
        withContext(Dispatchers.IO) {
            consultar("chat_id = ? AND destinatario_id IS NOT NULL", arrayOf(chatId))
                .firstNotNullOfOrNull { m ->
                    if (m.remitenteId == miId) m.destinatarioId?.takeIf { it != miId }
                    else m.remitenteId
                }
        }

    /**
     * Registra la asociación chatId → otroId para un chat directo.
     *
     * H-04: si ya existe un mapeo distinto para ese (chatId, miId), lo conserva
     * y descarta el nuevo. Solo se reemplaza cuando el llamador lo indica
     * explícitamente (p.ej. flujos locales de confianza como abrir un DM propio).
     */
    suspend fun registrarChatDirecto(
        chatId: String,
        miId: String,
        otroId: String,
        permitirReemplazo: Boolean = false   // H-04: false por defecto; solo true en flujos locales
    ): Unit = withContext(Dispatchers.IO) {
            if (chatId.isBlank() || miId.isBlank() || otroId.isBlank() || miId == otroId) return@withContext
            try {
                val db = writableDatabase
                val previo = db.query(
                    TABLA_DIRECTOS, arrayOf("otro_id"), "chat_id = ? AND mi_id = ?",
                    arrayOf(chatId, miId), null, null, null, "1"
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

                if (previo == otroId) return@withContext      // ya correcto, nada que hacer
                if (previo != null && !permitirReemplazo) {   // H-04: proteger mapeo existente
                    Log.w(TAG, "H-04: Intento de reasignar chat $chatId de '$previo' a '$otroId' rechazado.")
                    return@withContext
                }

                val cv = ContentValues().apply {
                    put("chat_id", chatId); put("mi_id", miId); put("otro_id", otroId)
                }
                db.insertWithOnConflict(
                    TABLA_DIRECTOS, null, cv,
                    if (permitirReemplazo) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE
                )
                // Un chat nuevo debe aparecer en "Activos" sin esperar a que llegue un mensaje.
                notificarCambio()
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando chat directo $chatId: ${e.message}", e)
            }
        }


    suspend fun obtenerOtroDeChatDirecto(chatId: String, miId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                readableDatabase.query(
                    TABLA_DIRECTOS, arrayOf("otro_id"), "chat_id = ? AND mi_id = ?",
                    arrayOf(chatId, miId), null, null, null, "1"
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo chat directo $chatId: ${e.message}", e)
                null
            }
        }

    /** chatId -> otroId para todos los DMs conocidos localmente de [miId]. */
    suspend fun obtenerChatsDirectosLocales(miId: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val mapa = mutableMapOf<String, String>()
            try {
                readableDatabase.query(
                    TABLA_DIRECTOS, arrayOf("chat_id", "otro_id"), "mi_id = ?",
                    arrayOf(miId), null, null, null
                ).use { c -> while (c.moveToNext()) mapa[c.getString(0)] = c.getString(1) }
            } catch (e: Exception) {
                Log.e(TAG, "Error listando chats directos: ${e.message}", e)
            }
            mapa
        }

    /** chatId -> timestamp de creación de DMs conocidos localmente de [miId]. */
    suspend fun obtenerChatsDirectosConTimestamp(miId: String): Map<String, Long> =
        withContext(Dispatchers.IO) {
            val mapa = mutableMapOf<String, Long>()
            try {
                readableDatabase.query(
                    TABLA_DIRECTOS, arrayOf("chat_id", "creado_en"), "mi_id = ?",
                    arrayOf(miId), null, null, null
                ).use { c -> while (c.moveToNext()) mapa[c.getString(0)] = c.getLong(1) }
            } catch (e: Exception) {
                Log.e(TAG, "Error listando timestamps de chats directos: ${e.message}", e)
            }
            mapa
        }

    suspend fun marcarEnviado(id: String): Unit = withContext(Dispatchers.IO) {
        try {
            writableDatabase.execSQL(
                "UPDATE $TABLA_OUTBOX SET estado = ? WHERE id = ?",
                arrayOf(EstadoEnvioOutbox.ENVIADO.name, id)
            )
            notificarCambio()
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando enviado $id: ${e.message}", e)
        }
    }

    suspend fun incrementarIntentos(id: String): Unit = withContext(Dispatchers.IO) {
        try {
            // Sin notificarCambio(): un reintento fallido no cambia nada visible y notificar
            // aquí re-disparaba a los observadores en cada intento.
            writableDatabase.execSQL("UPDATE $TABLA_OUTBOX SET intentos = intentos + 1 WHERE id = ?", arrayOf(id))
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementando intentos $id: ${e.message}", e)
        }
    }

    suspend fun purgarAntiguos(maxHoras: Long = 48): Unit = withContext(Dispatchers.IO) {
        try {
            val limiteMs = System.currentTimeMillis() - (maxHoras * 3600 * 1000)
            // Solo purgar mensajes que ya fueron confirmados como ENVIADOS.
            // Los mensajes PENDIENTES nunca se borran automáticamente para no perder envíos offline.
            val borradas = writableDatabase.delete(
                TABLA_OUTBOX,
                "timestamp < ? AND estado = ?",
                arrayOf(limiteMs.toString(), EstadoEnvioOutbox.ENVIADO.name)
            )
            if (borradas > 0) notificarCambio()
        } catch (e: Exception) {
            Log.e(TAG, "Error purgando outbox: ${e.message}", e)
        }
    }

    /** Elimina un mensaje específico de la outbox local por su id. */
    suspend fun eliminarMensaje(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val borradas = writableDatabase.delete(TABLA_OUTBOX, "id = ?", arrayOf(id))
            if (borradas > 0) notificarCambio()
            borradas > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando mensaje $id de outbox: ${e.message}", e)
            false
        }
    }

    /** Elimina todos los mensajes e historial de un chat o conversación local. */
    suspend fun eliminarConversacion(chatId: String, chatIdAlternativo: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            val chatsABorrar = listOfNotNull(chatId, chatIdAlternativo).distinct()
            var borradas = 0
            for (c in chatsABorrar) {
                borradas += db.delete(TABLA_OUTBOX, "chat_id = ?", arrayOf(c))
                db.delete(TABLA_DIRECTOS, "chat_id = ?", arrayOf(c))
            }
            if (borradas > 0) notificarCambio()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando conversación $chatId: ${e.message}", e)
            false
        }
    }

    suspend fun purgarTodo(): Unit = withContext(Dispatchers.IO) {
        try {
            writableDatabase.delete(TABLA_OUTBOX, null, null)
            writableDatabase.delete(TABLA_DIRECTOS, null, null)
            writableDatabase.delete(TABLA_PAQUETES_VISTOS, null, null)
            notificarCambio()
        } catch (e: Exception) {
            Log.e(TAG, "Error purgando todo: ${e.message}", e)
        }
    }

    // ── Deduplicación persistente de paquetes Mesh (anti-replay 24h) ──────────

    suspend fun esPaqueteVisto(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            readableDatabase.rawQuery(
                "SELECT 1 FROM $TABLA_PAQUETES_VISTOS WHERE id = ? LIMIT 1",
                arrayOf(id)
            ).use { cursor ->
                cursor.moveToFirst()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando paquete visto en SQLite: ${e.message}", e)
            false
        }
    }

    suspend fun marcarPaqueteVisto(id: String, vistoEn: Long = System.currentTimeMillis()): Unit = withContext(Dispatchers.IO) {
        try {
            val cv = ContentValues().apply {
                put("id", id)
                put("visto_en", vistoEn)
            }
            writableDatabase.insertWithOnConflict(
                TABLA_PAQUETES_VISTOS,
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE
            )
            // Purgar periódicamente paquetes más viejos de 24 horas y limitar el tamaño a 5000 registros
            purgarPaquetesVistosExpirados(limiteMs = vistoEn - (24 * 3600 * 1000L))
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando paquete visto en SQLite: ${e.message}", e)
        }
    }

    suspend fun purgarPaquetesVistosExpirados(limiteMs: Long, maxFilas: Int = 5000): Unit = withContext(Dispatchers.IO) {
        try {
            writableDatabase.delete(TABLA_PAQUETES_VISTOS, "visto_en < ?", arrayOf(limiteMs.toString()))
            writableDatabase.execSQL(
                """
                DELETE FROM $TABLA_PAQUETES_VISTOS
                WHERE id NOT IN (
                    SELECT id FROM $TABLA_PAQUETES_VISTOS ORDER BY visto_en DESC LIMIT $maxFilas
                )
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error purgando paquetes vistos expirados: ${e.message}", e)
        }
    }
}

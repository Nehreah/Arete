package com.renea.arete_social.data.local.descubrimientos

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.renea.arete_social.domain.model.Descubrimiento
import com.renea.arete_social.domain.model.EntidadSnapshot
import com.renea.arete_social.domain.model.TipoEntidadMapa
import com.renea.arete_social.domain.model.VisibilidadEvento
import com.renea.arete_social.domain.repository.AlmacenDescubrimientos

private const val TAG = "GestorDescubrimientos"
private const val DB_NAME = "antired_descubrimientos.db"
private const val DB_VERSION = 1
private const val TABLA = "descubrimientos"

/**
 * Lugares y eventos descubiertos (SQLite propio, separado de la base del Outbox para no
 * tocar su esquema). Una fila por persona y por lugar/evento; guarda una copia de lo esencial
 * para dibujarlo sin internet y las marcas de tiempo que usan las reglas de 24 h.
 */
class GestorDescubrimientosLocal(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION), AlmacenDescubrimientos {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLA (
                usuario_id TEXT NOT NULL,
                clave TEXT NOT NULL,
                tipo TEXT NOT NULL,
                entidad_id TEXT NOT NULL,
                nombre TEXT NOT NULL,
                descripcion TEXT NOT NULL DEFAULT '',
                latitud REAL NOT NULL,
                longitud REAL NOT NULL,
                chat_id TEXT NOT NULL DEFAULT '',
                fecha_evento INTEGER,
                visibilidad TEXT NOT NULL DEFAULT 'PUBLICO',
                ultimo_visto_cerca INTEGER,
                pase_recibido_en INTEGER,
                pase_emisor_id TEXT,
                PRIMARY KEY (usuario_id, clave)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 es la primera versión: nada que migrar todavía.
    }

    override fun cargar(usuarioId: String): List<Descubrimiento> = runCatching {
        readableDatabase.query(
            TABLA, null, "usuario_id = ?", arrayOf(usuarioId), null, null, null
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.aDescubrimiento()) }
        }
    }.getOrElse {
        Log.e(TAG, "No se pudieron leer los descubrimientos: ${it.message}")
        emptyList()
    }

    override fun guardar(usuarioId: String, descubrimiento: Descubrimiento) {
        runCatching {
            val s = descubrimiento.snapshot
            val valores = ContentValues().apply {
                put("usuario_id", usuarioId)
                put("clave", s.clave)
                put("tipo", s.tipo.name)
                put("entidad_id", s.id)
                put("nombre", s.nombre)
                put("descripcion", s.descripcion)
                put("latitud", s.latitud)
                put("longitud", s.longitud)
                put("chat_id", s.chatId)
                if (s.fechaEvento != null) put("fecha_evento", s.fechaEvento) else putNull("fecha_evento")
                put("visibilidad", s.visibilidad.name)
                if (descubrimiento.ultimoVistoCerca != null) put("ultimo_visto_cerca", descubrimiento.ultimoVistoCerca)
                else putNull("ultimo_visto_cerca")
                if (descubrimiento.paseRecibidoEn != null) put("pase_recibido_en", descubrimiento.paseRecibidoEn)
                else putNull("pase_recibido_en")
                if (descubrimiento.paseEmisorId != null) put("pase_emisor_id", descubrimiento.paseEmisorId)
                else putNull("pase_emisor_id")
            }
            writableDatabase.insertWithOnConflict(TABLA, null, valores, SQLiteDatabase.CONFLICT_REPLACE)
        }.onFailure { Log.e(TAG, "No se pudo guardar el descubrimiento: ${it.message}") }
    }

    override fun eliminar(usuarioId: String, clave: String) {
        runCatching {
            writableDatabase.delete(TABLA, "usuario_id = ? AND clave = ?", arrayOf(usuarioId, clave))
        }.onFailure { Log.e(TAG, "No se pudo borrar el descubrimiento: ${it.message}") }
    }

    override fun limpiar(usuarioId: String?) {
        runCatching {
            if (usuarioId == null) writableDatabase.delete(TABLA, null, null)
            else writableDatabase.delete(TABLA, "usuario_id = ?", arrayOf(usuarioId))
        }.onFailure { Log.e(TAG, "No se pudo limpiar: ${it.message}") }
    }

    private fun Cursor.aDescubrimiento(): Descubrimiento {
        fun largoONulo(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
        fun textoONulo(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
        val tipo = TipoEntidadMapa.valueOf(getString(getColumnIndexOrThrow("tipo")))
        return Descubrimiento(
            snapshot = EntidadSnapshot(
                tipo = tipo,
                id = getString(getColumnIndexOrThrow("entidad_id")),
                nombre = getString(getColumnIndexOrThrow("nombre")),
                descripcion = getString(getColumnIndexOrThrow("descripcion")),
                latitud = getDouble(getColumnIndexOrThrow("latitud")),
                longitud = getDouble(getColumnIndexOrThrow("longitud")),
                chatId = getString(getColumnIndexOrThrow("chat_id")),
                fechaEvento = largoONulo("fecha_evento"),
                visibilidad = VisibilidadEvento.desdeServidor(textoONulo("visibilidad"))
            ),
            ultimoVistoCerca = largoONulo("ultimo_visto_cerca"),
            paseRecibidoEn = largoONulo("pase_recibido_en"),
            paseEmisorId = textoONulo("pase_emisor_id")
        )
    }
}

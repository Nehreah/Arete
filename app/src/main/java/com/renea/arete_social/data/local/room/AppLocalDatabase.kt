package com.renea.arete_social.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.renea.arete_social.data.local.room.dao.CommunityDao
import com.renea.arete_social.data.local.room.dao.EventDao
import com.renea.arete_social.data.local.room.dao.IdentityDao
import com.renea.arete_social.data.local.room.dao.LedgerDao
import com.renea.arete_social.data.local.room.dao.PeerProfileDao
import com.renea.arete_social.data.local.room.dao.PeerSectionDao
import com.renea.arete_social.data.local.room.entity.CommunityEntity
import com.renea.arete_social.data.local.room.entity.CommunityMemberEntity
import com.renea.arete_social.data.local.room.entity.EpochKeyEntity
import com.renea.arete_social.data.local.room.entity.EventEntity
import com.renea.arete_social.data.local.room.entity.LedgerOperationEntity
import com.renea.arete_social.data.local.room.dao.EventParticipantDao
import com.renea.arete_social.data.local.room.entity.EventParticipantEntity
import com.renea.arete_social.data.local.room.entity.LocalIdentityEntity
import com.renea.arete_social.data.local.room.entity.PeerBitacoraEntity
import com.renea.arete_social.data.local.room.entity.PeerGustosEntity
import com.renea.arete_social.data.local.room.entity.PeerLibreriaEntity
import com.renea.arete_social.data.local.room.entity.PeerProfileEntity

/**
 * Base de datos Room local-first para el subsistema descentralizado de Aréte (Auditoría v3).
 *
 * Convive con el SQLite directo existente (outbox, descubrimientos) sin migración.
 * Este es el motor de persistencia exclusivo para el protocolo P2P local-first.
 */
@Database(
    entities = [
        LocalIdentityEntity::class,
        CommunityEntity::class,
        CommunityMemberEntity::class,
        LedgerOperationEntity::class,
        EpochKeyEntity::class,
        EventEntity::class,
        EventParticipantEntity::class,
        PeerProfileEntity::class,
        PeerGustosEntity::class,
        PeerBitacoraEntity::class,
        PeerLibreriaEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppLocalDatabase : RoomDatabase() {

    abstract fun identityDao(): IdentityDao
    abstract fun communityDao(): CommunityDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun eventDao(): EventDao
    abstract fun eventParticipantDao(): EventParticipantDao
    abstract fun peerProfileDao(): PeerProfileDao
    abstract fun peerSectionDao(): PeerSectionDao

    companion object {
        const val DB_NAME = "arete_local_p2p.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `peer_profile` (
                        `usuario_id` TEXT NOT NULL,
                        `nombre` TEXT NOT NULL,
                        `descripcion` TEXT NOT NULL DEFAULT '',
                        `foto_path` TEXT,
                        `foto_hash` TEXT,
                        `portada_path` TEXT,
                        `portada_hash` TEXT,
                        `perfil_ver` INTEGER NOT NULL DEFAULT 0,
                        `es_amigo` INTEGER NOT NULL DEFAULT 0,
                        `ultima_vez_visto` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`usuario_id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `community` ADD COLUMN `tipo` TEXT NOT NULL DEFAULT 'PRIVADA'")
                db.execSQL("ALTER TABLE `community` ADD COLUMN `foto_url` TEXT")
                db.execSQL("ALTER TABLE `community` ADD COLUMN `portada_url` TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `peer_profile` ADD COLUMN `es_privado` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `peer_gustos` (
                        `usuario_id` TEXT NOT NULL,
                        `intereses_json` TEXT NOT NULL,
                        `categorias_json` TEXT NOT NULL DEFAULT '[]',
                        `actualizado_en` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`usuario_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `peer_bitacora` (
                        `estado_id` TEXT NOT NULL,
                        `usuario_id` TEXT NOT NULL,
                        `texto` TEXT NOT NULL,
                        `foto_url` TEXT,
                        `foto_hash` TEXT,
                        `fecha` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`estado_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_peer_bitacora_usuario_id` ON `peer_bitacora` (`usuario_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_peer_bitacora_fecha` ON `peer_bitacora` (`fecha`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `peer_libreria` (
                        `obra_id` TEXT NOT NULL,
                        `usuario_id` TEXT NOT NULL,
                        `titulo` TEXT NOT NULL,
                        `sinopsis` TEXT NOT NULL DEFAULT '',
                        `tipo` TEXT NOT NULL DEFAULT 'CUENTO',
                        `caracteres_total` INTEGER NOT NULL DEFAULT 0,
                        `contenido` TEXT,
                        `actualizado_en` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`obra_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_peer_libreria_usuario_id` ON `peer_libreria` (`usuario_id`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_event` ADD COLUMN `firma_organizador` TEXT")
                db.execSQL("ALTER TABLE `local_event` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_event_participant` (
                        `evento_id` TEXT NOT NULL,
                        `usuario_id` TEXT NOT NULL,
                        `rol` TEXT NOT NULL DEFAULT 'ASISTENTE',
                        `updated_at_ms` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`evento_id`, `usuario_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_event_participant_evento_id` ON `local_event_participant` (`evento_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_event_participant_usuario_id` ON `local_event_participant` (`usuario_id`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_event` ADD COLUMN `foto_url` TEXT")
                db.execSQL("ALTER TABLE `local_event` ADD COLUMN `foto_hash` TEXT")
                db.execSQL("ALTER TABLE `community` ADD COLUMN `foto_hash` TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppLocalDatabase? = null

        fun getInstance(context: Context): AppLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppLocalDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

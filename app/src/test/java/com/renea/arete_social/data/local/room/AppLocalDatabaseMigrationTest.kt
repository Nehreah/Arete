package com.renea.arete_social.data.local.room

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocalDatabaseMigrationTest {

    @Test
    fun testMigrationVersionsSequence() {
        assertEquals(1, AppLocalDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppLocalDatabase.MIGRATION_1_2.endVersion)

        assertEquals(2, AppLocalDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppLocalDatabase.MIGRATION_2_3.endVersion)

        assertEquals(3, AppLocalDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppLocalDatabase.MIGRATION_3_4.endVersion)

        assertEquals(4, AppLocalDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppLocalDatabase.MIGRATION_4_5.endVersion)

        assertEquals(5, AppLocalDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppLocalDatabase.MIGRATION_5_6.endVersion)

        assertEquals(6, AppLocalDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, AppLocalDatabase.MIGRATION_6_7.endVersion)

        assertEquals(7, AppLocalDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, AppLocalDatabase.MIGRATION_7_8.endVersion)
    }

    @Test
    fun testAllMigrationsExecuteValidSql() {
        val executedStatements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any()) } answers {
            executedStatements.add(firstArg())
        }

        AppLocalDatabase.MIGRATION_1_2.migrate(db)
        AppLocalDatabase.MIGRATION_2_3.migrate(db)
        AppLocalDatabase.MIGRATION_3_4.migrate(db)
        AppLocalDatabase.MIGRATION_4_5.migrate(db)
        AppLocalDatabase.MIGRATION_5_6.migrate(db)
        AppLocalDatabase.MIGRATION_6_7.migrate(db)
        AppLocalDatabase.MIGRATION_7_8.migrate(db)

        verify(atLeast = 10) { db.execSQL(any()) }
        // Verify key tables and columns are altered or created
        assert(executedStatements.any { it.contains("peer_profile") })
        assert(executedStatements.any { it.contains("peer_gustos") })
        assert(executedStatements.any { it.contains("peer_bitacora") })
        assert(executedStatements.any { it.contains("peer_libreria") })
        assert(executedStatements.any { it.contains("local_event_participant") })
        assert(executedStatements.any { it.contains("foto_hash") })
    }
}

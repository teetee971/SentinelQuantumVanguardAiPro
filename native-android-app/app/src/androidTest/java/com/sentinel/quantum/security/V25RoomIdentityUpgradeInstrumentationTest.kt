package com.sentinel.quantum.security

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a pre-existing Room file that already carries the V25 identity and 29 synthetic rows.
 * No phone number, name, or message body is written.
 */
@RunWith(AndroidJUnit4::class)
class V25RoomIdentityUpgradeInstrumentationTest {
    @Test
    fun twentyNineSyntheticDecisionsSurviveOpenOfTheBundledIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '$IDENTITY')"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS call_filter_decisions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "occurredAtMs INTEGER NOT NULL, " +
                    "action TEXT NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "source TEXT NOT NULL, " +
                    "numberFingerprint TEXT)"
            )
            repeat(ROW_COUNT) { index ->
                val id = index + 1
                database.execSQL(
                    "INSERT INTO call_filter_decisions " +
                        "(id, occurredAtMs, action, reason, source, numberFingerprint) " +
                        "VALUES($id, ${1_700_000_000_000L + id}, 'ALLOW', 'synthetic', 'TEST', 'fp-$id')"
                )
            }
            database.version = 1
        }

        val rows = runBlocking { CallFilterLogStore.get(context).history() }
        assertEquals(ROW_COUNT, rows.size)
        assertEquals("fp-1", rows.last().numberFingerprint)
        assertEquals("fp-$ROW_COUNT", rows.first().numberFingerprint)
    }

    private companion object {
        const val DATABASE_NAME = "sentinel-local.db"
        const val IDENTITY = SentinelLocalDatabasePreservation.IDENTITY_HASH
        const val ROW_COUNT = SentinelLocalDatabasePreservation.V25_DECISION_ROWS
    }
}

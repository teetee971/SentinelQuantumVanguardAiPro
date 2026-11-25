package com.sentinel.quantum.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Read-only gate for the instrumentation process. It never deletes, checkpoints,
 * or updates the database. A failure to read an existing file is a refusal.
 */
internal object InstalledDataGuard {
    fun assertSafeToRun(context: Context) {
        val file = context.getDatabasePath("sentinel-local.db")
        val present = file.isFile
        val synthetic = if (present) syntheticFixture(file) else null
        check(
            SentinelLocalDatabasePreservation.instrumentationMayStart(
                packageName = context.packageName,
                databaseFilePresent = present,
                syntheticFixture = synthetic
            )
        ) { "REFUSING_TO_TOUCH_INSTALLED_V25_PACKAGE ${context.packageName}" }
    }

    private fun syntheticFixture(file: java.io.File): Boolean? = runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val identity = database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null
            ).use { cursor -> if (!cursor.moveToFirst()) null else cursor.getString(0) }
            if (identity != SentinelLocalDatabasePreservation.IDENTITY_HASH) return@use false
            if (database.version != SentinelLocalDatabasePreservation.SCHEMA_VERSION) return@use false
            database.rawQuery(
                "SELECT COUNT(*), " +
                    "SUM(CASE WHEN source = 'TEST' AND reason = 'synthetic' AND action = 'ALLOW' " +
                    "THEN 1 ELSE 0 END) FROM call_filter_decisions",
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val rows = cursor.getInt(0)
                val fixture = if (cursor.isNull(1)) 0 else cursor.getInt(1)
                rows > 0 && fixture == rows
            }
        }
    }.getOrNull()
}

package com.sentinel.quantum.security

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V25DataPreservationTest {
    @Test fun twentyNineDecisionsStayWhenRoomIdentityMatches() {
        val rows = (1..SentinelLocalDatabasePreservation.V25_DECISION_ROWS).map { id ->
            SentinelLocalDatabasePreservation.DecisionRow(
                id = id.toLong(),
                occurredAtMs = 1_700_000_000_000L + id,
                action = "ALLOW"
            )
        }
        val opened = SentinelLocalDatabasePreservation.open(
            installedIdentity = SentinelLocalDatabasePreservation.IDENTITY_HASH,
            installedVersion = SentinelLocalDatabasePreservation.SCHEMA_VERSION,
            rows = rows
        )
        assertEquals(SentinelLocalDatabasePreservation.OpenStatus.PRESERVED, opened.status)
        assertEquals(29, opened.rows.size)
        assertEquals(rows, opened.rows)
    }

    @Test fun identityMismatchDoesNotDestroyTheTwentyNineRows() {
        val rows = (1..29).map {
            SentinelLocalDatabasePreservation.DecisionRow(it.toLong(), it.toLong(), "BLOCK")
        }
        val opened = SentinelLocalDatabasePreservation.open(
            installedIdentity = "00000000000000000000000000000000",
            installedVersion = 1,
            rows = rows,
            destructiveFallbackRequested = true
        )
        assertEquals(
            SentinelLocalDatabasePreservation.OpenStatus.REFUSED_WITHOUT_DESTRUCTION,
            opened.status
        )
        assertEquals(rows, opened.rows)
    }

    @Test fun destructiveFallbackIsRejectedBeforeOpen() {
        try {
            SentinelLocalDatabasePreservation.assertNonDestructive(true)
            throw AssertionError("destructive fallback was accepted")
        } catch (failure: IllegalStateException) {
            assertEquals("DESTRUCTIVE_MIGRATION_FORBIDDEN", failure.message)
        }
    }

    @Test fun bundledSchemaFileCarriesTheV25Identity() {
        val schema = JSONObject(File("schemas/com.sentinel.quantum.security.SentinelLocalDatabase/1.json").readText())
        val database = schema.getJSONObject("database")
        assertEquals(1, database.getInt("version"))
        assertEquals(
            SentinelLocalDatabasePreservation.IDENTITY_HASH,
            database.getString("identityHash")
        )
        assertEquals(1, database.getJSONArray("entities").length())
        assertEquals(
            "call_filter_decisions",
            database.getJSONArray("entities").getJSONObject(0).getString("tableName")
        )
    }

    @Test fun roomBuilderSourceDoesNotRequestDestructiveMigration() {
        val source = File("src/main/java/com/sentinel/quantum/security/CallFilterLogStore.kt").readText()
        assertFalse(source.contains("fallbackToDestructiveMigration"))
        assertFalse(source.contains("deleteDatabase"))
        assertFalse(source.contains("assertNonDestructive(false)"))
        assertTrue(source.contains("RoomMasterIdentity.read"))
        assertTrue(source.contains("SQLiteDatabase.OPEN_READONLY"))
        assertTrue(source.contains("SentinelLocalDatabasePreservation.allowOpen("))
        assertTrue(source.contains("assertNonDestructive(!allowed)"))
    }

    @Test fun absentRoomFileMayBeCreated() {
        assertTrue(
            SentinelLocalDatabasePreservation.allowOpen(
                filePresent = false,
                identityHash = null,
                userVersion = -1
            )
        )
    }

    @Test fun matchingV25IdentityMayOpen() {
        assertTrue(
            SentinelLocalDatabasePreservation.allowOpen(
                filePresent = true,
                identityHash = SentinelLocalDatabasePreservation.IDENTITY_HASH,
                userVersion = SentinelLocalDatabasePreservation.SCHEMA_VERSION
            )
        )
    }

    @Test fun unreadableExistingFileIsRefused() {
        assertFalse(
            SentinelLocalDatabasePreservation.allowOpen(
                filePresent = true,
                identityHash = null,
                userVersion = -1
            )
        )
    }

    @Test fun mismatchedIdentityIsRefusedWithoutADestructiveFlag() {
        assertFalse(
            SentinelLocalDatabasePreservation.allowOpen(
                filePresent = true,
                identityHash = "00000000000000000000000000000000",
                userVersion = 1,
                destructiveFallbackRequested = false
            )
        )
    }

    @Test fun reputationPurgeRemovesCacheEntriesOnly() {
        val selected = CommunicationReputationPurge.keysToRemove(
            setOf(
                "h1:abc",
                "theme_mode",
                "blocked_prefixes",
                "hmac_secret_v1"
            )
        )
        assertEquals(setOf("h1:abc"), selected)
        val storage = File(
            "src/main/java/com/sentinel/quantum/security/SharedPreferencesCommunicationReputationStorage.kt"
        ).readText()
        assertTrue(storage.contains("STORAGE_KEY_PREFIX = \"${CommunicationReputationPurge.ENTRY_PREFIX}\""))
    }

    @Test fun settingsPurgeDoesNotClearTheWholePreferenceFile() {
        val source = File("src/main/java/com/sentinel/quantum/data/SettingsStore.kt").readText()
        assertFalse(source.contains(".clear()"))
        assertTrue(source.contains("keysToRemove"))
    }
}

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

    @Test fun historyScreenCatchesARefusedRoomOpen() {
        val source = File("src/main/java/com/sentinel/quantum/ui/screens/CallFilterHistoryScreen.kt").readText()
        assertTrue(source.contains("runCatching { CallFilterLogStore.get(context) }.getOrNull()"))
        assertFalse(source.contains("remember(context) { CallFilterLogStore.get(context) }"))
        assertTrue(source.contains("R.string.call_history_preserved_closed"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("call_history_preserved_closed"))
        assertTrue(strings.contains("laissé intact"))
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

    @Test fun commercialPackageIsNeverInstrumentable() {
        val installed = SentinelLocalDatabasePreservation.INSTALLED_PACKAGE
        val recette = SentinelLocalDatabasePreservation.VALIDATION_PACKAGE
        assertFalse(SentinelLocalDatabasePreservation.instrumentationMayStart(
            installed, databaseFilePresent = false
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationMayStart(
            installed, databaseFilePresent = true, syntheticFixture = true
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationMayStart(
            installed, databaseFilePresent = true, syntheticFixture = false
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationMayStart(
            installed, databaseFilePresent = true, databaseUnreadable = true
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationMayStart(
            installed, databaseFilePresent = true, syntheticFixture = true, walOrShmPresent = true
        ))
        assertTrue(SentinelLocalDatabasePreservation.instrumentationMayStart(
            recette, databaseFilePresent = true, syntheticFixture = false,
            databaseUnreadable = true, walOrShmPresent = true
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationMayStart(
            "$recette.test", databaseFilePresent = false
        ))
        assertTrue(SentinelLocalDatabasePreservation.instrumentationPackagesAreSeparated(
            recette, "$recette.test"
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationPackagesAreSeparated(
            installed, "$installed.test"
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationPackagesAreSeparated(
            recette, recette
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationPackagesAreSeparated(
            recette, installed
        ))
        assertFalse(SentinelLocalDatabasePreservation.instrumentationPackagesAreSeparated(
            "com.sentinel.quantum.aivalidation.c11",
            "com.sentinel.quantum.aivalidation.c11.test"
        ))
        val gradle = File("build.gradle").readText()
        assertTrue(gradle.contains("applicationId \"com.sentinel.quantum\""))
        assertTrue(gradle.contains("applicationIdSuffix '.v1validation'"))
        assertFalse(gradle.contains("testApplicationId"))
        assertTrue(gradle.contains("com.sentinel.quantum.security.SentinelPreservationInstrumentationRunner"))
        val properties = File("../gradle.properties").readText()
        assertFalse(properties.contains("testInstrumentationRunner"))
        val guard = File("src/androidTest/java/com/sentinel/quantum/security/InstalledDataGuard.kt").readText()
        assertTrue(guard.contains("REFUSING_TO_TOUCH_INSTALLED_V25_PACKAGE"))
        assertTrue(guard.contains("instrumentationPackagesAreSeparated"))
        assertFalse(guard.contains("SQLiteDatabase"))
        assertFalse(guard.contains("getDatabasePath"))
        assertFalse(guard.contains("deleteDatabase"))
    }

    @Test fun instrumentationClassesGateBeforeAnyDestructiveCall() {
        val destructive = listOf(
            "deleteDatabase(",
            ".clear().commit()",
            "deleteRecursively(",
            "deleteEntry(",
            "FileOutputStream("
        )
        val failures = mutableListOf<String>()
        File("src/androidTest/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val source = file.readText()
            if (!source.contains("@Test")) return@forEach
            val call = source.indexOf("InstalledDataGuard.assertSafeToRun").let { if (it < 0) Int.MAX_VALUE else it }
            val rule = source.indexOf("RefuseInstalledPackageRule()").let { if (it < 0) Int.MAX_VALUE else it }
            val gate = minOf(call, rule)
            if (gate == Int.MAX_VALUE) {
                failures += "${file.name} has no package gate"
                return@forEach
            }
            for (token in destructive) {
                val at = source.indexOf(token)
                if (at >= 0 && at < gate) failures += "${file.name} $token precedes the package gate"
            }
        }
        assertEquals(emptyList<String>(), failures)
        val room = File(
            "src/androidTest/java/com/sentinel/quantum/security/V25RoomIdentityUpgradeInstrumentationTest.kt"
        ).readText()
        val open = room.indexOf("SQLiteDatabase.openDatabase")
        val deletion = room.indexOf("deleteDatabase(")
        val gate = room.indexOf("InstalledDataGuard.assertSafeToRun")
        assertTrue(gate >= 0 && deletion > gate && open > gate)
        assertFalse(room.contains("fallbackToDestructiveMigration"))
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

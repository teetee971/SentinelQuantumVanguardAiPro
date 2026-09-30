package com.sentinel.quantum.data

import com.sentinel.quantum.security.ProtectionMode
import com.sentinel.quantum.security.FamilySafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelPreferencesBackupTest {
    private val snapshot = SentinelPreferencesBackup.Snapshot(
        themeMode = ThemeMode.DARK,
        protectionMode = ProtectionMode.LOCAL_ONLY,
        familySafetyProfile = FamilySafetyPolicy.Profile.ASSISTED,
        callerReputationEnrichmentEnabled = false,
        osintRefreshIntervalHours = 12,
        osintNotificationsEnabled = true,
        smsNotificationPreviewEnabled = false,
        blockedPrefixes = listOf("+590", "+33", "+590")
    )

    @Test fun roundTripPreservesOnlyRestoreSafePreferences() {
        val decoded = SentinelPreferencesBackup.decode(
            SentinelPreferencesBackup.encode(snapshot)
        )
        assertEquals(snapshot.themeMode, decoded?.themeMode)
        assertEquals(snapshot.protectionMode, decoded?.protectionMode)
        assertEquals(FamilySafetyPolicy.Profile.ASSISTED, decoded?.familySafetyProfile)
        assertEquals(12, decoded?.osintRefreshIntervalHours)
        assertEquals(listOf("+590", "+33"), decoded?.blockedPrefixes)
    }

    @Test fun unsupportedSchemaFailsClosed() {
        val raw = SentinelPreferencesBackup.encode(snapshot)
            .replace("\"schema_version\": 1", "\"schema_version\": 99")
        assertNull(SentinelPreferencesBackup.decode(raw))
    }

    @Test fun legacySchemaOneWithoutAssistedProfileDefaultsToStandard() {
        val raw = """{
          "schema_version":1,
          "theme_mode":"SYSTEM",
          "protection_mode":"LOCAL_ONLY",
          "caller_reputation_enrichment_enabled":false,
          "osint_refresh_interval_hours":0,
          "osint_notifications_enabled":true,
          "sms_notification_preview_enabled":false,
          "blocked_prefixes":[]
        }"""
        assertEquals(
            FamilySafetyPolicy.Profile.STANDARD,
            SentinelPreferencesBackup.decode(raw)?.familySafetyProfile
        )
    }

    @Test fun invalidPrefixFailsWholeRestore() {
        val raw = """{
          "schema_version":1,
          "theme_mode":"SYSTEM",
          "protection_mode":"LOCAL_ONLY",
          "osint_refresh_interval_hours":0,
          "blocked_prefixes":["+590","bad"]
        }"""
        assertNull(SentinelPreferencesBackup.decode(raw))
    }

    @Test fun backupNeverContainsSensitiveCollections() {
        val raw = SentinelPreferencesBackup.encode(snapshot).lowercase()
        assertTrue(!raw.contains("contact"))
        assertTrue(!raw.contains("sms_body"))
        assertTrue(!raw.contains("call_history"))
        assertTrue(!raw.contains("blocked_number_hashes"))
        assertTrue(!raw.contains("token"))
    }
}

package com.sentinel.quantum.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintRssConfigTest {
    @Test
    fun aMissingChoiceKeepsEveryRssFeedAndStillReadsTheJsonSources() {
        val rss = OsintRssConfig.rssSources().map { it.name }.toSet()
        assertEquals(rss, OsintRssConfig.selection(null).map { it.name }.toSet())
        assertFalse(rss.contains(OsintSource.NVD_RECENT.name))
        assertFalse(rss.contains(OsintSource.CISA_KEV.name))
        val fetched = OsintRssConfig.fetchSources(null).map { it.name }
        assertEquals(OsintSource.entries.map { it.name }, fetched)
    }

    @Test
    fun aStoredChoiceIgnoresJsonSourcesAndUnknownNames() {
        val stored = setOf(OsintSource.CERT_FR_ALERTS.name, OsintSource.NVD_RECENT.name, "NOT_A_FEED")
        assertEquals(setOf(OsintSource.CERT_FR_ALERTS), OsintRssConfig.selection(stored))
        val fetched = OsintRssConfig.fetchSources(stored)
        assertTrue(fetched.contains(OsintSource.CERT_FR_ALERTS))
        assertFalse(fetched.contains(OsintSource.CERT_FR_ADVISORIES))
        assertTrue(fetched.contains(OsintSource.NVD_RECENT))
        assertTrue(fetched.contains(OsintSource.CISA_KEV))
        assertEquals(emptySet<OsintSource>(), OsintRssConfig.selection(emptySet()))
        assertFalse(OsintRssConfig.fetchSources(emptySet()).any { it.kind == OsintSourceKind.RSS })
    }

    @Test
    fun anOldBackupWithoutRssChoiceStaysUnspecified() {
        val raw = """{
          "schema_version":1,
          "theme_mode":"SYSTEM",
          "protection_mode":"LOCAL_ONLY",
          "osint_refresh_interval_hours":0,
          "blocked_prefixes":[]
        }"""
        assertNull(SentinelPreferencesBackup.decode(raw)?.osintRssSources)
    }

    @Test
    fun aBackupRoundTripKeepsOnlyRssNames() {
        val encoded = SentinelPreferencesBackup.encode(
            SentinelPreferencesBackup.Snapshot(
                themeMode = ThemeMode.DARK,
                protectionMode = com.sentinel.quantum.security.ProtectionMode.LOCAL_ONLY,
                callerReputationEnrichmentEnabled = false,
                osintRefreshIntervalHours = 0,
                osintNotificationsEnabled = true,
                osintRssSources = setOf(
                    OsintSource.NCSC_UK_REPORTS.name,
                    OsintSource.CISA_KEV.name
                ),
                smsNotificationPreviewEnabled = false,
                blockedPrefixes = emptyList()
            )
        )
        val names = SentinelPreferencesBackup.decode(encoded)?.osintRssSources
        assertEquals(setOf(OsintSource.NCSC_UK_REPORTS.name), names)
        assertFalse(JSONObject(encoded).getJSONArray("osint_rss_sources").toString().contains("CISA"))
    }
}

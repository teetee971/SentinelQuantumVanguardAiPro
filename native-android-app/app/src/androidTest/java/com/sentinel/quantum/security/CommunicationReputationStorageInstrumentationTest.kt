package com.sentinel.quantum.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.data.SettingsStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunicationReputationStorageInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun enableRemoteReputationForPersistenceTests() {
        SettingsStore(context).apply {
            protectionMode = ProtectionMode.ENHANCED
            callerReputationEnrichmentEnabled = true
        }
    }

    @After
    fun cleanup() {
        SettingsStore(context).apply {
            callerReputationEnrichmentEnabled = false
            protectionMode = ProtectionMode.LOCAL_ONLY
        }
        context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun reputationSurvivesStorageRecreationWithoutPersistingPhoneNumber() {
        val raw = "06 12 34 56 78"
        val firstCache = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        )
        val entry = CommunicationReputationCache.Entry(
            riskScore = 91,
            categories = listOf("SPOOFING"),
            signals = 5,
            observedAtMs = 1_000L,
            ttlMs = 60_000L
        )

        assertTrue(firstCache.put(raw, entry, nowMs = 1_100L))

        val preferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val persisted = preferences.all.entries.joinToString("\n") { "${it.key}=${it.value}" }
        assertFalse(persisted.contains(raw))
        assertFalse(persisted.contains("0612345678"))
        assertFalse(persisted.contains("+33612345678"))

        val restored = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        ).get("+33612345678", nowMs = 1_100L)
        assertNotNull(restored)
        assertTrue(restored == entry)
    }

    @Test
    fun persistedStorageKeyIsSecretDerivedNotBarePhoneDigest() {
        val number = "+33612345678"
        val cache = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        )
        val entry = CommunicationReputationCache.Entry(
            riskScore = 80,
            categories = listOf("ROBOCALL"),
            signals = 2,
            observedAtMs = 1_000L,
            ttlMs = 60_000L
        )

        assertTrue(cache.put(number, entry, nowMs = 1_100L))

        val bareDigestKey = CommunicationReputationCache.cacheKeyFor(number)!!
        val preferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(preferences.all.keys.isNotEmpty())
        assertFalse(preferences.all.keys.contains(bareDigestKey))
        assertTrue(preferences.all.keys.all { it.startsWith("h1:") })
    }

    @Test
    fun secretIsSeparatedFromReputationEntries() {
        val cache = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        )
        assertTrue(
            cache.put(
                "+33612345678",
                CommunicationReputationCache.Entry(
                    riskScore = 65,
                    categories = emptyList(),
                    signals = 1,
                    observedAtMs = 1_000L,
                    ttlMs = 60_000L
                ),
                nowMs = 1_100L
            )
        )

        val reputationPreferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val keyPreferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(reputationPreferences.all.keys.all { it.startsWith("h1:") })
        assertTrue(keyPreferences.all.isNotEmpty())
        assertFalse(keyPreferences.all.keys.any { it.startsWith("h1:") })
    }

    @Test
    fun revokedConsentPreventsNewReputationPersistence() {
        val settings = SettingsStore(context)
        settings.callerReputationEnrichmentEnabled = false
        val cache = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        )
        val entry = CommunicationReputationCache.Entry(
            riskScore = 92,
            categories = listOf("SPOOFING"),
            signals = 7,
            observedAtMs = 1_000L,
            ttlMs = 60_000L
        )

        assertFalse(cache.put("+33612345678", entry, nowMs = 1_100L))

        val preferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun revokingConsentPurgesPersistedReputationButKeepsInstallSecret() {
        val cache = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        )
        assertTrue(
            cache.put(
                "+33612345678",
                CommunicationReputationCache.Entry(
                    riskScore = 88,
                    categories = listOf("SCAM"),
                    signals = 4,
                    observedAtMs = 1_000L,
                    ttlMs = 60_000L
                ),
                nowMs = 1_100L
            )
        )

        val reputationPreferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val keyPreferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(reputationPreferences.all.isNotEmpty())
        assertTrue(keyPreferences.all.isNotEmpty())

        // Revocation must minimize retained remote-derived metadata, not just hide it from readers.
        SettingsStore(context).callerReputationEnrichmentEnabled = false

        assertTrue(reputationPreferences.all.isEmpty())
        assertTrue(keyPreferences.all.isNotEmpty())
    }

    @Test
    fun leavingEnhancedModePurgesPersistedReputationButKeepsInstallSecret() {
        val cache = CommunicationReputationCache(
            SharedPreferencesCommunicationReputationStorage(context)
        )
        assertTrue(
            cache.put(
                "+33612345678",
                CommunicationReputationCache.Entry(
                    riskScore = 86,
                    categories = listOf("SCAM"),
                    signals = 3,
                    observedAtMs = 1_000L,
                    ttlMs = 60_000L
                ),
                nowMs = 1_100L
            )
        )

        val reputationPreferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val keyPreferences = context.getSharedPreferences(
            SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(reputationPreferences.all.isNotEmpty())
        assertTrue(keyPreferences.all.isNotEmpty())

        SettingsStore(context).protectionMode = ProtectionMode.LOCAL_ONLY

        assertTrue(reputationPreferences.all.isEmpty())
        assertTrue(keyPreferences.all.isNotEmpty())
    }

    @Test
    fun removeDeletesThePersistedOpaqueEntry() {
        val storage = SharedPreferencesCommunicationReputationStorage(context)
        val cache = CommunicationReputationCache(storage)
        val number = "+33612345678"
        val entry = CommunicationReputationCache.Entry(
            riskScore = 50,
            categories = emptyList(),
            signals = 0,
            observedAtMs = 1_000L,
            ttlMs = 60_000L
        )
        assertTrue(cache.put(number, entry, nowMs = 1_100L))
        val key = CommunicationReputationCache.cacheKeyFor(number)!!
        assertNotNull(storage.get(key))

        storage.remove(key)

        assertNull(storage.get(key))
    }
}

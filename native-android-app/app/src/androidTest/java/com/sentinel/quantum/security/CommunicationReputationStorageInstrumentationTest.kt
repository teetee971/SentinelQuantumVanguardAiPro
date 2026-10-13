package com.sentinel.quantum.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunicationReputationStorageInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @After
    fun cleanup() {
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

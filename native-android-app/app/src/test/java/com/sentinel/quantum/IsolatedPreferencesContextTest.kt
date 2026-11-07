package com.sentinel.quantum

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.CommunicationReputationCache
import com.sentinel.quantum.security.ProtectionMode
import com.sentinel.quantum.security.SharedPreferencesCommunicationReputationStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class IsolatedPreferencesContextTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val names = listOf("sentinel_settings", SharedPreferencesCommunicationReputationStorage.PREFS_NAME,
        SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME)
    @Test fun applicationContextCannotEscapeTheNamespace() {
        IsolatedPreferencesContext(app).use { assertSame(it,it.applicationContext) }
    }
    @Test fun actualConsentRevocationAndHmacWritesLeaveProductionPreferencesUntouched() {
        names.forEach { assertTrue(app.getSharedPreferences(it,0).edit().putString("production-sentinel","keep").commit()) }
        val before=names.associateWith{app.getSharedPreferences(it,0).all.toMap()}
        IsolatedPreferencesContext(app).use { isolated ->
            SettingsStore(isolated).apply {protectionMode=ProtectionMode.ENHANCED;callerReputationEnrichmentEnabled=true}
            val cache=CommunicationReputationCache(SharedPreferencesCommunicationReputationStorage(isolated))
            assertTrue(cache.put("+33612345678",CommunicationReputationCache.Entry(80,listOf("SCAM"),2,1000,60000),1100))
            assertFalse(isolated.getSharedPreferences(SharedPreferencesCommunicationReputationStorage.PREFS_NAME,0).all.isEmpty())
            SettingsStore(isolated).callerReputationEnrichmentEnabled=false
            assertTrue(isolated.getSharedPreferences(SharedPreferencesCommunicationReputationStorage.PREFS_NAME,0).all.isEmpty())
            assertFalse(isolated.getSharedPreferences(SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,0).all.isEmpty())
            assertEquals(before,names.associateWith{app.getSharedPreferences(it,0).all.toMap()})
        }
        assertEquals(before,names.associateWith{app.getSharedPreferences(it,0).all.toMap()})
    }
    @Test fun separateTestContextsNeverShareConsentOrSecrets() {
        IsolatedPreferencesContext(app).use { one -> IsolatedPreferencesContext(app).use { two ->
            SettingsStore(one).callerReputationEnrichmentEnabled=true
            assertFalse(SettingsStore(two).callerReputationEnrichmentEnabled)
            one.getSharedPreferences(SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,0).edit().putString("secret","one").commit()
            assertTrue(two.getSharedPreferences(SharedPreferencesCommunicationReputationStorage.KEY_PREFS_NAME,0).all.isEmpty())
        } }
    }
    @Test fun unexpectedPreferenceNameFailsInsteadOfReachingProduction() {
        IsolatedPreferencesContext(app).use { assertThrows(SecurityException::class.java){it.getSharedPreferences("unexpected",0)} }
    }
    @Test fun closeClearsOnlyItsOwnNamespaceAndPreventsReuse() {
        val opened=linkedSetOf<String>()
        val tracking=object:ContextWrapper(app){override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
            opened+=name;return super.getSharedPreferences(name,mode)
        }}
        val production=app.getSharedPreferences("sentinel_settings",0);production.edit().putString("unchanged","yes").commit()
        val isolated=IsolatedPreferencesContext(tracking)
        isolated.getSharedPreferences("sentinel_settings",0).edit().putString("test","value").commit()
        assertTrue(opened.isNotEmpty());assertTrue(opened.all{it.startsWith("sentinel_test_")})
        isolated.close()
        assertTrue(opened.all{app.getSharedPreferences(it,0).all.isEmpty()})
        assertEquals("yes",production.getString("unchanged",null))
        assertThrows(IllegalStateException::class.java){isolated.getSharedPreferences("sentinel_settings",0)}
    }
}

package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Phase fixture for the external ROLE_SMS process-death qualification.
 *
 * Removing ROLE_SMS can kill the target process, so no single instrumentation method may own both
 * sides of that transition. The host invokes these phases in separate fresh instrumentation runs.
 */
@RunWith(AndroidJUnit4::class)
class SmsRoleLossRecoveryInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val fixture = context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE)

    @Test
    fun prepareProviderReadyFixture() {
        requireExternalPhase(PHASE_PREPARE)
        assumeTrue("ROLE_SMS process-death qualification starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertTrue("Sentinel must hold ROLE_SMS while preparing the fixture", SentinelSmsSender(context).holdsSmsRole())
        assertFalse("A stale ROLE_SMS fixture must not be reused", fixture.contains(KEY_TOKEN))

        val subscriptionId = activeSubscriptionId()
        val token = UUID.randomUUID().toString()
        val createdAtMs = System.currentTimeMillis()
        val journal = SmsPreSubmitJournal(context)
        assertTrue("PREPARING journal record must be durable", journal.begin(token, subscriptionId, createdAtMs))

        val providerMessageId = requireNotNull(
            SmsPreSubmitProvider.insertOutgoingOutbox(
                context = context,
                address = "+15550125",
                body = "SentinelRoleLossProcessDeathProbe",
                subscriptionId = subscriptionId,
                timestampMs = createdAtMs
            )
        )
        assertTrue(
            "Provider identity must be durably correlated before role loss",
            journal.recordProvider(token, providerMessageId)
        )
        assertEquals(SmsPreSubmitJournal.Phase.PROVIDER_READY, journal.read(token)?.phase)
        assertProviderState(providerMessageId, Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.STATUS_PENDING)

        assertTrue(
            "Fixture identity must survive the process killed by ROLE_SMS loss",
            fixture.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_PROVIDER_ID, providerMessageId)
                .commit()
        )
    }

    @Test
    fun roleLossPreservesProviderReadyFixture() {
        requireExternalPhase(PHASE_ROLE_ABSENT)
        assumeTrue("ROLE_SMS process-death qualification starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertFalse("ROLE_SMS must be absent in the post-kill phase", SentinelSmsSender(context).holdsSmsRole())

        val token = fixture.getString(KEY_TOKEN, null)
        assertNotNull("Process-death fixture token must survive role loss", token)
        val providerMessageId = fixture.getLong(KEY_PROVIDER_ID, -1L)
        assertTrue("Process-death fixture provider id must survive role loss", providerMessageId > 0L)
        val record = SmsPreSubmitJournal(context).read(requireNotNull(token))
        assertNotNull("Pre-transport journal must survive role-driven process death", record)
        assertEquals(providerMessageId, record!!.providerMessageId)
        assertEquals(SmsPreSubmitJournal.Phase.PROVIDER_READY, record.phase)
    }

    @Test
    fun recoverAfterRoleRestoration() {
        requireExternalPhase(PHASE_RECOVER)
        assumeTrue("ROLE_SMS process-death qualification starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertTrue("ROLE_SMS must be restored before recovery", SentinelSmsSender(context).holdsSmsRole())

        val token = fixture.getString(KEY_TOKEN, null)
        assertNotNull("Process-death fixture token must survive until recovery", token)
        val providerMessageId = fixture.getLong(KEY_PROVIDER_ID, -1L)
        assertTrue("Process-death fixture provider id must survive until recovery", providerMessageId > 0L)

        SmsPreSubmitRecoveryWorker.scheduleStartupRecovery(context)
        repeat(100) {
            val state = providerState(providerMessageId)
            val journalCleared = SmsPreSubmitJournal(context).read(requireNotNull(token)) == null
            if (
                state?.first == Telephony.Sms.MESSAGE_TYPE_FAILED &&
                state.second == Telephony.Sms.STATUS_FAILED &&
                journalCleared
            ) {
                assertTrue("Recovered fixture metadata must be clearable", fixture.edit().clear().commit())
                return
            }
            Thread.sleep(100)
        }

        assertProviderState(providerMessageId, Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_FAILED)
        assertEquals(null, SmsPreSubmitJournal(context).read(requireNotNull(token)))
    }

    private fun requireExternalPhase(expected: String) {
        val actual = instrumentation.arguments.getString(ARG_PHASE)
        assumeTrue("External ROLE_SMS phase $expected is not requested", actual == expected)
    }

    private fun activeSubscriptionId(): Int {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList
            .orEmpty()
            .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        assertTrue("Emulator must expose at least one active SMS subscription", subscriptions.isNotEmpty())
        return subscriptions.first().subscriptionId
    }

    private fun assertProviderState(providerMessageId: Long, expectedType: Int, expectedStatus: Int) {
        val state = providerState(providerMessageId)
        assertNotNull("SMS provider fixture row must be readable", state)
        assertEquals(expectedType, state!!.first)
        assertEquals(expectedStatus, state.second)
    }

    private fun providerState(providerMessageId: Long): Pair<Int, Int>? =
        context.contentResolver.query(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerMessageId),
            arrayOf(Telephony.Sms.TYPE, Telephony.Sms.STATUS),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getInt(0) to cursor.getInt(1)
        }

    companion object {
        private const val ARG_PHASE = "sentinel_role_phase"
        private const val PHASE_PREPARE = "prepare"
        private const val PHASE_ROLE_ABSENT = "role_absent"
        private const val PHASE_RECOVER = "recover"
        private const val FIXTURE_PREFS = "sentinel_sms_role_loss_fixture_v1"
        private const val KEY_TOKEN = "token"
        private const val KEY_PROVIDER_ID = "provider_id"
    }
}

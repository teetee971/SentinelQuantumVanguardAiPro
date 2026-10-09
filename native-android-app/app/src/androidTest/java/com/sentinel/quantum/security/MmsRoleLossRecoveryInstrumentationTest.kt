package com.sentinel.quantum.security

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** External multi-process proof for MMS READY recovery across ROLE_SMS loss. */
@RunWith(AndroidJUnit4::class)
class MmsRoleLossRecoveryInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val fixture = context.getSharedPreferences(FIXTURE_PREFS, Context.MODE_PRIVATE)

    @Test
    fun prepareReadyAndAwaitRoleLoss() {
        requireExternalPhase(PHASE_PREPARE)
        assumeTrue("MMS ROLE_SMS process-death qualification starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertTrue("Sentinel must hold ROLE_SMS while preparing MMS fixture", holdsSmsRole())
        assertFalse("A stale MMS role-loss fixture must not be reused", fixture.contains(KEY_TOKEN))

        val subscriptionId = activeSubscriptionId()
        val staged = MmsSendPduStager.stage(
            context,
            "SentinelMmsRoleLossPduProbe".toByteArray(Charsets.UTF_8)
        ) as? MmsSendPduStager.Result.Staged
        assertNotNull("MMS PDU fixture must stage before provider mutation", staged)
        staged!!

        val transactionId = "roleloss-" + staged.token.replace("-", "").take(24)
        val persisted = MmsConversationStore(context).persistOutgoingOutbox(
            token = staged.token,
            transactionId = transactionId,
            destination = "+15550126",
            text = "SentinelMmsRoleLossProcessDeathProbe",
            attachments = emptyList(),
            subscriptionId = subscriptionId
        )
        assertTrue("MMS provider fixture must reach READY", persisted is MmsConversationStore.PersistResult.Ready)
        val providerMessageId = (persisted as MmsConversationStore.PersistResult.Ready).providerMessageId

        val record = MmsProviderJournal(context).read(staged.token)
        assertNotNull("MMS READY journal must exist before role loss", record)
        assertEquals(providerMessageId, record!!.providerMessageId)
        assertEquals(MmsProviderJournal.Phase.READY, record.phase)
        assertProviderOutbox(providerMessageId)
        assertTrue("Staged MMS PDU must exist before role loss", stagedFile(staged.fileName).isFile)

        assertTrue(
            "MMS fixture identity must survive role-driven process death",
            fixture.edit()
                .putString(KEY_TOKEN, staged.token)
                .putString(KEY_FILE_NAME, staged.fileName)
                .putLong(KEY_PROVIDER_ID, providerMessageId)
                .commit()
        )

        Log.i(READY_LOG_TAG, READY_LOG_MARKER)
        repeat(600) {
            if (!holdsSmsRole()) {
                fail("MMS instrumentation survived ROLE_SMS loss; host process-death proof is invalid")
            }
            Thread.sleep(100)
        }
        fail("Host did not remove ROLE_SMS while the prepared MMS fixture process was alive")
    }

    @Test
    fun roleLossPreservesReadyFixture() {
        requireExternalPhase(PHASE_ROLE_ABSENT)
        assumeTrue("MMS ROLE_SMS process-death qualification starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertFalse("ROLE_SMS must be absent in the MMS post-kill phase", holdsSmsRole())

        val token = fixture.getString(KEY_TOKEN, null)
        val fileName = fixture.getString(KEY_FILE_NAME, null)
        val providerMessageId = fixture.getLong(KEY_PROVIDER_ID, -1L)
        assertNotNull("MMS fixture token must survive role loss", token)
        assertNotNull("MMS staged filename must survive role loss", fileName)
        assertTrue("MMS provider id must survive role loss", providerMessageId > 0L)

        val record = MmsProviderJournal(context).read(requireNotNull(token))
        assertNotNull("MMS READY journal must survive role-driven process death", record)
        assertEquals(providerMessageId, record!!.providerMessageId)
        assertEquals(MmsProviderJournal.Phase.READY, record.phase)
        assertTrue("MMS staged PDU must survive until authorized recovery", stagedFile(requireNotNull(fileName)).isFile)
    }

    @Test
    fun recoverAfterRoleRestoration() {
        requireExternalPhase(PHASE_RECOVER)
        assumeTrue("MMS ROLE_SMS process-death qualification starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertTrue("ROLE_SMS must be restored before MMS recovery", holdsSmsRole())

        val token = fixture.getString(KEY_TOKEN, null)
        val fileName = fixture.getString(KEY_FILE_NAME, null)
        val providerMessageId = fixture.getLong(KEY_PROVIDER_ID, -1L)
        assertNotNull("MMS fixture token must survive until recovery", token)
        assertNotNull("MMS staged filename must survive until recovery", fileName)
        assertTrue("MMS provider id must survive until recovery", providerMessageId > 0L)

        assertTrue("MMS READY recovery must complete once ROLE_SMS is restored", MmsPreTransportRecovery.repairReadyRecords(context))
        assertEquals(null, MmsProviderJournal(context).read(requireNotNull(token)))
        assertFalse("Recovered MMS provider row must be absent", providerExists(providerMessageId))
        assertFalse("Recovered MMS staged PDU must be absent", stagedFile(requireNotNull(fileName)).exists())
        assertTrue("Recovered MMS fixture metadata must be clearable", fixture.edit().clear().commit())
    }

    private fun requireExternalPhase(expected: String) {
        val actual = InstrumentationRegistry.getArguments().getString(ARG_PHASE)
        assumeTrue("External MMS ROLE_SMS phase $expected is not requested", actual == expected)
    }

    private fun activeSubscriptionId(): Int {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList
            .orEmpty()
            .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        assertTrue("Emulator must expose at least one active MMS subscription", subscriptions.isNotEmpty())
        return subscriptions.first().subscriptionId
    }

    private fun holdsSmsRole(): Boolean =
        context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD

    private fun assertProviderOutbox(providerMessageId: Long) {
        val box = context.contentResolver.query(
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId),
            arrayOf(Telephony.Mms.MESSAGE_BOX),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
        assertEquals(Telephony.Mms.MESSAGE_BOX_OUTBOX, box)
    }

    private fun providerExists(providerMessageId: Long): Boolean = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId),
            arrayOf(Telephony.Mms._ID),
            null,
            null,
            null
        )?.use { it.moveToFirst() } ?: true
    }.getOrDefault(true)

    private fun stagedFile(fileName: String): File =
        File(File(context.cacheDir, "sentinel_mms_send"), fileName)

    companion object {
        private const val ARG_PHASE = "sentinel_mms_role_phase"
        private const val PHASE_PREPARE = "prepare"
        private const val PHASE_ROLE_ABSENT = "role_absent"
        private const val PHASE_RECOVER = "recover"
        private const val FIXTURE_PREFS = "sentinel_mms_role_loss_fixture_v1"
        private const val KEY_TOKEN = "token"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val READY_LOG_TAG = "SentinelRoleLoss"
        private const val READY_LOG_MARKER = "MMS_FIXTURE_READY"
    }
}

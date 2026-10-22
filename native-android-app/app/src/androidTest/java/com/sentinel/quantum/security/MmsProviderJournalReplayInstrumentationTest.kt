package com.sentinel.quantum.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MmsProviderJournalReplayInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val tokens = mutableListOf<String>()

    @After
    fun cleanup() {
        val journal = MmsProviderJournal(context)
        tokens.forEach(journal::remove)
        tokens.clear()
    }

    @Test
    fun replayCannotOverwriteUnresolvedRootOrReadyState() {
        val token = "e0a40cc2-44aa-4fbb-8eee-000000000001"
        tokens += token
        val journal = MmsProviderJournal(context)
        assertTrue(journal.begin(token, "tx-one", 100L))
        assertTrue(journal.recordRoot(token, 42L, 110L))
        assertFalse(MmsProviderJournal(context).begin(token, "tx-two", 120L))
        val record = MmsProviderJournal(context).read(token)
        assertNotNull(record)
        assertEquals("tx-one", record?.transactionId)
        assertEquals(42L, record?.providerMessageId)
        assertEquals(MmsProviderJournal.Phase.ROOT_INSERTED, record?.phase)
        assertTrue(journal.markReady(token, 42L, 130L))
        assertFalse(MmsProviderJournal(context).begin(token, "tx-three", 140L))
        assertEquals(MmsProviderJournal.Phase.READY, journal.read(token)?.phase)
    }

    @Test
    fun replayCannotDowngradeSubmittedOrUnknownTransportResult() {
        val token = "e0a40cc2-44aa-4fbb-8eee-000000000002"
        tokens += token
        val journal = MmsProviderJournal(context)
        assertTrue(journal.begin(token, "tx-original", 100L))
        assertTrue(journal.recordRoot(token, 43L, 110L))
        assertTrue(journal.markReady(token, 43L, 120L))
        assertTrue(journal.markSubmissionUnknown(token, 43L, 130L))
        assertFalse(MmsProviderJournal(context).begin(token, "tx-replay", 140L))
        assertEquals(MmsProviderJournal.Phase.SUBMISSION_UNKNOWN, journal.read(token)?.phase)
        assertEquals("tx-original", journal.read(token)?.transactionId)
    }

    @Test
    fun clearedTokenMayStartNewProjection() {
        val token = "e0a40cc2-44aa-4fbb-8eee-000000000003"
        tokens += token
        val journal = MmsProviderJournal(context)
        assertTrue(journal.begin(token, "tx-initial", 100L))
        assertTrue(journal.remove(token))
        assertTrue(MmsProviderJournal(context).begin(token, "tx-new", 200L))
        assertEquals("tx-new", journal.read(token)?.transactionId)
        assertEquals(MmsProviderJournal.Phase.BUILDING, journal.read(token)?.phase)
    }
}

package com.sentinel.quantum.security

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MmsDownloadRecoveryJournalInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val createdFiles = mutableListOf<String>()

    @After
    fun cleanup() {
        val journal = MmsDownloadRecoveryJournal(context)
        createdFiles.forEach(journal::remove)
        createdFiles.clear()
    }

    @Test
    fun recordSurvivesJournalRecreationAndCanBeRetired() {
        val fileName = "123e4567-e89b-12d3-a456-426614174000.pdu"
        createdFiles += fileName
        val subscriptionId = 1
        val requestedAtMs = 1_234_567L

        assertTrue(
            MmsDownloadRecoveryJournal(context).record(
                fileName = fileName,
                subscriptionId = subscriptionId,
                requestedAtMs = requestedAtMs
            )
        )

        val restored = MmsDownloadRecoveryJournal(context).read(fileName)
        requireNotNull(restored)
        assertEquals(fileName, restored.fileName)
        assertEquals(subscriptionId, restored.subscriptionId)
        assertEquals(requestedAtMs, restored.requestedAtMs)

        assertTrue(MmsDownloadRecoveryJournal(context).remove(fileName))
        assertNull(MmsDownloadRecoveryJournal(context).read(fileName))
    }

    @Test
    fun invalidFilenameAndSubscriptionAreRejectedFailClosed() {
        val journal = MmsDownloadRecoveryJournal(context)

        assertFalse(
            journal.record(
                fileName = "../escape.pdu",
                subscriptionId = 1,
                requestedAtMs = 1L
            )
        )
        assertFalse(
            journal.record(
                fileName = "123e4567-e89b-12d3-a456-426614174001.pdu",
                subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                requestedAtMs = 1L
            )
        )
        assertFalse(
            journal.record(
                fileName = "123e4567-e89b-12d3-a456-426614174002.pdu",
                subscriptionId = 1,
                requestedAtMs = -1L
            )
        )
    }
}

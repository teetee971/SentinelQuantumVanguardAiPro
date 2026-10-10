package com.sentinel.quantum.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A malformed journal record is not proof that the corresponding staged MMS PDU is orphaned.
 * Test with an isolated synthetic digest and restore preferences after execution.
 */
@RunWith(AndroidJUnit4::class)
class IncomingMmsWapIngressJournalInstrumentationTest {
    @Test
    fun corruptJournalEntryRemainsPresentAndBlocksUnsafeReplayOrEviction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(IncomingMmsWapIngressJournal.PREFS_NAME, Context.MODE_PRIVATE)
        val digest = "e".repeat(64)
        val freshDigest = "f".repeat(64)
        val key = IncomingMmsWapIngressJournal.KEY_PREFIX + digest
        val freshKey = IncomingMmsWapIngressJournal.KEY_PREFIX + freshDigest
        val previous = prefs.all[key]
        val previousFresh = prefs.all[freshKey]
        try {
            assertTrue(prefs.edit().putString(key, "{not-valid-json").commit())
            val journal = IncomingMmsWapIngressJournal(context)
            assertTrue(runCatching { journal.read(digest) }.exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { journal.all() }.exceptionOrNull() is IllegalStateException)
            assertFalse(journal.record(freshDigest, subscriptionId = -1, slotIndex = null))
            assertTrue("Corrupt metadata must not be deleted as a side effect", prefs.contains(key))
            assertFalse("No new entry should be recorded while journal is indeterminate", prefs.contains(freshKey))
        } finally {
            val editor = prefs.edit()
            if (previous is String) editor.putString(key, previous) else editor.remove(key)
            if (previousFresh is String) editor.putString(freshKey, previousFresh) else editor.remove(freshKey)
            assertTrue("Restore test journal keys", editor.commit())
        }
    }
}

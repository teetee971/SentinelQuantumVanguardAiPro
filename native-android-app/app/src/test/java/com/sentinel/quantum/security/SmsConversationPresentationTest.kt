package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class SmsConversationPresentationTest {
    private fun message(thread: Long, body: String) = SmsConversationStore.Message(1, "Fixture", body, 1000, 1, thread)
    private fun summary(thread: Long, address: String) = SmsConversationStore.ThreadSummary(thread, address, "Fixture", 1000, 1)

    @Test fun lateResultFromPreviousThreadCannotAppearInNewThread() {
        val loaded = SmsConversationSnapshot(11, listOf(message(11, "Private A")))
        assertTrue(loaded.messagesFor(12).isEmpty())
        assertEquals("Private A", loaded.messagesFor(11).single().body)
    }
    @Test fun closedConversationAndInvalidIdsCannotExposeLoadedRows() {
        val loaded = SmsConversationSnapshot(11, listOf(message(11, "Private A")))
        assertTrue(loaded.messagesFor(null).isEmpty())
        assertTrue(SmsConversationSnapshot(0, listOf(message(0, "Invalid"))).messagesFor(0).isEmpty())
        assertTrue(SmsConversationSnapshot(-1, listOf(message(-1, "Invalid"))).messagesFor(-1).isEmpty())
    }
    @Test fun mixedProviderRowsNeverCrossTheSelectedThreadBoundary() {
        val loaded = SmsConversationSnapshot(11, listOf(message(12, "Other"), message(11, "Selected")))
        assertEquals(listOf("Selected"), loaded.messagesFor(11).map { it.body })
    }
    @Test fun completedSnapshotDoesNotShareTheMutableProviderList() {
        val source = mutableListOf(message(11, "Selected"))
        val loaded = SmsConversationSnapshot(11, source)
        source.clear()
        assertEquals("Selected", loaded.messagesFor(11).single().body)
    }
    @Test fun replyRecipientComesOnlyFromTheMatchingCurrentSummary() {
        val summaries = listOf(summary(12, "Other recipient"), summary(11, "Selected recipient"))
        assertEquals("Selected recipient", SmsConversationRecipient.forThread(11, summaries))
        assertEquals("Other recipient", SmsConversationRecipient.forThread(12, summaries))
    }
    @Test fun absentSummaryOrSelectionDoesNotGuessAReplyRecipient() {
        val summaries = listOf(summary(12, "Other recipient"), summary(0, "Invalid"))
        assertEquals("", SmsConversationRecipient.forThread(11, summaries))
        assertEquals("", SmsConversationRecipient.forThread(null, summaries))
        assertEquals("", SmsConversationRecipient.forThread(0, summaries))
    }
}

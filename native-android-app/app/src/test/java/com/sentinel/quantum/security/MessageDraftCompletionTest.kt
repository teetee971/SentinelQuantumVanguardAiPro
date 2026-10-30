package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MessageDraftCompletionTest {
    private val sent = MessageTextDraft("Fixture A", "Sent text", false)
    @Test fun unchangedComposerTextIsClearedAfterAcceptance() {
        assertEquals("", MessageDraftCompletion.remainingText(sent, sent))
    }
    @Test fun editedTextSurvivesAcceptanceOfPreviousText() {
        assertEquals("Next text", MessageDraftCompletion.remainingText(sent, sent.copy(text = "Next text")))
    }
    @Test fun newRecipientKeepsTextEvenWhenItEqualsThePreviousMessage() {
        assertEquals("Sent text", MessageDraftCompletion.remainingText(sent, sent.copy(destination = "Fixture B")))
    }
    @Test fun changedSmsMmsModeKeepsTheCurrentDraft() {
        assertEquals("Sent text", MessageDraftCompletion.remainingText(sent, sent.copy(mms = true)))
    }
    @Test fun editedReplyAndOtherThreadsSurviveOldAcceptance() {
        val drafts = mapOf(11L to "Next reply", 12L to "Other thread")
        assertEquals(drafts, MessageDraftCompletion.acceptedReply(drafts, 11, "Old reply"))
    }
    @Test fun unchangedReplyIsRemovedWithoutTouchingAnotherThread() {
        assertEquals(mapOf(12L to "Other thread"), MessageDraftCompletion.acceptedReply(mapOf(11L to "Sent reply", 12L to "Other thread"), 11, "Sent reply"))
        assertEquals(mapOf(12L to "Other thread"), MessageDraftCompletion.acceptedReply(mapOf(12L to "Other thread"), 11, "Sent reply"))
    }
}

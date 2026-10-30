package com.sentinel.quantum.security

/** Identity of the text handed to Android; does not persist or transmit another copy. */
internal data class MessageTextDraft(val destination: String, val text: String, val mms: Boolean)

internal object MessageDraftCompletion {
    fun remainingText(submitted: MessageTextDraft, current: MessageTextDraft): String =
        if (submitted == current) "" else current.text

    fun acceptedReply(drafts: Map<Long, String>, threadId: Long, submittedText: String): Map<Long, String> =
        if (drafts[threadId] == submittedText) drafts - threadId else drafts
}

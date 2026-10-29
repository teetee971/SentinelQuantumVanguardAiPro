package com.sentinel.quantum.security

/** A provider result may only be rendered in the conversation for which it was loaded. */
internal class SmsConversationSnapshot(
    private val threadId: Long? = null,
    messages: List<SmsConversationStore.Message> = emptyList()
) {
    private val rows = messages.filter { threadId != null && threadId > 0 && it.threadId == threadId }

    fun messagesFor(currentThreadId: Long?): List<SmsConversationStore.Message> =
        if (currentThreadId != null && currentThreadId > 0 && currentThreadId == threadId) rows else emptyList()
}

/** No fallback to an unrelated or stale message when the current summary is unavailable. */
internal object SmsConversationRecipient {
    fun forThread(threadId: Long?, threads: List<SmsConversationStore.ThreadSummary>): String =
        if (threadId != null && threadId > 0) threads.firstOrNull { it.threadId == threadId }?.address.orEmpty()
        else ""
}

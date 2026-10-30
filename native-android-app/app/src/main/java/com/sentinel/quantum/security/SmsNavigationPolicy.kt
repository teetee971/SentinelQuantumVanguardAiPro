package com.sentinel.quantum.security

/** Back leaves nested message views before leaving the Activity; SENDTO keeps its caller. */
internal object SmsNavigationPolicy {
    enum class BackAction { CONVERSATIONS, FINISH }

    fun backAction(showComposer: Boolean, conversationOpen: Boolean, composerFromConversations: Boolean): BackAction =
        if (if (showComposer) composerFromConversations else conversationOpen) BackAction.CONVERSATIONS
        else BackAction.FINISH
}

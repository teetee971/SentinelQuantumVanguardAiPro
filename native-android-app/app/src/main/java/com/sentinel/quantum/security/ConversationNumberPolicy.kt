package com.sentinel.quantum.security

/** A displayed address may prefill an explicit action; it is never identity or reachability proof. */
internal object ConversationNumberPolicy {
    fun number(raw: String?): String? {
        if (raw?.any { it.isWhitespace() && it !in " \u00a0\u202f" } == true) return null
        return ContactDialNumberPolicy.fromProvider(raw)?.takeIf { number ->
            number.all { it in '0'..'9' || it == '+' }
        }
    }
}

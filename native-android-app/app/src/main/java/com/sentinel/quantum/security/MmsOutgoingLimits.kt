package com.sentinel.quantum.security

/** Shared structural limits; the selected carrier's complete-PDU limit remains authoritative. */
internal object MmsOutgoingLimits {
    const val MAX_ATTACHMENTS = 8
    const val MAX_ATTACHMENT_BYTES = 5L * 1024L * 1024L
    const val MAX_TOTAL_ATTACHMENT_BYTES = 10L * 1024L * 1024L
    const val MAX_TEXT_CHARS = 10_000
}

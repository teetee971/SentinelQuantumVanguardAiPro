package com.sentinel.quantum.security

/**
 * Truthful interpretation of the synchronous SmsManager submission boundary.
 *
 * An exception thrown while submitting a message cannot prove that no multipart segment crossed
 * into Android telephony. Durable FAILED state therefore remains reserved for validated SENT
 * callbacks reporting failure.
 */
internal object SmsSubmissionOutcomePolicy {
    const val OUTCOME_UNKNOWN = "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN"
    const val UNSUPPORTED_PART_COUNT = "SMS_MULTIPART_LIMIT_EXCEEDED"

    /** Only submit messages whose Android callbacks fit the durable multipart reducer. */
    fun reasonForPartCount(partCount: Int): String? =
        if (partCount in 1..SmsCallbackProgress.MAX_PARTS) null else UNSUPPORTED_PART_COUNT

    fun reasonForSynchronousException(): String = OUTCOME_UNKNOWN

    fun shouldMarkProviderFailedOnSynchronousException(): Boolean = false
}

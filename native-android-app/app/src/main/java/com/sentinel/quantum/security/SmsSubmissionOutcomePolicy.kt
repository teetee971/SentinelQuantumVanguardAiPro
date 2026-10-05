package com.sentinel.quantum.security

/**
 * Truthful interpretation of the SMS submission phases around SmsManager.
 *
 * Preparation and callback construction happen before Android telephony is invoked and therefore
 * have conclusive "not submitted" outcomes. Only an exception thrown by the actual SmsManager
 * send call can leave the telephony result unknown because one or more multipart segments may have
 * crossed the framework boundary before the exception became observable.
 */
internal object SmsSubmissionOutcomePolicy {
    const val OUTCOME_UNKNOWN = "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN"
    const val PREPARATION_FAILED = "SMS_TELEPHONY_PREPARATION_FAILED"
    const val CALLBACK_PREPARATION_FAILED = "SMS_CALLBACK_PREPARATION_FAILED"
    const val CALLBACK_PREPARATION_PROVIDER_REPAIR_FAILED =
        "SMS_CALLBACK_PREPARATION_FAILED_PROVIDER_REPAIR_FAILED"
    const val UNSUPPORTED_PART_COUNT = "SMS_MULTIPART_LIMIT_EXCEEDED"

    /** Only submit messages whose Android callbacks fit the durable multipart reducer. */
    fun reasonForPartCount(partCount: Int): String? =
        if (partCount in 1..SmsCallbackProgress.MAX_PARTS) null else UNSUPPORTED_PART_COUNT

    fun reasonForPreparationException(): String = PREPARATION_FAILED

    fun reasonForCallbackPreparationException(providerRepairSucceeded: Boolean): String =
        if (providerRepairSucceeded) CALLBACK_PREPARATION_FAILED
        else CALLBACK_PREPARATION_PROVIDER_REPAIR_FAILED

    fun reasonForSubmissionException(): String = OUTCOME_UNKNOWN
}

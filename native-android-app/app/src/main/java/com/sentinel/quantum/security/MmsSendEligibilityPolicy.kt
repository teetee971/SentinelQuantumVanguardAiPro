package com.sentinel.quantum.security

import android.telephony.SubscriptionManager

/**
 * Pure fail-closed eligibility boundary for outgoing MMS transport.
 *
 * The transport selects a concrete subscription before invoking this policy and must supply an
 * already canonical E.164 destination. This boundary therefore never invents a country from a
 * national number and never silently accepts two region-dependent representations as equivalent.
 * Passing this policy means only that a request is structurally eligible to be composed; it does
 * not mean Android/the carrier accepted, sent or delivered the MMS.
 */
object MmsSendEligibilityPolicy {
    const val MAX_ATTACHMENTS = 8
    const val MAX_ATTACHMENT_BYTES = 5L * 1024L * 1024L
    const val MAX_TOTAL_ATTACHMENT_BYTES = 10L * 1024L * 1024L
    const val MAX_TEXT_CHARS = 10_000

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "text/plain"
    )

    data class Attachment(val mimeType: String, val sizeBytes: Long)

    sealed class Result {
        data object Eligible : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun evaluate(
        roleState: SmsActivationDiagnostics.SmsRoleState,
        subscriptionId: Int,
        destination: String?,
        text: String,
        attachments: List<Attachment>
    ): Result {
        if (roleState != SmsActivationDiagnostics.SmsRoleState.HELD) {
            return Result.Rejected("SMS_ROLE_NOT_HELD")
        }
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return Result.Rejected("MMS_SUBSCRIPTION_REQUIRED")
        }
        if (GlobalPhoneIdentityPolicy.canonicalE164OrNull(destination) == null) {
            return Result.Rejected("E164_DESTINATION_REQUIRED")
        }
        if (text.length > MAX_TEXT_CHARS) return Result.Rejected("TEXT_TOO_LARGE")
        if (attachments.isEmpty() && text.isBlank()) return Result.Rejected("EMPTY_MMS")
        if (attachments.size > MAX_ATTACHMENTS) return Result.Rejected("TOO_MANY_ATTACHMENTS")

        var total = 0L
        for (attachment in attachments) {
            if (attachment.mimeType.lowercase() !in ALLOWED_MIME_TYPES) {
                return Result.Rejected("UNSUPPORTED_MIME_TYPE")
            }
            if (attachment.sizeBytes !in 1..MAX_ATTACHMENT_BYTES) {
                return Result.Rejected("ATTACHMENT_SIZE_REJECTED")
            }
            if (Long.MAX_VALUE - total < attachment.sizeBytes) {
                return Result.Rejected("ATTACHMENT_SIZE_OVERFLOW")
            }
            total += attachment.sizeBytes
            if (total > MAX_TOTAL_ATTACHMENT_BYTES) {
                return Result.Rejected("TOTAL_ATTACHMENT_SIZE_REJECTED")
            }
        }
        return Result.Eligible
    }
}

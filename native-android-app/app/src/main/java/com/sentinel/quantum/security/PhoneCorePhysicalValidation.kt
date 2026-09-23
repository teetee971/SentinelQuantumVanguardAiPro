package com.sentinel.quantum.security

/**
 * Derives physical Phone Core validation only from bounded local metadata already observed by
 * Android. No phone number, contact name, message body, URL or subscription identifier is needed.
 */
object PhoneCorePhysicalValidation {
    data class Evidence(
        val incomingCallConnected: Boolean,
        val outgoingCallConnected: Boolean,
        val incomingSmsReceived: Boolean,
        val outgoingSmsSubmitted: Boolean,
        val incomingMmsSafePreview: Boolean
    ) {
        val completedCount: Int
            get() = listOf(
                incomingCallConnected,
                outgoingCallConnected,
                incomingSmsReceived,
                outgoingSmsSubmitted,
                incomingMmsSafePreview
            ).count { it }

        val requiredCount: Int get() = 5

        val fullyValidated: Boolean
            get() = completedCount == requiredCount
    }

    fun evaluate(
        events: List<PhonePrivateTimeline.Event>,
        notBeforeMs: Long = 0L
    ): Evidence {
        val currentBuildEvents = events.filter { it.timestampMs >= notBeforeMs.coerceAtLeast(0L) }
        fun has(kind: PhonePrivateTimeline.Kind, direction: String, signal: String): Boolean =
            currentBuildEvents.any {
                it.kind == kind &&
                    it.direction == direction &&
                    it.signal == signal
            }

        return Evidence(
            incomingCallConnected = has(
                PhonePrivateTimeline.Kind.CALL,
                "INCOMING",
                SIGNAL_CALL_ACTIVE
            ),
            outgoingCallConnected = has(
                PhonePrivateTimeline.Kind.CALL,
                "OUTGOING",
                SIGNAL_CALL_ACTIVE
            ),
            incomingSmsReceived = has(
                PhonePrivateTimeline.Kind.SMS,
                "INCOMING",
                SIGNAL_SMS_RECEIVED
            ),
            outgoingSmsSubmitted = has(
                PhonePrivateTimeline.Kind.SMS,
                "OUTGOING",
                SIGNAL_SMS_SENT_OK
            ),
            incomingMmsSafePreview = currentBuildEvents.any {
                it.kind == PhonePrivateTimeline.Kind.MMS &&
                    it.direction == "INCOMING" &&
                    it.signal in MMS_SAFE_SIGNALS
            }
        )
    }

    const val SIGNAL_CALL_ACTIVE = "INCALL_ACTIVE"
    const val SIGNAL_SMS_RECEIVED = "SMS_RECEIVED"
    const val SIGNAL_SMS_SENT_OK = "SENT_OK"

    private val MMS_SAFE_SIGNALS = setOf(
        "MMS_SAFE_PREVIEW_READY",
        "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
    )
}

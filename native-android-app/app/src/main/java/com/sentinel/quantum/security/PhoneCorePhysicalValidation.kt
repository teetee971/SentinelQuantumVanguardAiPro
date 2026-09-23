package com.sentinel.quantum.security

/**
 * Derives physical Phone Core validation from bounded local metadata and operational provider
 * probes observed by the current installed APK. No phone number, contact name, message body,
 * URL or subscription identifier is retained as validation evidence.
 */
object PhoneCorePhysicalValidation {
    data class Evidence(
        val incomingCallConnected: Boolean,
        val outgoingCallConnected: Boolean,
        val callScreeningObserved: Boolean,
        val contactsProviderReady: Boolean,
        val callHistoryProviderReady: Boolean,
        val incomingSmsReceived: Boolean,
        val outgoingSmsSubmitted: Boolean,
        val outgoingSmsDeliveredSuccessfully: Boolean,
        val incomingMmsSafePreview: Boolean
    ) {
        val completedCount: Int
            get() = listOf(
                incomingCallConnected,
                outgoingCallConnected,
                callScreeningObserved,
                contactsProviderReady,
                callHistoryProviderReady,
                incomingSmsReceived,
                outgoingSmsSubmitted,
                outgoingSmsDeliveredSuccessfully,
                incomingMmsSafePreview
            ).count { it }

        val requiredCount: Int get() = 9

        val fullyValidated: Boolean
            get() = completedCount == requiredCount
    }

    fun evaluate(
        events: List<PhonePrivateTimeline.Event>,
        notBeforeMs: Long = 0L,
        contactsProviderReady: Boolean = false,
        callHistoryProviderReady: Boolean = false
    ): Evidence {
        val currentBuildEvents = events.filter { it.timestampMs >= notBeforeMs.coerceAtLeast(0L) }
        fun has(kind: PhonePrivateTimeline.Kind, direction: String, signal: String): Boolean =
            currentBuildEvents.any {
                it.kind == kind &&
                    it.direction == direction &&
                    it.signal == signal
            }

        val screeningObserved = currentBuildEvents.any {
            it.kind == PhonePrivateTimeline.Kind.CALL &&
                it.direction == "INCOMING" &&
                it.signal != null &&
                it.signal != SIGNAL_CALL_ACTIVE
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
            callScreeningObserved = screeningObserved,
            contactsProviderReady = contactsProviderReady,
            callHistoryProviderReady = callHistoryProviderReady,
            incomingSmsReceived = has(
                PhonePrivateTimeline.Kind.SMS,
                "INCOMING",
                SIGNAL_SMS_RECEIVED
            ),
            outgoingSmsSubmitted = has(
                PhonePrivateTimeline.Kind.SMS,
                "OUTGOING",
                SIGNAL_SMS_ALL_PARTS_SENT
            ),
            outgoingSmsDeliveredSuccessfully = has(
                PhonePrivateTimeline.Kind.SMS,
                "OUTGOING",
                SIGNAL_SMS_ALL_PARTS_DELIVERED
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
    const val SIGNAL_SMS_ALL_PARTS_SENT = "SMS_ALL_PARTS_SENT"
    const val SIGNAL_SMS_ALL_PARTS_DELIVERED = "SMS_ALL_PARTS_DELIVERED"

    private val MMS_SAFE_SIGNALS = setOf(
        "MMS_SAFE_PREVIEW_READY",
        "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
    )
}

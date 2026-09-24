package com.sentinel.quantum.security

/**
 * Derives physical Phone Core validation from bounded local metadata and operational provider
 * probes observed by the current installed APK. No phone number, contact name, message body,
 * URL or subscription identifier is retained as validation evidence.
 */
object PhoneCorePhysicalValidation {
    const val CERTIFICATION_SCHEMA_VERSION = 2
    data class Evidence(
        val incomingCallConnected: Boolean,
        val outgoingCallConnected: Boolean,
        val callScreeningObserved: Boolean,
        val contactsProviderReady: Boolean,
        val callHistoryProviderReady: Boolean,
        val incomingSmsReceived: Boolean,
        val outgoingSmsSubmitted: Boolean,
        val outgoingSmsDeliveredSuccessfully: Boolean,
        val incomingMmsSafePreview: Boolean,
        val incomingCallNotificationPosted: Boolean,
        val incomingSmsNotificationPosted: Boolean,
        val callerIdUiShown: Boolean,
        val inCallUiShown: Boolean
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
                incomingMmsSafePreview,
                incomingCallNotificationPosted,
                incomingSmsNotificationPosted,
                callerIdUiShown,
                inCallUiShown
            ).count { it }

        val requiredCount: Int get() = 13

        val fullyValidated: Boolean
            get() = completedCount == requiredCount
    }

    fun evaluateCertification(
        events: List<PhonePrivateTimeline.Event>,
        activeScope: PhoneCoreCertificationProvenance.Scope?,
        notBeforeMs: Long = 0L,
        contactsProviderReady: Boolean = false,
        callHistoryProviderReady: Boolean = false
    ): Evidence {
        val normalized = activeScope?.let(PhoneCoreCertificationProvenance::normalize)
            ?: return evaluate(emptyList(), notBeforeMs, contactsProviderReady, callHistoryProviderReady)
        val certified = events.filter {
            PhoneCoreCertificationProvenance.belongsTo(it.provenance, normalized)
        }
        return evaluate(certified, notBeforeMs, contactsProviderReady, callHistoryProviderReady)
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
                it.signal?.startsWith(SIGNAL_CALL_SCREENED_PREFIX) == true
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
            },
            incomingCallNotificationPosted = has(
                PhonePrivateTimeline.Kind.CALL,
                "INCOMING",
                SIGNAL_CALL_NOTIFICATION_POSTED
            ),
            incomingSmsNotificationPosted = has(
                PhonePrivateTimeline.Kind.SMS,
                "INCOMING",
                SIGNAL_SMS_NOTIFICATION_POSTED
            ),
            callerIdUiShown = has(
                PhonePrivateTimeline.Kind.CALL,
                "INCOMING",
                SIGNAL_CALLER_ID_UI_SHOWN
            ),
            inCallUiShown = has(
                PhonePrivateTimeline.Kind.CALL,
                "LOCAL",
                SIGNAL_INCALL_UI_SHOWN
            )
        )
    }

    const val SIGNAL_CALL_ACTIVE = "INCALL_ACTIVE"
    const val SIGNAL_CALL_SCREENED_PREFIX = "CALL_SCREENED:"
    const val SIGNAL_SMS_RECEIVED = "SMS_RECEIVED"
    const val SIGNAL_SMS_ALL_PARTS_SENT = "SMS_ALL_PARTS_SENT"
    const val SIGNAL_SMS_ALL_PARTS_DELIVERED = "SMS_ALL_PARTS_DELIVERED"
    const val SIGNAL_WIFI_SCAN_FRESH = "WIFI_SCAN_FRESH"
    const val SIGNAL_CALL_NOTIFICATION_POSTED = "CALL_NOTIFICATION_POSTED"
    const val SIGNAL_SMS_NOTIFICATION_POSTED = "SMS_NOTIFICATION_POSTED"
    const val SIGNAL_CALLER_ID_UI_SHOWN = "CALLER_ID_UI_SHOWN"
    const val SIGNAL_INCALL_UI_SHOWN = "INCALL_UI_SHOWN"

    private val MMS_SAFE_SIGNALS = setOf(
        "MMS_SAFE_PREVIEW_READY",
        "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
    )
}

package com.sentinel.quantum.security

/**
 * Derives physical Phone Core validation from bounded local metadata and operational provider
 * probes observed by the current installed APK. No phone number, contact name, message body,
 * URL or subscription identifier is retained as validation evidence.
 */
object PhoneCorePhysicalValidation {
    const val CERTIFICATION_SCHEMA_VERSION = 4
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
        val wifiScanFresh: Boolean,
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

        val missingCriteria: List<String>
            get() = buildList {
                if (!incomingCallConnected) add("incoming_call_connected")
                if (!outgoingCallConnected) add("outgoing_call_connected")
                if (!callScreeningObserved) add("call_screening_observed")
                if (!contactsProviderReady) add("contacts_provider_ready")
                if (!callHistoryProviderReady) add("call_history_provider_ready")
                if (!incomingSmsReceived) add("incoming_sms_received")
                if (!outgoingSmsSubmitted) add("outgoing_sms_submitted")
                if (!outgoingSmsDeliveredSuccessfully) add("outgoing_sms_delivered")
                if (!incomingMmsSafePreview) add("incoming_mms_safe_preview")
                if (!incomingCallNotificationPosted) add("incoming_call_notification")
                if (!incomingSmsNotificationPosted) add("incoming_sms_notification")
                if (!callerIdUiShown) add("caller_id_ui_shown")
                if (!inCallUiShown) add("in_call_ui_shown")
            }

        val fullyValidated: Boolean
            get() = missingCriteria.isEmpty() && completedCount == requiredCount
    }

    enum class CriterionKind { AUTOMATIC_CHECK, OPERATIONAL_TEST, UNKNOWN }

    private val automaticCriteria = setOf(
        "contacts_provider_ready",
        "call_history_provider_ready"
    )

    private val operationalCriteria = setOf(
        "incoming_call_connected",
        "outgoing_call_connected",
        "call_screening_observed",
        "incoming_sms_received",
        "outgoing_sms_submitted",
        "outgoing_sms_delivered",
        "incoming_mms_safe_preview",
        "incoming_call_notification",
        "incoming_sms_notification",
        "caller_id_ui_shown",
        "in_call_ui_shown"
    )

    fun criterionKind(id: String): CriterionKind = when (id) {
        in automaticCriteria -> CriterionKind.AUTOMATIC_CHECK
        in operationalCriteria -> CriterionKind.OPERATIONAL_TEST
        else -> CriterionKind.UNKNOWN
    }

    fun criterionLabel(id: String): String = when (id) {
        "incoming_call_connected" -> "Recevoir et décrocher un appel réel"
        "outgoing_call_connected" -> "Passer un appel réel"
        "call_screening_observed" -> "Observer le filtrage d’un appel entrant"
        "contacts_provider_ready" -> "Vérifier l’accès réel aux contacts"
        "call_history_provider_ready" -> "Vérifier l’accès réel à l’historique d’appels"
        "incoming_sms_received" -> "Recevoir un SMS réel"
        "outgoing_sms_submitted" -> "Envoyer un SMS réel"
        "outgoing_sms_delivered" -> "Confirmer la livraison d’un SMS sortant"
        "incoming_mms_safe_preview" -> "Recevoir et prévisualiser un MMS réel"
        "incoming_call_notification" -> "Observer une notification d’appel entrant"
        "incoming_sms_notification" -> "Observer une notification de SMS entrant"
        "caller_id_ui_shown" -> "Observer l’identification d’appel à l’écran"
        "in_call_ui_shown" -> "Observer l’interface Sentinel pendant un appel"
        else -> "Effectuer le test physique requis"
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
            wifiScanFresh = has(
                PhonePrivateTimeline.Kind.WIFI,
                "LOCAL",
                SIGNAL_WIFI_SCAN_FRESH
            ),
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

package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCorePhysicalValidationTest {
    private fun event(
        kind: PhonePrivateTimeline.Kind,
        direction: String,
        signal: String,
        timestampMs: Long = 1_000L
    ) = PhonePrivateTimeline.Event(
        kind = kind,
        timestampMs = timestampMs,
        direction = direction,
        signal = signal
    )

    private fun almostCompleteEvents() = listOf(
        event(PhonePrivateTimeline.Kind.CALL, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
        event(PhonePrivateTimeline.Kind.CALL, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
        event(
            PhonePrivateTimeline.Kind.CALL,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_SCREENED_PREFIX + "ALLOW:NO_MATCHING_RULE:NONE"
        ),
        event(PhonePrivateTimeline.Kind.SMS, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED),
        event(
            PhonePrivateTimeline.Kind.SMS,
            "OUTGOING",
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_SENT
        ),
        event(
            PhonePrivateTimeline.Kind.SMS,
            "OUTGOING",
            PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_DELIVERED
        ),
        event(
            PhonePrivateTimeline.Kind.WIFI,
            "LOCAL",
            PhoneCorePhysicalValidation.SIGNAL_WIFI_SCAN_FRESH
        ),
        event(
            PhonePrivateTimeline.Kind.CALL,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_NOTIFICATION_POSTED
        ),
        event(
            PhonePrivateTimeline.Kind.SMS,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_SMS_NOTIFICATION_POSTED
        ),
        event(
            PhonePrivateTimeline.Kind.CALL,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALLER_ID_UI_SHOWN
        ),
        event(
            PhonePrivateTimeline.Kind.CALL,
            "LOCAL",
            PhoneCorePhysicalValidation.SIGNAL_INCALL_UI_SHOWN
        )
    )

    @Test fun schemaV2RequiresAllThirteenPhoneCoreChecks() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = almostCompleteEvents(),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertEquals(12, evidence.completedCount)
        assertEquals(13, evidence.requiredCount)
        assertFalse(evidence.fullyValidated)
        assertFalse(evidence.incomingMmsSafePreview)
        assertTrue(evidence.outgoingSmsSubmitted)
        assertTrue(evidence.outgoingSmsDeliveredSuccessfully)
        assertTrue(evidence.callScreeningObserved)
    }

    @Test fun safeIncomingMmsCompletesLocalOperationalValidation() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = almostCompleteEvents() + event(
                PhonePrivateTimeline.Kind.MMS,
                "INCOMING",
                "MMS_DOWNLOAD_SAFE_PREVIEW_READY"
            ),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertTrue(evidence.fullyValidated)
        assertEquals(12, evidence.completedCount)
    }

    @Test fun rawFragmentCallbacksDoNotProveMultipartSuccess() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(PhonePrivateTimeline.Kind.CALL, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
                event(PhonePrivateTimeline.Kind.CALL, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
                event(
            PhonePrivateTimeline.Kind.CALL,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_SCREENED_PREFIX + "ALLOW:NO_MATCHING_RULE:NONE"
        ),
                event(PhonePrivateTimeline.Kind.SMS, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "SENT_OK"),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "DELIVERED_OK"),
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_SAFE_PREVIEW_READY"),
                event(PhonePrivateTimeline.Kind.WIFI, "LOCAL", PhoneCorePhysicalValidation.SIGNAL_WIFI_SCAN_FRESH)
            ),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertFalse(evidence.outgoingSmsSubmitted)
        assertFalse(evidence.outgoingSmsDeliveredSuccessfully)
        assertEquals(7, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun aggregateSendWithoutSuccessfulDeliveryDoesNotCompleteValidation() {
        val withoutDelivery = almostCompleteEvents()
            .filterNot { it.signal == PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_DELIVERED }
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = withoutDelivery + event(
                PhonePrivateTimeline.Kind.MMS,
                "INCOMING",
                "MMS_SAFE_PREVIEW_READY"
            ),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertTrue(evidence.outgoingSmsSubmitted)
        assertFalse(evidence.outgoingSmsDeliveredSuccessfully)
        assertEquals(12, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun deliveryErrorNeverCountsAsSuccessfulPhysicalDelivery() {
        val withoutSuccessfulDelivery = almostCompleteEvents()
            .filterNot { it.signal == PhoneCorePhysicalValidation.SIGNAL_SMS_ALL_PARTS_DELIVERED }
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = withoutSuccessfulDelivery +
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "DELIVERY_ERROR_2") +
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_SAFE_PREVIEW_READY"),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertTrue(evidence.outgoingSmsSubmitted)
        assertFalse(evidence.outgoingSmsDeliveredSuccessfully)
        assertEquals(12, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun wifiEvidenceDoesNotCountTowardPhoneCoreSchemaV2() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(PhonePrivateTimeline.Kind.WIFI, "LOCAL", PhoneCorePhysicalValidation.SIGNAL_WIFI_SCAN_FRESH)
            )
        )
        assertEquals(PhoneCorePhysicalValidation.CERTIFICATION_SCHEMA_VERSION, 2)
        assertEquals(13, evidence.requiredCount)
        assertEquals(0, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun providersAreRequiredEvenWhenTransportSignalsExist() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = almostCompleteEvents() + event(
                PhonePrivateTimeline.Kind.MMS,
                "INCOMING",
                "MMS_SAFE_PREVIEW_READY"
            ),
            contactsProviderReady = false,
            callHistoryProviderReady = false
        )
        assertEquals(12, evidence.completedCount)
        assertFalse(evidence.contactsProviderReady)
        assertFalse(evidence.callHistoryProviderReady)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun ignoresEvidenceFromBeforeCurrentApkInstall() {
        val old = event(
            PhonePrivateTimeline.Kind.WIFI,
            "LOCAL",
            PhoneCorePhysicalValidation.SIGNAL_WIFI_SCAN_FRESH,
            timestampMs = 999L
        )
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(old),
            notBeforeMs = 1_000L
        )
        assertEquals(0, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }


    @Test fun unrelatedIncomingCallEventDoesNotProveScreening() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(PhonePrivateTimeline.Kind.CALL, "INCOMING", "SHORT_RING")
            )
        )
        assertFalse(evidence.callScreeningObserved)
        assertEquals(0, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun screeningCountsButFailedSendQuarantinedMmsAndStaleWifiDoNot() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(
            PhonePrivateTimeline.Kind.CALL,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_SCREENED_PREFIX + "ALLOW:NO_MATCHING_RULE:NONE"
        ),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "SENT_ERROR_1"),
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_LOCAL_QUARANTINE"),
                event(PhonePrivateTimeline.Kind.WIFI, "LOCAL", "WIFI_SCAN_CACHED")
            )
        )
        assertEquals(1, evidence.completedCount)
        assertTrue(evidence.callScreeningObserved)
        assertFalse(evidence.outgoingSmsSubmitted)
        assertFalse(evidence.outgoingSmsDeliveredSuccessfully)
        assertFalse(evidence.incomingMmsSafePreview)
        assertFalse(evidence.wifiFreshScanObserved)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun notificationPublicationProofsAreIndependent() {
        val callOnly = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(
                    PhonePrivateTimeline.Kind.CALL,
                    "INCOMING",
                    PhoneCorePhysicalValidation.SIGNAL_CALL_NOTIFICATION_POSTED
                )
            )
        )
        assertTrue(callOnly.incomingCallNotificationPosted)
        assertFalse(callOnly.incomingSmsNotificationPosted)
        assertEquals(1, callOnly.completedCount)

        val smsOnly = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(
                    PhonePrivateTimeline.Kind.SMS,
                    "INCOMING",
                    PhoneCorePhysicalValidation.SIGNAL_SMS_NOTIFICATION_POSTED
                )
            )
        )
        assertFalse(smsOnly.incomingCallNotificationPosted)
        assertTrue(smsOnly.incomingSmsNotificationPosted)
        assertEquals(1, smsOnly.completedCount)
    }

    @Test fun callerIdAndInCallUiProofsAreIndependentAndRequired() {
        val callerIdOnly = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(
                    PhonePrivateTimeline.Kind.CALL,
                    "INCOMING",
                    PhoneCorePhysicalValidation.SIGNAL_CALLER_ID_UI_SHOWN
                )
            )
        )
        assertTrue(callerIdOnly.callerIdUiShown)
        assertFalse(callerIdOnly.inCallUiShown)
        assertEquals(1, callerIdOnly.completedCount)

        val inCallOnly = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(
                    PhonePrivateTimeline.Kind.CALL,
                    "LOCAL",
                    PhoneCorePhysicalValidation.SIGNAL_INCALL_UI_SHOWN
                )
            )
        )
        assertFalse(inCallOnly.callerIdUiShown)
        assertTrue(inCallOnly.inCallUiShown)
        assertEquals(1, inCallOnly.completedCount)
    }

}

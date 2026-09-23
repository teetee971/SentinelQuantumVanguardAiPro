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
        event(PhonePrivateTimeline.Kind.CALL, "INCOMING", "ALLOW:NO_MATCHING_RULE:NONE"),
        event(PhonePrivateTimeline.Kind.SMS, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED),
        event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_SMS_SENT_OK),
        event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_SMS_DELIVERED_OK)
    )

    @Test fun requiresAllNineLocalOperationalChecks() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = almostCompleteEvents(),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertEquals(8, evidence.completedCount)
        assertEquals(9, evidence.requiredCount)
        assertFalse(evidence.fullyValidated)
        assertFalse(evidence.incomingMmsSafePreview)
        assertTrue(evidence.outgoingSmsDeliveryStatusObserved)
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
        assertEquals(9, evidence.completedCount)
    }

    @Test fun sentOkAloneDoesNotProveDeliveryStatusPath() {
        val withoutDeliveryCallback = almostCompleteEvents()
            .filterNot { it.signal == PhoneCorePhysicalValidation.SIGNAL_SMS_DELIVERED_OK }
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = withoutDeliveryCallback + event(
                PhonePrivateTimeline.Kind.MMS,
                "INCOMING",
                "MMS_SAFE_PREVIEW_READY"
            ),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertTrue(evidence.outgoingSmsSubmitted)
        assertFalse(evidence.outgoingSmsDeliveryStatusObserved)
        assertEquals(8, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun deliveryErrorStillProvesCallbackPathWasObserved() {
        val withoutSuccessfulDelivery = almostCompleteEvents()
            .filterNot { it.signal == PhoneCorePhysicalValidation.SIGNAL_SMS_DELIVERED_OK }
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = withoutSuccessfulDelivery +
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "DELIVERY_ERROR_2") +
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_SAFE_PREVIEW_READY"),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertTrue(evidence.outgoingSmsDeliveryStatusObserved)
        assertTrue(evidence.fullyValidated)
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
        assertEquals(7, evidence.completedCount)
        assertFalse(evidence.contactsProviderReady)
        assertFalse(evidence.callHistoryProviderReady)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun ignoresEvidenceFromBeforeCurrentApkInstall() {
        val old = event(
            PhonePrivateTimeline.Kind.CALL,
            "INCOMING",
            PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE,
            timestampMs = 999L
        )
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(old),
            notBeforeMs = 1_000L
        )
        assertEquals(0, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }

    @Test fun screeningCountsButFailedSendAndQuarantinedMmsDoNot() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = listOf(
                event(PhonePrivateTimeline.Kind.CALL, "INCOMING", "ALLOW:NO_MATCHING_RULE:NONE"),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "SENT_ERROR_1"),
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_LOCAL_QUARANTINE")
            )
        )
        assertEquals(1, evidence.completedCount)
        assertTrue(evidence.callScreeningObserved)
        assertFalse(evidence.outgoingSmsSubmitted)
        assertFalse(evidence.outgoingSmsDeliveryStatusObserved)
        assertFalse(evidence.incomingMmsSafePreview)
        assertFalse(evidence.fullyValidated)
    }
}

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
        event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_SMS_SENT_OK)
    )

    @Test fun requiresAllEightOperationalChecks() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            events = almostCompleteEvents(),
            contactsProviderReady = true,
            callHistoryProviderReady = true
        )
        assertEquals(7, evidence.completedCount)
        assertEquals(8, evidence.requiredCount)
        assertFalse(evidence.fullyValidated)
        assertFalse(evidence.incomingMmsSafePreview)
        assertTrue(evidence.callScreeningObserved)
    }

    @Test fun safeIncomingMmsCompletesOperationalValidation() {
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
        assertEquals(8, evidence.completedCount)
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
        assertEquals(6, evidence.completedCount)
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

    @Test fun screeningCountsButFailedSmsAndQuarantinedMmsDoNot() {
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
        assertFalse(evidence.incomingMmsSafePreview)
        assertFalse(evidence.fullyValidated)
    }
}

package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCorePhysicalValidationTest {
    private fun event(
        kind: PhonePrivateTimeline.Kind,
        direction: String,
        signal: String
    ) = PhonePrivateTimeline.Event(
        kind = kind,
        timestampMs = 1_000L,
        direction = direction,
        signal = signal
    )

    @Test fun requiresAllFiveObservedPhysicalSignals() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            listOf(
                event(PhonePrivateTimeline.Kind.CALL, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
                event(PhonePrivateTimeline.Kind.CALL, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
                event(PhonePrivateTimeline.Kind.SMS, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_SMS_SENT_OK)
            )
        )
        assertEquals(4, evidence.completedCount)
        assertEquals(5, evidence.requiredCount)
        assertFalse(evidence.fullyValidated)
        assertFalse(evidence.incomingMmsSafePreview)
    }

    @Test fun safeIncomingMmsCompletesPhysicalValidation() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            listOf(
                event(PhonePrivateTimeline.Kind.CALL, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
                event(PhonePrivateTimeline.Kind.CALL, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_CALL_ACTIVE),
                event(PhonePrivateTimeline.Kind.SMS, "INCOMING", PhoneCorePhysicalValidation.SIGNAL_SMS_RECEIVED),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", PhoneCorePhysicalValidation.SIGNAL_SMS_SENT_OK),
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_DOWNLOAD_SAFE_PREVIEW_READY")
            )
        )
        assertTrue(evidence.fullyValidated)
        assertEquals(5, evidence.completedCount)
    }

    @Test fun screeningAndFailedSmsDoNotCountAsPhysicalSuccess() {
        val evidence = PhoneCorePhysicalValidation.evaluate(
            listOf(
                event(PhonePrivateTimeline.Kind.CALL, "INCOMING", "ALLOW:RULE"),
                event(PhonePrivateTimeline.Kind.SMS, "OUTGOING", "SENT_ERROR_1"),
                event(PhonePrivateTimeline.Kind.MMS, "INCOMING", "MMS_LOCAL_QUARANTINE")
            )
        )
        assertEquals(0, evidence.completedCount)
        assertFalse(evidence.fullyValidated)
    }
}

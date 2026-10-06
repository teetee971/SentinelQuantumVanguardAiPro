package com.sentinel.quantum.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the callback-reducer boundary without consulting Android telephony identity.
 *
 * A concatenated UCS-2 SMS can carry at most 67 UTF-16 code units per segment once the
 * concatenation header is present. Sentinel accepts bodies up to MAX_BODY_CHARS, so a CJK body at
 * that application limit necessarily requires more segments than the durable callback reducer can
 * track. The production sender still uses SmsManager.divideMessage() for the actual carrier-aware
 * split and rejects the send when the resulting part count exceeds MAX_PARTS.
 *
 * Do not replace this arithmetic with SmsManager.divideMessage() or SmsMessage.calculateLength():
 * both may consult subscription/carrier EMS state and require telephony identity that an
 * instrumentation process legitimately does not hold on newer Android releases.
 */
@RunWith(AndroidJUnit4::class)
class SmsMultipartBoundaryInstrumentationTest {
    @Test
    fun longUnicodeMessageCannotCrossTheCallbackTrackingLimit() {
        val maximumUcs2CodeUnitsPerMultipartSegment = 67
        val minimumPartCount =
            (SentinelSmsSender.MAX_BODY_CHARS + maximumUcs2CodeUnitsPerMultipartSegment - 1) /
                maximumUcs2CodeUnitsPerMultipartSegment

        assertTrue(minimumPartCount > SmsCallbackProgress.MAX_PARTS)
        assertEquals(
            "SMS_MULTIPART_LIMIT_EXCEEDED",
            SmsSubmissionOutcomePolicy.reasonForPartCount(minimumPartCount)
        )
    }
}

package com.sentinel.quantum.security

import android.telephony.SmsMessage
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses Android's SMS encoding calculation without binding the test to a default SmsManager.
 *
 * SmsManager.divideMessage() resolves subscription/carrier EMS configuration and can legitimately
 * require telephony identity that an instrumentation test does not hold on newer Android releases.
 * This test only needs the framework's encoded multipart count to prove that the maximum accepted
 * Unicode body cannot fit in the durable callback reducer.
 */
@RunWith(AndroidJUnit4::class)
class SmsMultipartBoundaryInstrumentationTest {
    @Test
    fun longUnicodeMessageCannotCrossTheCallbackTrackingLimit() {
        val encodedLength = SmsMessage.calculateLength(
            "漢".repeat(SentinelSmsSender.MAX_BODY_CHARS),
            false
        )
        val partCount = encodedLength[0]

        assertTrue(partCount > SmsCallbackProgress.MAX_PARTS)
        assertEquals(
            "SMS_MULTIPART_LIMIT_EXCEEDED",
            SmsSubmissionOutcomePolicy.reasonForPartCount(partCount)
        )
    }
}

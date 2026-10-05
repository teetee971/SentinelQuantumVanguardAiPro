package com.sentinel.quantum.security

import android.telephony.SmsManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real Android fragmentation without submitting an SMS to telephony. */
@RunWith(AndroidJUnit4::class)
class SmsMultipartBoundaryInstrumentationTest {
    @Test
    fun longUnicodeMessageCannotCrossTheCallbackTrackingLimit() {
        @Suppress("DEPRECATION")
        val manager = SmsManager.getDefault()
        val parts = manager.divideMessage("漢".repeat(SentinelSmsSender.MAX_BODY_CHARS))
        assertTrue(parts.size > SmsCallbackProgress.MAX_PARTS)
        assertEquals("SMS_MULTIPART_LIMIT_EXCEEDED", SmsSubmissionOutcomePolicy.reasonForPartCount(parts.size))
    }

}

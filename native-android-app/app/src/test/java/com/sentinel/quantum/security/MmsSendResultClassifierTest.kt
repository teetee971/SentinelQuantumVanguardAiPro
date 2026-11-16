package com.sentinel.quantum.security

import android.app.Activity
import android.telephony.SmsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendResultClassifierTest {
    @Test fun successIsTheOnlySuccessfulOutcome() {
        val success = MmsSendResultClassifier.classify(Activity.RESULT_OK)
        assertTrue(success.success)
        assertEquals(PhoneCorePhysicalValidation.SIGNAL_MMS_SENT_OK, success.signal)
        assertTrue(success.preview.contains("réception par le destinataire n'est pas confirmée"))
    }

    @Test fun everyDocumentedMmsFailureMapsToStableFactualState() {
        val cases = listOf(
            SmsManager.MMS_ERROR_UNSPECIFIED to "MMS_SEND_UNSPECIFIED_ERROR",
            SmsManager.MMS_ERROR_INVALID_APN to "MMS_SEND_INVALID_APN",
            SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS to "MMS_SEND_CONNECTION_FAILED",
            SmsManager.MMS_ERROR_HTTP_FAILURE to "MMS_SEND_HTTP_FAILURE",
            SmsManager.MMS_ERROR_IO_ERROR to "MMS_SEND_IO_ERROR",
            SmsManager.MMS_ERROR_RETRY to "MMS_SEND_RETRY_FAILED",
            SmsManager.MMS_ERROR_CONFIGURATION_ERROR to "MMS_SEND_CONFIGURATION_ERROR",
            SmsManager.MMS_ERROR_NO_DATA_NETWORK to "MMS_SEND_NO_DATA_NETWORK",
            SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID to "MMS_SEND_INVALID_SUBSCRIPTION",
            SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION to "MMS_SEND_INACTIVE_SUBSCRIPTION",
            SmsManager.MMS_ERROR_DATA_DISABLED to "MMS_SEND_DATA_DISABLED",
            SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER to "MMS_SEND_DISABLED_BY_CARRIER"
        )

        cases.forEach { (code, expectedSignal) ->
            val outcome = MmsSendResultClassifier.classify(code)
            assertFalse("code=$code", outcome.success)
            assertEquals("code=$code", expectedSignal, outcome.signal)
            assertTrue("code=$code", outcome.title.isNotBlank())
            assertTrue("code=$code", outcome.preview.isNotBlank())
            assertTrue("code=$code", outcome.diagnostic.isNotBlank())
        }
    }

    @Test fun httpStatusIsBoundedAndPersistedOnlyForHttpFailure() {
        val http = MmsSendResultClassifier.classify(SmsManager.MMS_ERROR_HTTP_FAILURE, 503)
        assertEquals("MMS_SEND_HTTP_FAILURE:HTTP_503", http.signal)
        assertTrue(http.preview.contains("503"))

        val invalidHttp = MmsSendResultClassifier.classify(SmsManager.MMS_ERROR_HTTP_FAILURE, 999)
        assertEquals("MMS_SEND_HTTP_FAILURE", invalidHttp.signal)
        assertFalse(invalidHttp.preview.contains("999"))

        val nonHttp = MmsSendResultClassifier.classify(SmsManager.MMS_ERROR_IO_ERROR, 503)
        assertEquals("MMS_SEND_IO_ERROR", nonHttp.signal)
        assertFalse(nonHttp.preview.contains("503"))
    }

    @Test fun unknownCodesFailClosedAndRemainDiagnosable() {
        val outcome = MmsSendResultClassifier.classify(9876)
        assertFalse(outcome.success)
        assertEquals("MMS_SEND_UNKNOWN_ERROR:9876", outcome.signal)
        assertTrue(outcome.preview.contains("9876"))
    }

    @Test fun everySignalCanBePersistedByPrivateTimelineSanitizer() {
        val knownCodes = listOf(
            Activity.RESULT_OK,
            SmsManager.MMS_ERROR_UNSPECIFIED,
            SmsManager.MMS_ERROR_INVALID_APN,
            SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS,
            SmsManager.MMS_ERROR_HTTP_FAILURE,
            SmsManager.MMS_ERROR_IO_ERROR,
            SmsManager.MMS_ERROR_RETRY,
            SmsManager.MMS_ERROR_CONFIGURATION_ERROR,
            SmsManager.MMS_ERROR_NO_DATA_NETWORK,
            SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID,
            SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION,
            SmsManager.MMS_ERROR_DATA_DISABLED,
            SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER,
            9876
        )

        knownCodes.forEach { code ->
            val outcome = MmsSendResultClassifier.classify(
                code,
                if (code == SmsManager.MMS_ERROR_HTTP_FAILURE) 503 else null
            )
            val event = PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.MMS,
                timestampMs = 1_000L,
                direction = "OUTGOING",
                signal = outcome.signal
            )
            assertEquals(event, PhonePrivateTimeline.sanitize(event, nowMs = 1_000L))
        }
    }
}

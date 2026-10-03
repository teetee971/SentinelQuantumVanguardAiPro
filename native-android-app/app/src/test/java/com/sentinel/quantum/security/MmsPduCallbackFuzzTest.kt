package com.sentinel.quantum.security

import android.app.Activity
import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPduCallbackFuzzTest {
    @Test fun deterministicPduFuzzNeverThrowsOrEscapesStagingBound() {
        val random = Random(0x1455)
        val allowedMime = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "text/plain")

        repeat(500) { iteration ->
            val destination = when (iteration % 5) {
                0 -> "+336${random.nextInt(10_000_000, 99_999_999)}"
                1 -> "0690${random.nextInt(10_000, 99_999)}"
                2 -> "bad number ${random.nextInt()}"
                3 -> "+12+${random.nextInt(1000)}"
                else -> ""
            }
            val transactionLength = random.nextInt(0, 46)
            val transactionId = buildString(transactionLength) {
                repeat(transactionLength) {
                    append(random.nextInt(0x20, 0x80).toChar())
                }
            }
            val textLength = random.nextInt(0, 1_025)
            val text = buildString(textLength) {
                repeat(textLength) { append(('a'.code + random.nextInt(26)).toChar()) }
            }
            val partCount = random.nextInt(0, MmsSendEligibilityPolicy.MAX_ATTACHMENTS + 3)
            val parts = List(partCount) { index ->
                val mime = if ((iteration + index) % 7 == 0) {
                    "application/x-unsupported"
                } else {
                    allowedMime[random.nextInt(allowedMime.size)]
                }
                val size = when ((iteration + index) % 9) {
                    0 -> 0
                    else -> random.nextInt(1, 4_097)
                }
                SentinelMmsSendPduComposer.Part(mime, ByteArray(size) { random.nextInt(256).toByte() })
            }

            val result = runCatching {
                SentinelMmsSendPduComposer.compose(destination, transactionId, text, parts)
            }.getOrElse { failure ->
                throw AssertionError("Composer threw on deterministic fuzz iteration $iteration", failure)
            }

            if (result is SentinelMmsSendPduComposer.Result.Composed) {
                assertTrue(result.pdu.isNotEmpty())
                assertTrue(result.pdu.size.toLong() <= MmsSendPduStager.MAX_STAGED_PDU_BYTES)
            }
        }
    }

    @Test fun unknownCallbackCodesAlwaysFailClosedAndRemainTimelineSafe() {
        val knownSuccess = Activity.RESULT_OK
        for (code in -512..512) {
            val httpStatus = if (code % 3 == 0) 100 + (code + 512) % 500 else null
            val outcome = MmsSendResultClassifier.classify(code, httpStatus)
            if (code == knownSuccess) {
                assertTrue(outcome.success)
            } else {
                assertFalse("Unexpected success for callback code $code", outcome.success)
            }
            val event = PhonePrivateTimeline.Event(
                kind = PhonePrivateTimeline.Kind.MMS,
                timestampMs = 1_000L,
                direction = "OUTGOING",
                signal = outcome.signal
            )
            assertNotNull(PhonePrivateTimeline.sanitize(event, nowMs = 1_000L))
        }
    }
}

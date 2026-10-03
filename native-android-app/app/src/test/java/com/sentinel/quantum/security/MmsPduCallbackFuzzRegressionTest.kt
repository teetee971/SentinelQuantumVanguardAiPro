package com.sentinel.quantum.security

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Deterministic fuzz/regression probes for the bounded outgoing MMS surfaces.
 *
 * A fixed seed keeps CI reproducible while still exercising a much wider state space than the
 * hand-picked examples. No case is allowed to throw or turn malformed input into success.
 */
class MmsPduCallbackFuzzRegressionTest {
    @Test fun printableTransactionIdsWithinContractComposeAndOversizedIdsFailClosed() {
        val random = Random(0x51E71E1)
        repeat(1_000) {
            val validLength = random.nextInt(1, 41)
            val validId = buildString(validLength) {
                repeat(validLength) { append(random.nextInt(0x21, 0x7f).toChar()) }
            }
            val valid = SentinelMmsSendPduComposer.compose(
                destination = "+15551234567",
                transactionId = validId,
                text = "probe",
                attachments = emptyList()
            )
            assertTrue("length=$validLength id=$validId", valid is SentinelMmsSendPduComposer.Result.Composed)

            val oversizedLength = random.nextInt(41, 65)
            val oversizedId = "x".repeat(oversizedLength)
            val oversized = SentinelMmsSendPduComposer.compose(
                destination = "+15551234567",
                transactionId = oversizedId,
                text = "probe",
                attachments = emptyList()
            )
            assertEquals(
                "INVALID_TRANSACTION_ID",
                (oversized as SentinelMmsSendPduComposer.Result.Rejected).reason
            )
        }
    }

    @Test fun nonPrintableTransactionIdsNeverReachPduSuccess() {
        val random = Random(0xBAD1D)
        val forbidden = charArrayOf('\u0000', '\u0001', '\u001f', '\u007f', '\u0080', '\uffff')
        repeat(500) {
            val id = "safe-${random.nextInt()}-${forbidden[random.nextInt(forbidden.size)]}"
            val outcome = SentinelMmsSendPduComposer.compose(
                destination = "+15551234567",
                transactionId = id.take(40),
                text = "probe",
                attachments = emptyList()
            )
            assertEquals(
                "INVALID_TRANSACTION_ID",
                (outcome as SentinelMmsSendPduComposer.Result.Rejected).reason
            )
        }
    }

    @Test fun randomBoundedSupportedPartsComposeWithoutThrowing() {
        val random = Random(0xC0FFEE)
        val mimeTypes = listOf("text/plain", "image/jpeg", "image/png", "image/gif", "image/webp")
        repeat(750) { iteration ->
            val partCount = random.nextInt(0, MmsSendEligibilityPolicy.MAX_ATTACHMENTS + 1)
            val attachments = List(partCount) {
                val size = random.nextInt(1, 129)
                SentinelMmsSendPduComposer.Part(
                    mimeType = mimeTypes[random.nextInt(mimeTypes.size)],
                    payload = ByteArray(size) { random.nextInt(0, 256).toByte() }
                )
            }
            val text = if (partCount == 0 || random.nextBoolean()) {
                "fuzz-$iteration-${random.nextInt()}"
            } else {
                ""
            }
            val outcome = SentinelMmsSendPduComposer.compose(
                destination = "+1555${random.nextInt(1_000_000, 9_999_999)}",
                transactionId = "fuzz-$iteration",
                text = text,
                attachments = attachments
            )
            assertTrue("iteration=$iteration parts=$partCount", outcome is SentinelMmsSendPduComposer.Result.Composed)
            val pdu = (outcome as SentinelMmsSendPduComposer.Result.Composed).pdu
            assertTrue(pdu.isNotEmpty())
            assertEquals(0x8c.toByte(), pdu[0])
            assertEquals(0x80.toByte(), pdu[1])
        }
    }

    @Test fun unsupportedMimeAndInvalidDestinationFuzzNeverCompose() {
        val random = Random(0xFA11C105.toInt())
        val badMimeTypes = listOf(
            "application/pdf",
            "application/octet-stream",
            "image/svg+xml",
            "IMAGE/JPEG",
            "",
            "text/html"
        )
        repeat(500) { iteration ->
            val unsupported = SentinelMmsSendPduComposer.compose(
                destination = "+15551234567",
                transactionId = "mime-$iteration",
                text = "",
                attachments = listOf(
                    SentinelMmsSendPduComposer.Part(
                        badMimeTypes[random.nextInt(badMimeTypes.size)],
                        byteArrayOf(1)
                    )
                )
            )
            assertEquals(
                "UNSUPPORTED_MIME",
                (unsupported as SentinelMmsSendPduComposer.Result.Rejected).reason
            )

            val invalidDestination = when (random.nextInt(5)) {
                0 -> ""
                1 -> "+33 6 12 34 56 78"
                2 -> "tel:+15551234567"
                3 -> "+1555A${random.nextInt(1000)}"
                else -> "9".repeat(33 + random.nextInt(20))
            }
            val invalid = SentinelMmsSendPduComposer.compose(
                destination = invalidDestination,
                transactionId = "dest-$iteration",
                text = "probe",
                attachments = emptyList()
            )
            assertEquals(
                "INVALID_DESTINATION",
                (invalid as SentinelMmsSendPduComposer.Result.Rejected).reason
            )
        }
    }

    @Test fun uintvarRoundTripsAcrossRandomNonNegativeInts() {
        val random = Random(0x117A4)
        val edgeCases = listOf(0, 1, 127, 128, 16_383, 16_384, 2_097_151, Int.MAX_VALUE)
        val values = edgeCases + List(2_000) { random.nextInt(0, Int.MAX_VALUE) }
        for (value in values) {
            val encoded = SentinelMmsSendPduComposer.encodeUintvar(value)
            assertTrue("value=$value size=${encoded.size}", encoded.size in 1..5)
            val decoded = decodeUintvar(encoded)
            assertEquals("value=$value", value.toLong(), decoded)
            encoded.dropLast(1).forEach { byte ->
                assertTrue("non-terminal byte must carry continuation", byte.toInt() and 0x80 != 0)
            }
            assertFalse("terminal byte must end the value", encoded.last().toInt() and 0x80 != 0)
        }
    }

    @Test fun arbitraryCallbackCodesNeverBecomeSuccessExceptAndroidResultOk() {
        val random = Random(0xCA11BAC)
        repeat(5_000) {
            var code = random.nextInt(-20_000, 20_001)
            if (code == Activity.RESULT_OK) code = 20_001
            val outcome = MmsSendResultClassifier.classify(code, random.nextInt(-500, 1_000))
            assertFalse("unexpected success for code=$code", outcome.success)
            assertTrue(outcome.signal.isNotBlank())
            assertTrue(outcome.diagnostic.isNotBlank())
        }

        val success = MmsSendResultClassifier.classify(Activity.RESULT_OK, 503)
        assertTrue(success.success)
        assertEquals(PhoneCorePhysicalValidation.SIGNAL_MMS_SENT_OK, success.signal)
    }

    private fun decodeUintvar(bytes: ByteArray): Long {
        var value = 0L
        for ((index, byte) in bytes.withIndex()) {
            value = (value shl 7) or (byte.toInt() and 0x7f).toLong()
            if (byte.toInt() and 0x80 == 0) {
                check(index == bytes.lastIndex) { "trailing bytes after uintvar terminal" }
                return value
            }
        }
        error("unterminated uintvar")
    }
}

package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MmsDecodePipelineTest {
    @Test fun rejectsEmptyPduBeforeDecoder() {
        var called = false
        val result = MmsDecodePipeline.decodeAndValidate(byteArrayOf()) { called = true; MmsPduDecoder.DecodeResult.Decoded(emptyList()) }
        assertTrue(result is MmsDecodePipeline.Result.Rejected)
        assertFalse(called)
    }

    @Test fun rejectsOversizedPduBeforeDecoder() {
        var called = false
        val result = MmsDecodePipeline.decodeAndValidate(ByteArray(512 * 1024 + 1)) {
            called = true
            MmsPduDecoder.DecodeResult.Decoded(emptyList())
        }
        assertEquals("INVALID_PDU_SIZE", (result as MmsDecodePipeline.Result.Rejected).reason)
        assertFalse(called)
    }

    @Test fun decoderFailureIsFailClosed() {
        val result = MmsDecodePipeline.decodeAndValidate(byteArrayOf(1)) { throw IllegalStateException("bad pdu") }
        assertEquals("PDU_DECODER_FAILED", (result as MmsDecodePipeline.Result.Rejected).reason)
    }

    @Test fun decoderCannotMutateCallerPdu() {
        val pdu = byteArrayOf(1, 2, 3)
        MmsDecodePipeline.decodeAndValidate(pdu) { supplied ->
            supplied[0] = 99
            MmsPduDecoder.DecodeResult.Rejected("invalid")
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), pdu)
    }

    @Test fun decoderRejectionReasonIsBounded() {
        val result = MmsDecodePipeline.decodeAndValidate(byteArrayOf(1)) {
            MmsPduDecoder.DecodeResult.Rejected("x".repeat(200))
        }
        val reason = (result as MmsDecodePipeline.Result.Rejected).reason
        assertTrue(reason.startsWith("PDU_REJECTED:"))
        assertEquals("PDU_REJECTED:".length + 80, reason.length)
    }

    @Test fun unsafeDecodedPartStillPassesThroughSafetyBoundary() {
        val result = MmsDecodePipeline.decodeAndValidate(byteArrayOf(1)) {
            MmsPduDecoder.DecodeResult.Decoded(listOf(MmsDecodeBoundary.DecodedPart("application/octet-stream", "x.bin", byteArrayOf(1))))
        }
        assertEquals("MIME_NOT_PREVIEWABLE", (result as MmsDecodePipeline.Result.Rejected).reason)
    }

    @Test fun safeTextPartCanPassBoundary() {
        val result = MmsDecodePipeline.decodeAndValidate(byteArrayOf(1)) {
            MmsPduDecoder.DecodeResult.Decoded(listOf(MmsDecodeBoundary.DecodedPart("text/plain", "note.txt", "bonjour".toByteArray())))
        }
        assertTrue(result is MmsDecodePipeline.Result.Accepted)
    }
}

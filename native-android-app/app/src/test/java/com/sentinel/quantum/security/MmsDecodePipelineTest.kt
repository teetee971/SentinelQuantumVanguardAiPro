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

    @Test fun decoderFailureIsFailClosed() {
        val result = MmsDecodePipeline.decodeAndValidate(byteArrayOf(1)) { throw IllegalStateException("bad pdu") }
        assertEquals("PDU_DECODER_FAILED", (result as MmsDecodePipeline.Result.Rejected).reason)
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

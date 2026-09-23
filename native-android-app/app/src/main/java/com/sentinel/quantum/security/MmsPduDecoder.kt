package com.sentinel.quantum.security

/** Contract for an audited MMS/PDU decoder. Implementations treat PDU bytes as untrusted. */
fun interface MmsPduDecoder {
    fun decode(pdu: ByteArray): DecodeResult

    sealed class DecodeResult {
        data class Decoded(val parts: List<MmsDecodeBoundary.DecodedPart>) : DecodeResult()
        data class Rejected(val reason: String) : DecodeResult()
    }
}

/** Fail-closed adapter between a future maintained decoder and the existing safety boundary. */
object MmsDecodePipeline {
    sealed class Result {
        data class Accepted(val parts: List<MmsDecodeBoundary.SafePart>) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun decodeAndValidate(pdu: ByteArray, decoder: MmsPduDecoder): Result {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return Result.Rejected("INVALID_PDU_SIZE")
        val decoded = try { decoder.decode(pdu.copyOf()) } catch (_: Exception) {
            return Result.Rejected("PDU_DECODER_FAILED")
        }
        return when (decoded) {
            is MmsPduDecoder.DecodeResult.Rejected -> Result.Rejected("PDU_REJECTED:" + decoded.reason.take(MAX_REASON_CHARS))
            is MmsPduDecoder.DecodeResult.Decoded -> when (val validated = MmsDecodeBoundary.validate(decoded.parts)) {
                is MmsDecodeBoundary.Result.Accepted -> Result.Accepted(validated.parts)
                is MmsDecodeBoundary.Result.Rejected -> Result.Rejected(validated.reason)
            }
        }
    }

    private const val MAX_PDU_BYTES = 17 * 1024 * 1024
    private const val MAX_REASON_CHARS = 80
}

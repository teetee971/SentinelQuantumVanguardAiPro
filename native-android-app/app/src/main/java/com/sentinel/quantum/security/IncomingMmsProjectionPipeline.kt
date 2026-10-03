package com.sentinel.quantum.security

/**
 * Pure preparation pipeline for canonical incoming MMS provider projection.
 *
 * Raw bytes cross three independent fail-closed boundaries before Android provider mutation:
 * envelope grammar -> body decoder/safety policy -> provider projection plan. A failure at any
 * stage leaves the PDU in the private/quarantine path and never manufactures canonical state.
 */
internal object IncomingMmsProjectionPipeline {
    sealed interface Result {
        data class Ready(
            val plan: IncomingMmsProjectionPlan.Plan,
            val safeParts: List<MmsDecodeBoundary.SafePart>
        ) : Result

        data class Quarantined(val reason: String) : Result
    }

    fun prepare(
        pdu: ByteArray,
        digestHex: String,
        subscriptionId: Int
    ): Result {
        val envelope = when (val parsed = MmsRetrieveEnvelopeParser.parse(pdu)) {
            is MmsRetrieveEnvelopeParser.Result.Accepted -> parsed.envelope
            is MmsRetrieveEnvelopeParser.Result.Rejected ->
                return Result.Quarantined("ENVELOPE:${parsed.reason.take(MAX_REASON_CHARS)}")
        }

        val decoded = try {
            SentinelMmsPduDecoder.decodeRetrieveBody(pdu.copyOf(), envelope)
        } catch (_: Exception) {
            return Result.Quarantined("BODY_DECODER_FAILED")
        }
        val safeParts = when (decoded) {
            is MmsPduDecoder.DecodeResult.Rejected ->
                return Result.Quarantined("BODY:${decoded.reason.take(MAX_REASON_CHARS)}")
            is MmsPduDecoder.DecodeResult.Decoded -> when (
                val validated = MmsDecodeBoundary.validate(decoded.parts)
            ) {
                is MmsDecodeBoundary.Result.Rejected ->
                    return Result.Quarantined("SAFETY:${validated.reason.take(MAX_REASON_CHARS)}")
                is MmsDecodeBoundary.Result.Accepted -> validated.parts
            }
        }

        return when (
            val planned = IncomingMmsProjectionPlan.build(
                digestHex = digestHex,
                envelope = envelope,
                safeParts = safeParts,
                subscriptionId = subscriptionId
            )
        ) {
            is IncomingMmsProjectionPlan.Result.Quarantined ->
                Result.Quarantined("PLAN:${planned.reason.take(MAX_REASON_CHARS)}")
            is IncomingMmsProjectionPlan.Result.Ready ->
                Result.Ready(planned.plan, safeParts)
        }
    }

    private const val MAX_REASON_CHARS = 96
}

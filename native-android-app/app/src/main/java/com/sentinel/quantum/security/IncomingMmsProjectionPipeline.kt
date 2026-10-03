package com.sentinel.quantum.security

/**
 * Pure preparation pipeline for canonical incoming MMS provider projection.
 *
 * Raw bytes cross independent fail-closed boundaries before Android provider mutation:
 * envelope grammar -> related-presentation metadata -> body decoder/safety policy -> provider plan.
 * A failure at any stage leaves the PDU in the private/quarantine path and never manufactures
 * canonical state.
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

        val relatedPresentation = if (envelope.contentType == MULTIPART_RELATED) {
            when (val inspected = MmsRelatedPresentationInspector.inspect(pdu, envelope)) {
                is MmsRelatedPresentationInspector.Result.Ready -> inspected.metadata
                is MmsRelatedPresentationInspector.Result.Rejected ->
                    return Result.Quarantined("PRESENTATION:${inspected.reason.take(MAX_REASON_CHARS)}")
            }
        } else {
            null
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

        if (relatedPresentation != null) {
            val presentationError = MmsRelatedPresentationValidator.validate(
                safeParts,
                relatedPresentation
            )
            if (presentationError != null) {
                return Result.Quarantined("PRESENTATION:${presentationError.take(MAX_REASON_CHARS)}")
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
    private const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"
}

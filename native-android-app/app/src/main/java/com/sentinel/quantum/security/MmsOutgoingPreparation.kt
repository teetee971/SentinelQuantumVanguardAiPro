package com.sentinel.quantum.security

/** Pure preparation. Returns the only PDU and attachment bytes eligible for persistence/submission. */
internal object MmsOutgoingPreparation {
    sealed interface Result {
        data class Ready(val attachments: List<SentinelMmsSendPduComposer.Part>, val pdu: ByteArray) : Result
        data class Rejected(val reason: String) : Result
    }

    fun prepare(
        destination: String,
        transactionId: String,
        text: String,
        attachments: List<SentinelMmsSendPduComposer.Part>,
        maxPduBytes: Int,
        compressJpeg: (ByteArray, Int) -> MmsOutgoingJpegCompressor.Result = MmsOutgoingJpegCompressor::compress
    ): Result {
        if (maxPduBytes <= 0) return Result.Rejected("MMS_CARRIER_CONFIG_UNAVAILABLE")
        if (Thread.currentThread().isInterrupted) return Result.Rejected("MMS_PREPARATION_INTERRUPTED")
        // Validate bounds before copying any caller-owned arrays.
        val initial = SentinelMmsSendPduComposer.compose(destination, transactionId, text, attachments, maxPduBytes)
        if (initial is SentinelMmsSendPduComposer.Result.Rejected && initial.reason != "MMS_CARRIER_SIZE_EXCEEDED") {
            return Result.Rejected(initial.reason)
        }
        try {
            val parts = attachments.map { it.copy(payload = it.payload.copyOf()) }.toMutableList()
            val jpegIndices = parts.indices.filter { parts[it].mimeType == "image/jpeg" }
            val budgets = IntArray(parts.size) { parts[it].payload.size }
            if (initial is SentinelMmsSendPduComposer.Result.Rejected) {
                if (jpegIndices.isEmpty()) return Result.Rejected(initial.reason)
                val originalSize = SentinelMmsSendPduComposer.measuredSize(destination, transactionId, text, parts)
                    ?: return Result.Rejected("PDU_COMPOSITION_FAILED")
                val payloadSize = parts.sumOf { it.payload.size.toLong() }
                val fixedBytes = parts.filter { it.mimeType != "image/jpeg" }.sumOf { it.payload.size.toLong() }
                // Original header lengths upper-bound the final headers: JPEG payloads only shrink.
                var available = maxPduBytes.toLong() - (originalSize.toLong() - payloadSize) - fixedBytes
                if (available < jpegIndices.size) return Result.Rejected("MMS_CARRIER_SIZE_EXCEEDED")
                val sorted = jpegIndices.sortedWith(compareBy<Int> { parts[it].payload.size }.thenBy { it })
                for ((position, index) in sorted.withIndex()) {
                    val share = available / (sorted.size - position)
                    budgets[index] = minOf(parts[index].payload.size.toLong(), share).toInt()
                    available -= budgets[index]
                }
            }
            // One call per JPEG: validation-only when it already fits, otherwise one bounded transcode.
            for (index in jpegIndices) {
                if (Thread.currentThread().isInterrupted) return Result.Rejected("MMS_PREPARATION_INTERRUPTED")
                val encoded = try { compressJpeg(parts[index].payload, budgets[index]) }
                    catch (_: RuntimeException) { return Result.Rejected("MMS_JPEG_COMPRESSION_FAILED") }
                when (encoded) {
                    is MmsOutgoingJpegCompressor.Result.Rejected -> return Result.Rejected("MMS_JPEG_${encoded.reason}")
                    is MmsOutgoingJpegCompressor.Result.Compressed -> {
                        if (encoded.bytes.isEmpty() || encoded.bytes.size > budgets[index]) return Result.Rejected("MMS_JPEG_BUDGET_EXCEEDED")
                        parts[index] = parts[index].copy(payload = encoded.bytes)
                    }
                }
            }
            return when (val final = SentinelMmsSendPduComposer.compose(destination, transactionId, text, parts, maxPduBytes)) {
                is SentinelMmsSendPduComposer.Result.Rejected -> Result.Rejected(final.reason)
                is SentinelMmsSendPduComposer.Result.Composed -> Result.Ready(parts.toList(), final.pdu)
            }
        } catch (_: OutOfMemoryError) {
            return Result.Rejected("MMS_JPEG_MEMORY_LIMIT")
        }
    }
}

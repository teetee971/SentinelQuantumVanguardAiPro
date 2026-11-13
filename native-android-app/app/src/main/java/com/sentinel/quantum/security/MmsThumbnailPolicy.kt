package com.sentinel.quantum.security

internal object MmsThumbnailPolicy {
    const val MAX_COMPRESSED_BYTES = 8 * 1024 * 1024
    const val MAX_SOURCE_PIXELS = 20_000_000L
    const val MAX_SOURCE_DIMENSION = 16_384
    const val MAX_THUMBNAIL_PIXELS = 1_048_576L

    data class PartKey(
        val providerMessageId: Long,
        val threadId: Long,
        val partId: Long
    )

    fun validKey(key: PartKey): Boolean =
        key.providerMessageId > 0L &&
            key.threadId > 0L &&
            key.partId > 0L

    fun validSource(width: Int, height: Int): Boolean =
        width > 0 &&
            height > 0 &&
            width <= MAX_SOURCE_DIMENSION &&
            height <= MAX_SOURCE_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS

    fun sampleSize(
        width: Int,
        height: Int,
        targetDimension: Int
    ): Int? {
        if (!validSource(width, height)) return null
        if (targetDimension !in 64..1024) return null

        var sample = 1
        while (
            width / sample > targetDimension ||
            height / sample > targetDimension ||
            (width / sample).toLong() *
                (height / sample).toLong() > MAX_THUMBNAIL_PIXELS
        ) {
            if (sample >= 1 shl 15) return null
            sample *= 2
        }
        return sample
    }
}

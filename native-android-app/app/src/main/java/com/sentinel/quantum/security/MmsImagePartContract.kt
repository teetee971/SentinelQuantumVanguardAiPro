package com.sentinel.quantum.security

internal object MmsImagePartContract {

    private val supportedTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    data class MessageIdentity(
        val syntheticMessageId: Long,
        val providerMessageId: Long,
        val threadId: Long
    )

    data class PartMetadata(
        val partId: Long,
        val parentMessageId: Long,
        val mimeType: String?
    )

    // /<message>/part is a collection route; a single part uses /part/<id>.
    // The URI alone does not bind a parent: metadata validation below remains mandatory.
    fun partContentUri(key: MmsThumbnailPolicy.PartKey): String? =
        if (MmsThumbnailPolicy.validKey(key))
            "content://mms/part/${key.partId}"
        else null

    fun samePartSnapshot(message: MessageIdentity, expected: PartMetadata, current: PartMetadata?): Boolean {
        val expectedKey = acceptPart(message, expected) ?: return false
        val observed = current ?: return false
        return acceptPart(message, observed) == expectedKey &&
            expected.mimeType?.trim()?.lowercase() == observed.mimeType?.trim()?.lowercase()
    }

    fun resolveMessage(
        syntheticMessageId: Long,
        threadId: Long
    ): MessageIdentity? {
        if (threadId <= 0L) return null

        val providerId =
            UnifiedProviderMessage.decodeMmsId(
                syntheticMessageId
            ) ?: return null

        return MessageIdentity(
            syntheticMessageId = syntheticMessageId,
            providerMessageId = providerId,
            threadId = threadId
        )
    }

    fun acceptPart(
        message: MessageIdentity,
        part: PartMetadata
    ): MmsThumbnailPolicy.PartKey? {
        if (
            part.parentMessageId != message.providerMessageId
        ) return null

        val mime = part.mimeType
            ?.trim()
            ?.lowercase()
            ?: return null

        if (mime !in supportedTypes) return null

        val key = MmsThumbnailPolicy.PartKey(
            providerMessageId = message.providerMessageId,
            threadId = message.threadId,
            partId = part.partId
        )

        return key.takeIf(MmsThumbnailPolicy::validKey)
    }
}

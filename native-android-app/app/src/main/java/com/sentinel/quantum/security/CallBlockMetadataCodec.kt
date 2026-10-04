package com.sentinel.quantum.security

/** Version-tolerant wire codec for exact-rule metadata persisted beside irreversible fingerprints. */
internal object CallBlockMetadataCodec {
    fun encode(entry: CallBlockMetadata.Entry): String = listOf(
        entry.fingerprint,
        entry.createdAtEpochMs.toString(),
        entry.expiresAtEpochMs?.toString().orEmpty(),
        entry.origin.name,
        entry.canonicalization.name,
        entry.reason.replace("|", " ")
    ).joinToString("|")

    fun decode(encoded: String): CallBlockMetadata.Entry? {
        val parts = encoded.split("|", limit = 6)
        if (parts.size !in 5..6 || parts[0].isBlank()) return null
        val created = parts[1].toLongOrNull() ?: return null
        val expires = if (parts[2].isEmpty()) null else parts[2].toLongOrNull() ?: return null
        val origin = runCatching { CallBlockMetadata.Origin.valueOf(parts[3]) }.getOrNull() ?: return null
        val canonicalization = if (parts.size == 6) {
            runCatching { CallBlockMetadata.Canonicalization.valueOf(parts[4]) }.getOrNull()
                ?: return null
        } else {
            CallBlockMetadata.Canonicalization.LEGACY_UNSPECIFIED
        }
        val reason = if (parts.size == 6) parts[5] else parts[4]
        return CallBlockMetadata.Entry(
            fingerprint = parts[0],
            reason = reason,
            createdAtEpochMs = created,
            expiresAtEpochMs = expires,
            origin = origin,
            canonicalization = canonicalization
        )
    }
}

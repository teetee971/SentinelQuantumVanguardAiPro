package com.sentinel.quantum.security

/**
 * Pure durability transitions for an incoming MMS recovery journal.
 *
 * Schema 1 stays readable. A SHA is optional. These decisions never delete a record.
 */
internal object MmsDownloadJournalMutation {
    enum class DigestCommit {
        APPLIED,
        IDENTICAL_REPLAY,
        CONFLICTING_DIGEST,
        IDENTITY_MISMATCH,
        RECORD_ABSENT,
        INVALID_DIGEST
    }

    data class Snapshot(
        val fileName: String,
        val subscriptionId: Int,
        val requestedAtMs: Long,
        val digestHex: String? = null,
        val privateMissCount: Int = 0
    )

    fun decideDigest(
        current: Snapshot?,
        expectedFileName: String,
        expectedSubscriptionId: Int,
        expectedRequestedAtMs: Long,
        digestHex: String
    ): DigestCommit {
        val normalized = digestHex.lowercase()
        if (IncomingMmsIdentity.persistedFileName(normalized) == null) return DigestCommit.INVALID_DIGEST
        val record = current ?: return DigestCommit.RECORD_ABSENT
        if (
            record.fileName != expectedFileName ||
            record.subscriptionId != expectedSubscriptionId ||
            record.requestedAtMs != expectedRequestedAtMs
        ) {
            return DigestCommit.IDENTITY_MISMATCH
        }
        val existing = record.digestHex?.lowercase()
        return when {
            existing == null -> DigestCommit.APPLIED
            existing == normalized -> DigestCommit.IDENTICAL_REPLAY
            else -> DigestCommit.CONFLICTING_DIGEST
        }
    }

    /**
     * Decodes a schema-1 journal value. Extra keys are ignored. A missing SHA stays null.
     * Returns null only when the payload cannot be trusted as this transaction's identity.
     */
    fun decodeSchema1(
        fileName: String,
        raw: String,
        fileNameAcceptable: Boolean,
        subscriptionAcceptable: (Int) -> Boolean
    ): Snapshot? {
        if (!fileNameAcceptable || raw.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = org.json.JSONObject(raw)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val subscriptionId = json.getInt("subscription_id")
            val requestedAtMs = json.getLong("requested_at_ms")
            if (!subscriptionAcceptable(subscriptionId) || requestedAtMs < 0L) return@runCatching null
            val digest = json.optString("sha256", "")
                .lowercase()
                .takeIf { IncomingMmsIdentity.persistedFileName(it) != null }
            Snapshot(
                fileName = fileName,
                subscriptionId = subscriptionId,
                requestedAtMs = requestedAtMs,
                digestHex = digest,
                privateMissCount = json.optInt("private_miss_count", 0).coerceAtLeast(0)
            )
        }.getOrNull()
    }

    fun encode(snapshot: Snapshot): String =
        org.json.JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("subscription_id", snapshot.subscriptionId)
            .put("requested_at_ms", snapshot.requestedAtMs)
            .apply {
                snapshot.digestHex?.let { put("sha256", it) }
                if (snapshot.privateMissCount > 0) put("private_miss_count", snapshot.privateMissCount)
            }
            .toString()

    const val SCHEMA_VERSION = 1
    const val MAX_ENCODED_CHARS = 512
}

/**
 * What to do when the staging file is already gone.
 * Neither historical journals nor a missing private copy are deleted here.
 */
internal object MmsDownloadMissingCopyPolicy {
    enum class Disposition {
        CONTINUE_PRIVATE,
        RETRY_INCONSISTENT,
        DIAGNOSE_INCONSISTENT,
        RETRY_HISTORICAL,
        DIAGNOSE_HISTORICAL
    }

    const val OBSERVATION_LIMIT = 8

    fun disposition(
        digestPresent: Boolean,
        privateReadable: Boolean,
        observations: Int,
        limit: Int = OBSERVATION_LIMIT
    ): Disposition {
        if (digestPresent && privateReadable) return Disposition.CONTINUE_PRIVATE
        val exhausted = observations >= limit
        return if (digestPresent) {
            if (exhausted) Disposition.DIAGNOSE_INCONSISTENT else Disposition.RETRY_INCONSISTENT
        } else {
            if (exhausted) Disposition.DIAGNOSE_HISTORICAL else Disposition.RETRY_HISTORICAL
        }
    }
}

/** Staging may be unlinked only after the private copy was reread and the journal accepted that SHA. */
internal object MmsDownloadDurability {
    fun stagingDeletable(privateRereadMatches: Boolean, journalAccepted: Boolean): Boolean =
        privateRereadMatches && journalAccepted

    fun roleRevocationDeletesStaging(): Boolean = false
}

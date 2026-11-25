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
        INVALID_DIGEST,
        TRANSPORT_FAILED
    }

    data class Snapshot(
        val fileName: String,
        val subscriptionId: Int,
        val requestedAtMs: Long,
        val digestHex: String? = null,
        val privateMissCount: Int = 0,
        val transportFailed: Boolean = false
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
        if (record.transportFailed) return DigestCommit.TRANSPORT_FAILED
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
                privateMissCount = json.optInt("private_miss_count", 0).coerceAtLeast(0),
                transportFailed = digest == null && json.optBoolean("transport_failed", false)
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
                if (snapshot.transportFailed && snapshot.digestHex == null) put("transport_failed", true)
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

    /**
     * A missing or undecodable journal is not proof that the staging file is empty.
     * Bytes stay. Only a confirmed absence of staging bytes may end the recovery.
     */
    fun outcomeWhenJournalUnreadable(stagingBytesPresent: Boolean): MmsDownloadRecovery.Outcome =
        if (stagingBytesPresent) MmsDownloadRecovery.Outcome.DIAGNOSE
        else MmsDownloadRecovery.Outcome.TERMINAL
}

/**
 * Two identical observations, separated by monotonic elapsed time.
 * Wall-clock steps do not make a file stable and do not expire it.
 */
internal object MmsStagingStability {
    fun ready(
        previousSize: Long?,
        previousModifiedAtMs: Long?,
        previousElapsedMs: Long?,
        size: Long,
        modifiedAtMs: Long,
        elapsedMs: Long,
        requiredElapsedMs: Long = MmsDownloadRecovery.STABLE_FILE_GRACE_MS
    ): Boolean {
        if (size <= 0L || modifiedAtMs <= 0L || requiredElapsedMs <= 0L) return false
        if (previousSize == null || previousModifiedAtMs == null || previousElapsedMs == null) return false
        if (previousSize != size || previousModifiedAtMs != modifiedAtMs) return false
        if (elapsedMs < previousElapsedMs) return false
        return elapsedMs - previousElapsedMs >= requiredElapsedMs
    }

    fun encode(size: Long, modifiedAtMs: Long, elapsedMs: Long): String =
        "$size $modifiedAtMs $elapsedMs"

    fun decode(raw: String): Triple<Long, Long, Long>? {
        val parts = raw.trim().split(' ')
        if (parts.size != 3) return null
        val size = parts[0].toLongOrNull() ?: return null
        val modifiedAtMs = parts[1].toLongOrNull() ?: return null
        val elapsedMs = parts[2].toLongOrNull() ?: return null
        if (size < 0L || modifiedAtMs < 0L || elapsedMs < 0L) return null
        return Triple(size, modifiedAtMs, elapsedMs)
    }
}

/** Undecodable rows and diagnosed or already-failed rows must not block a new download. */
internal object MmsDownloadJournalAdmission {
    fun countsTowardCap(
        decodable: Boolean,
        privateMissCount: Int,
        transportFailed: Boolean,
        diagnosisLimit: Int = MmsDownloadMissingCopyPolicy.OBSERVATION_LIMIT
    ): Boolean = decodable && !transportFailed && privateMissCount < diagnosisLimit
}

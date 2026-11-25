package com.sentinel.quantum.security

/**
 * Open policy for the V25 Room history. A matching identity keeps every row.
 * A different identity or an explicit destructive request leaves the rows untouched.
 */
internal object SentinelLocalDatabasePreservation {
    const val IDENTITY_HASH = "21617aeab12bfb29ee9bec17bc238d62"
    const val SCHEMA_VERSION = 1
    const val V25_DECISION_ROWS = 29

    data class DecisionRow(val id: Long, val occurredAtMs: Long, val action: String)

    enum class OpenStatus { PRESERVED, REFUSED_WITHOUT_DESTRUCTION }

    data class OpenOutcome(val status: OpenStatus, val rows: List<DecisionRow>)

    fun open(
        installedIdentity: String?,
        installedVersion: Int,
        rows: List<DecisionRow>,
        destructiveFallbackRequested: Boolean = false
    ): OpenOutcome {
        val mismatch = installedIdentity != null &&
            (installedIdentity != IDENTITY_HASH || installedVersion != SCHEMA_VERSION)
        if (destructiveFallbackRequested || mismatch) {
            return OpenOutcome(OpenStatus.REFUSED_WITHOUT_DESTRUCTION, rows.toList())
        }
        return OpenOutcome(OpenStatus.PRESERVED, rows.toList())
    }

    fun assertNonDestructive(destructiveFallbackRequested: Boolean) {
        check(!destructiveFallbackRequested) { "DESTRUCTIVE_MIGRATION_FORBIDDEN" }
    }
}

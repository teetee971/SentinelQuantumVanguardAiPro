package com.sentinel.quantum.security

/**
 * Open policy for the V25 Room history. A matching identity keeps every row.
 * A different identity or an explicit destructive request leaves the rows untouched.
 */
internal object SentinelLocalDatabasePreservation {
    const val IDENTITY_HASH = "21617aeab12bfb29ee9bec17bc238d62"
    const val SCHEMA_VERSION = 1
    const val V25_DECISION_ROWS = 29
    const val INSTALLED_PACKAGE = "com.sentinel.quantum"
    const val VALIDATION_PACKAGE = "com.sentinel.quantum.v1validation"

    /**
     * Instrumentation may start on the side-by-side recette package, or on a
     * throwaway install that does not already have a database. An existing
     * database on the installed package is allowed only when every row is the
     * synthetic fixture. Unreadable is refused. This does not delete anything.
     */
    fun instrumentationMayStart(
        packageName: String,
        databaseFilePresent: Boolean,
        syntheticFixture: Boolean?
    ): Boolean {
        if (packageName == VALIDATION_PACKAGE) return true
        if (packageName != INSTALLED_PACKAGE) return false
        if (!databaseFilePresent) return true
        return syntheticFixture == true
    }

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

    /**
     * Decision used by the real Room open. A missing file may be created.
     * An existing file opens only when its Room identity and user version match V25.
     * An unreadable file, a different identity, or a destructive request is refused.
     * Refusal must happen before Room is built, and must not delete the file.
     */
    fun allowOpen(
        filePresent: Boolean,
        identityHash: String?,
        userVersion: Int,
        destructiveFallbackRequested: Boolean = false
    ): Boolean {
        if (destructiveFallbackRequested) return false
        if (!filePresent) return true
        return identityHash == IDENTITY_HASH && userVersion == SCHEMA_VERSION
    }

    fun assertNonDestructive(destructiveFallbackRequested: Boolean) {
        check(!destructiveFallbackRequested) { "DESTRUCTIVE_MIGRATION_FORBIDDEN" }
    }
}

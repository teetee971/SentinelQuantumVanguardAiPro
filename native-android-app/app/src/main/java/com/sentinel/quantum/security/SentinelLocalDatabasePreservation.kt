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
     * Only the side-by-side recette package may be instrumented.
     * The installed package is refused even when its database is absent, synthetic,
     * unreadable, or accompanied by WAL/SHM files. Database state never grants access.
     */
    @Suppress("UNUSED_PARAMETER")
    fun instrumentationMayStart(
        packageName: String,
        databaseFilePresent: Boolean = false,
        syntheticFixture: Boolean? = null,
        databaseUnreadable: Boolean = false,
        walOrShmPresent: Boolean = false
    ): Boolean = packageName == VALIDATION_PACKAGE

    /** The test APK must be a distinct package. Same identity as the target or the installed app is refused. */
    fun instrumentationPackagesAreSeparated(targetPackage: String, testPackage: String): Boolean {
        if (!instrumentationMayStart(targetPackage)) return false
        return testPackage == "$VALIDATION_PACKAGE.test" &&
            testPackage != targetPackage &&
            testPackage != INSTALLED_PACKAGE
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

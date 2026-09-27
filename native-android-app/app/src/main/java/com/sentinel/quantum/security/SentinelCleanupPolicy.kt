package com.sentinel.quantum.security

/**
 * Truthful cleanup contract. Discovery, user consent, execution and verification
 * are separate states; merely offering an action can never become CLEANED.
 */
object SentinelCleanupPolicy {
    enum class Scope {
        SENTINEL_CACHE,
        SENTINEL_TEMPORARY_FILE,
        USER_SELECTED_FILE,
        EXTERNAL_APP_PRIVATE_DATA
    }

    enum class ActionState {
        DISCOVERED,
        USER_CONFIRMATION_REQUIRED,
        EXECUTABLE,
        EXECUTED_UNVERIFIED,
        VERIFIED_REMOVED,
        NOT_ACCESSIBLE,
        FAILED
    }

    data class Candidate(
        val stableId: String,
        val scope: Scope,
        val displayName: String,
        val bytes: Long?,
        val state: ActionState
    ) {
        init {
            require(stableId.isNotBlank())
            require(displayName.isNotBlank())
            require(bytes == null || bytes >= 0L)
        }

        val reclaimableBytes: Long?
            get() = if (state == ActionState.VERIFIED_REMOVED) 0L else bytes
    }

    data class Result(
        val before: Candidate,
        val after: Candidate
    ) {
        val verifiedFreedBytes: Long?
            get() {
                if (after.state != ActionState.VERIFIED_REMOVED) return null
                val beforeBytes = before.bytes ?: return null
                val afterBytes = after.bytes ?: 0L
                return (beforeBytes - afterBytes).coerceAtLeast(0L)
            }

        val isVerifiedCleaned: Boolean
            get() = after.state == ActionState.VERIFIED_REMOVED
    }

    fun canExecuteDirectly(candidate: Candidate): Boolean =
        candidate.scope == Scope.SENTINEL_CACHE ||
            candidate.scope == Scope.SENTINEL_TEMPORARY_FILE

    fun requiresUserSelection(candidate: Candidate): Boolean =
        candidate.scope == Scope.USER_SELECTED_FILE

    fun isUnsupportedDirectCleanup(candidate: Candidate): Boolean =
        candidate.scope == Scope.EXTERNAL_APP_PRIVATE_DATA
}

package com.sentinel.quantum.security

/**
 * Pure policy for app-usage evidence. Missing usage access is never converted
 * into an "unused app" finding.
 */
object SentinelAppUsagePolicy {
    const val STALE_AFTER_DAYS = 90L
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    enum class Observation {
        RECENTLY_USED,
        STALE_USAGE_CONFIRMED,
        NEVER_USED_SINCE_OBSERVATION_START,
        NOT_OBSERVABLE,
        INVALID
    }

    data class Snapshot(
        val packageName: String,
        val usageAccessAvailable: Boolean,
        val lastUsedEpochMillis: Long?,
        val observationStartedEpochMillis: Long?,
        val observedAtEpochMillis: Long
    ) {
        init {
            require(packageName.isNotBlank())
            require(observedAtEpochMillis >= 0L)
        }
    }

    fun evaluate(snapshot: Snapshot): Observation {
        if (!snapshot.usageAccessAvailable) return Observation.NOT_OBSERVABLE

        val started = snapshot.observationStartedEpochMillis ?: return Observation.INVALID
        if (started < 0L || started > snapshot.observedAtEpochMillis) return Observation.INVALID

        val lastUsed = snapshot.lastUsedEpochMillis
            ?: return if (snapshot.observedAtEpochMillis - started >= STALE_AFTER_DAYS * DAY_MILLIS) {
                Observation.NEVER_USED_SINCE_OBSERVATION_START
            } else {
                Observation.NOT_OBSERVABLE
            }

        if (lastUsed < 0L || lastUsed > snapshot.observedAtEpochMillis || lastUsed < started) {
            return Observation.INVALID
        }

        return if (snapshot.observedAtEpochMillis - lastUsed >= STALE_AFTER_DAYS * DAY_MILLIS) {
            Observation.STALE_USAGE_CONFIRMED
        } else {
            Observation.RECENTLY_USED
        }
    }
}

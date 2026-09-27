package com.sentinel.quantum.security

/**
 * Maps usage evidence to reversible/user-controlled remediation.
 */
object SentinelAppHygieneRemediation {
    enum class Action {
        NONE,
        OFFER_REVIEW,
        OFFER_SYSTEM_UNINSTALL_FLOW,
        REQUEST_USAGE_ACCESS
    }

    fun actionFor(
        observation: SentinelAppUsagePolicy.Observation,
        freshness: SentinelAppUsageFreshness.State = SentinelAppUsageFreshness.State.FRESH
    ): Action {
        if (freshness != SentinelAppUsageFreshness.State.FRESH) return Action.OFFER_REVIEW
        return when (observation) {
        SentinelAppUsagePolicy.Observation.RECENTLY_USED -> Action.NONE
        SentinelAppUsagePolicy.Observation.STALE_USAGE_CONFIRMED,
        SentinelAppUsagePolicy.Observation.NEVER_USED_SINCE_OBSERVATION_START ->
            Action.OFFER_SYSTEM_UNINSTALL_FLOW
        SentinelAppUsagePolicy.Observation.NOT_OBSERVABLE -> Action.REQUEST_USAGE_ACCESS
        SentinelAppUsagePolicy.Observation.INVALID -> Action.OFFER_REVIEW
        }
    }
}

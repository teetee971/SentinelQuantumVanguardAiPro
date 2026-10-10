package com.sentinel.quantum.security

/**
 * UI submission gate aligned with the fail-closed SMS/MMS sender subscription policy.
 *
 * This does not authorize sending by itself: the concrete sender remains the final enforcement
 * boundary. It prevents the composer from presenting an actionable send button when the active
 * SIM state still requires an explicit user choice.
 */
object SmsSubmitReadiness {
    fun canSubmit(
        activationCanSend: Boolean,
        activeSubscriptionIds: Collection<Int>,
        selectedSubscriptionId: Int?,
        destinationPresent: Boolean,
        bodyPresent: Boolean,
        submissionInFlight: Boolean = false
    ): Boolean {
        if (submissionInFlight || !activationCanSend || !destinationPresent || !bodyPresent) return false
        return SmsSubscriptionSelectionPolicy.select(
            activeSubscriptionIds = activeSubscriptionIds.toSet(),
            requestedSubscriptionId = selectedSubscriptionId,
            defaultSubscriptionId = null
        ).accepted
    }
}

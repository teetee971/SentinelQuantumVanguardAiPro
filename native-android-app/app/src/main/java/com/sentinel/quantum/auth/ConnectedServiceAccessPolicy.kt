package com.sentinel.quantum.auth

/**
 * Local presentation gate for optional connected features.
 *
 * This policy never authorizes server requests: the backend must validate
 * token signature, issuer, audience, expiry, email verification and entitlements.
 * Local phone/SMS/emergency functionality must not depend on this gate.
 */
internal enum class ConnectedAccessDecision {
    SIGN_IN_REQUIRED,
    EMAIL_VERIFICATION_REQUIRED,
    SESSION_REVALIDATION_REQUIRED,
    ENTITLEMENT_REQUIRED,
    ALLOW_PRESENTATION
}

internal data class ConnectedAccessSnapshot(
    val authenticated: Boolean,
    val emailVerified: Boolean,
    val sessionValid: Boolean,
    val entitlementGranted: Boolean,
    val premiumFeature: Boolean = false
)

internal object ConnectedServiceAccessPolicy {
    fun evaluate(snapshot: ConnectedAccessSnapshot): ConnectedAccessDecision = when {
        !snapshot.authenticated -> ConnectedAccessDecision.SIGN_IN_REQUIRED
        !snapshot.sessionValid -> ConnectedAccessDecision.SESSION_REVALIDATION_REQUIRED
        !snapshot.emailVerified -> ConnectedAccessDecision.EMAIL_VERIFICATION_REQUIRED
        snapshot.premiumFeature && !snapshot.entitlementGranted -> ConnectedAccessDecision.ENTITLEMENT_REQUIRED
        else -> ConnectedAccessDecision.ALLOW_PRESENTATION
    }
}

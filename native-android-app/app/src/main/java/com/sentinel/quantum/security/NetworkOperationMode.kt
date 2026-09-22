package com.sentinel.quantum.security

/**
 * Execution boundary for local network features.
 *
 * DEFENSIVE_LOCAL is the fail-closed default and is suitable for normal inventory,
 * local risk scoring and user-initiated defensive discovery.
 *
 * AUTHORIZED_LAB is reserved for bounded test sessions against equipment the user
 * owns or is explicitly authorized to administer. Selecting this mode does not by
 * itself authorize any active or intercepting operation: every such operation must
 * still pass [NetworkOperationPolicy] with a current authorization context.
 */
enum class NetworkOperationMode {
    DEFENSIVE_LOCAL,
    AUTHORIZED_LAB
}

data class NetworkAuthorizationContext(
    val ownsOrAdministersTarget: Boolean,
    val explicitTestSession: Boolean,
    val localOrIsolatedScope: Boolean
)

/**
 * Central fail-closed policy gate for any future active network-test capability.
 *
 * This object deliberately contains no scanner, packet-capture, interception or
 * attack implementation. It only defines the authorization predicates that those
 * implementations must satisfy before they can be exposed.
 */
object NetworkOperationPolicy {
    fun permitsDefensiveDiscovery(mode: NetworkOperationMode): Boolean = true

    fun permitsActiveProbe(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext
    ): Boolean =
        mode == NetworkOperationMode.AUTHORIZED_LAB &&
            authorization.ownsOrAdministersTarget &&
            authorization.explicitTestSession &&
            authorization.localOrIsolatedScope

    fun permitsTrafficInspection(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext
    ): Boolean = permitsActiveProbe(mode, authorization)

    fun permitsAdversarialSimulation(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext
    ): Boolean = permitsActiveProbe(mode, authorization)

    fun permitsThirdPartyTargeting(
        mode: NetworkOperationMode,
        authorization: NetworkAuthorizationContext
    ): Boolean = false
}

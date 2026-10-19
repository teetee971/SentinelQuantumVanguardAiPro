package com.sentinel.quantum.security

/**
 * Push-to-talk readiness contract. No microphone or network session is started here.
 *
 * This is an integration foundation, not a functioning walkie-talkie transport.
 * Permission checks must be re-evaluated immediately before each actual transmission.
 */
internal enum class PttLinkState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

internal enum class PttTransmissionBlock {
    MICROPHONE_PERMISSION_MISSING,
    AUTHENTICATION_REQUIRED,
    GROUP_MEMBERSHIP_REQUIRED,
    TRANSPORT_UNAVAILABLE,
    DEVICE_BUSY
}

internal data class PttTransmissionInputs(
    val microphonePermissionGranted: Boolean,
    val authenticated: Boolean,
    val groupMembershipVerified: Boolean,
    val linkState: PttLinkState,
    val anotherTransmissionActive: Boolean
)

internal sealed interface PttTransmissionDecision {
    data object Allowed : PttTransmissionDecision
    data class Blocked(val reason: PttTransmissionBlock) : PttTransmissionDecision
}

internal object PttTransmissionPolicy {
    fun evaluate(inputs: PttTransmissionInputs): PttTransmissionDecision = when {
        !inputs.microphonePermissionGranted ->
            PttTransmissionDecision.Blocked(PttTransmissionBlock.MICROPHONE_PERMISSION_MISSING)
        !inputs.authenticated ->
            PttTransmissionDecision.Blocked(PttTransmissionBlock.AUTHENTICATION_REQUIRED)
        !inputs.groupMembershipVerified ->
            PttTransmissionDecision.Blocked(PttTransmissionBlock.GROUP_MEMBERSHIP_REQUIRED)
        inputs.linkState != PttLinkState.CONNECTED ->
            PttTransmissionDecision.Blocked(PttTransmissionBlock.TRANSPORT_UNAVAILABLE)
        inputs.anotherTransmissionActive ->
            PttTransmissionDecision.Blocked(PttTransmissionBlock.DEVICE_BUSY)
        else -> PttTransmissionDecision.Allowed
    }
}

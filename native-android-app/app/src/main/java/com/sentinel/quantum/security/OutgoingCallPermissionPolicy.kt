package com.sentinel.quantum.security

/**
 * Fail-closed authority boundary for ordinary carrier/PSTN placement.
 *
 * TelecomManager.isOutgoingCallPermitted() cannot authorize a framework SIM on behalf of a
 * third-party/default dialer. Phone Core therefore accepts only a PhoneAccount that Android
 * marks CAPABILITY_SIM_SUBSCRIPTION and revalidates that capability immediately before placeCall().
 */
object OutgoingCallPermissionPolicy {
    enum class AccountAuthority { FRAMEWORK_SIM, UNVERIFIED }

    fun classify(frameworkSimCapability: Boolean): AccountAuthority =
        if (frameworkSimCapability) AccountAuthority.FRAMEWORK_SIM else AccountAuthority.UNVERIFIED

    fun mayPlacePstnCall(authority: AccountAuthority): Boolean =
        authority == AccountAuthority.FRAMEWORK_SIM
}

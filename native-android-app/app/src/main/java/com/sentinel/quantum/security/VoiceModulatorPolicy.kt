package com.sentinel.quantum.security

/**
 * Product and platform contract for the optional Voice Studio add-on.
 *
 * A third-party Android dialer does not get a public API for rewriting the microphone
 * uplink of a carrier/SIM call. Keep carrier calls fail-closed: the paid entitlement
 * can only unlock a future Sentinel-managed VoIP transport once that transport and
 * its DSP path have been physically validated.
 */
object VoiceModulatorPolicy {
    enum class Transport {
        LOCAL_PREVIEW,
        SENTINEL_MANAGED_VOIP,
        CARRIER_PSTN
    }

    enum class Entitlement {
        NOT_COMMERCIALIZED,
        INACTIVE,
        ACTIVE
    }

    enum class Availability {
        PREVIEW_AVAILABLE,
        READY,
        PURCHASE_REQUIRED,
        ENGINE_NOT_VALIDATED,
        PERMISSION_REQUIRED,
        UNSUPPORTED_BY_ANDROID
    }

    data class Input(
        val transport: Transport,
        val entitlement: Entitlement,
        val engineValidated: Boolean,
        val microphonePermissionGranted: Boolean
    )

    fun availability(input: Input): Availability = when (input.transport) {
        Transport.CARRIER_PSTN -> Availability.UNSUPPORTED_BY_ANDROID
        Transport.LOCAL_PREVIEW -> {
            if (input.microphonePermissionGranted) Availability.PREVIEW_AVAILABLE
            else Availability.PERMISSION_REQUIRED
        }
        Transport.SENTINEL_MANAGED_VOIP -> when {
            !input.engineValidated -> Availability.ENGINE_NOT_VALIDATED
            input.entitlement != Entitlement.ACTIVE -> Availability.PURCHASE_REQUIRED
            else -> Availability.READY
        }
    }

    fun canTransformLiveCall(input: Input): Boolean =
        availability(input) == Availability.READY
}

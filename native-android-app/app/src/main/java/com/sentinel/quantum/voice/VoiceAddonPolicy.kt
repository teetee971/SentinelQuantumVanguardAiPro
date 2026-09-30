package com.sentinel.quantum.voice

/**
 * Commercial and platform truth for the optional Voice Studio add-on.
 *
 * A normal third-party Android dialer does not own the carrier/SIM audio path, so Sentinel
 * never advertises or unlocks live carrier-call voice modulation. The paid entitlement may
 * only become sellable after Sentinel owns a VoIP media pipeline and that pipeline has passed
 * the required device/audio/privacy validation.
 */
object VoiceAddonPolicy {
    enum class Effect(val label: String, val pitch: Float) {
        NATURAL("Naturelle", 1.0f),
        DEEP("Grave", 0.72f),
        BRIGHT("Aiguë", 1.35f)
    }

    enum class LiveCallPath {
        CARRIER_SIM_UNSUPPORTED,
        SENTINEL_VOIP_NOT_CERTIFIED
    }

    data class CommercialState(
        val previewAvailable: Boolean,
        val paidCheckoutAllowed: Boolean,
        val liveCallPath: LiveCallPath,
        val customerLabel: String
    )

    fun currentState(): CommercialState = CommercialState(
        previewAvailable = true,
        paidCheckoutAllowed = false,
        liveCallPath = LiveCallPath.CARRIER_SIM_UNSUPPORTED,
        customerLabel = "Aperçu local disponible · add-on appels en préparation"
    )

    fun mayOfferPaidCheckout(
        ownsVoipMediaPipeline: Boolean,
        deviceAudioValidated: Boolean,
        privacyReviewPassed: Boolean
    ): Boolean = ownsVoipMediaPipeline && deviceAudioValidated && privacyReviewPassed
}

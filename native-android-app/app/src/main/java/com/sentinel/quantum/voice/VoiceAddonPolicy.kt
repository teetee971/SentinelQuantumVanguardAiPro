package com.sentinel.quantum.voice

/**
 * Commercial/platform truth for the Voice Studio add-on.
 *
 * Product requirement: live voice transformation MUST be present for Sentinel-controlled
 * VoIP calls before the add-on can ship. The DSP engine and outgoing-media processing
 * boundary are integrated in the app. What remains is the actual Sentinel VoIP/PSTN
 * transport plus device/audio/privacy certification.
 *
 * Android's public third-party dialer APIs do not expose a carrier/SIM media injection path,
 * so native SIM-call modulation stays fail-closed instead of being falsely advertised.
 */
object VoiceAddonPolicy {
    enum class Effect(val label: String, val pitch: Float) {
        NATURAL("Naturelle", 1.0f),
        DEEP("Grave", 0.72f),
        BRIGHT("Aiguë", 1.35f)
    }

    enum class LiveCallPath {
        CARRIER_SIM_BLOCKED_BY_ANDROID,
        SENTINEL_WEBRTC_CLIENT_INTEGRATED_SERVICE_PENDING,
        SENTINEL_VOIP_CERTIFIED
    }

    data class CommercialState(
        val previewAvailable: Boolean,
        val liveTransformRequired: Boolean,
        val liveTransformEngineIntegrated: Boolean,
        val webRtcClientIntegrated: Boolean,
        val paidCheckoutAllowed: Boolean,
        val liveCallPath: LiveCallPath,
        val customerLabel: String
    )

    fun currentState(): CommercialState = CommercialState(
        previewAvailable = true,
        liveTransformRequired = true,
        liveTransformEngineIntegrated = true,
        paidCheckoutAllowed = false,
        liveCallPath = LiveCallPath.SENTINEL_VOIP_ENGINE_INTEGRATED_TRANSPORT_PENDING,
        customerLabel = "Moteur temps réel intégré · transport d’appel Sentinel à finaliser"
    )

    fun mayOfferPaidCheckout(
        ownsVoipMediaPipeline: Boolean,
        liveTransformEngineIntegrated: Boolean,
        webRtcClientIntegrated: Boolean,
        callTransportValidated: Boolean,
        deviceAudioValidated: Boolean,
        privacyReviewPassed: Boolean
    ): Boolean =
        ownsVoipMediaPipeline &&
            liveTransformEngineIntegrated &&
            webRtcClientIntegrated &&
            callTransportValidated &&
            deviceAudioValidated &&
            privacyReviewPassed
}

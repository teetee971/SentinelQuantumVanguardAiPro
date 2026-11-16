package com.sentinel.quantum.voice

/**
 * Commercial/platform truth for the Voice Studio add-on.
 *
 * Product requirement: live voice transformation MUST be present for Sentinel-controlled
 * VoIP calls before the add-on can ship. The DSP engine and outgoing-media processing
 * boundary and low-level LiveKit transport are integrated in the app. What remains is a
 * user-reachable Sentinel call-session flow, authenticated ephemeral token issuance, deployed
 * LiveKit/signaling/PSTN service, and device/audio/privacy certification.
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
        val runtimeCallFlowIntegrated: Boolean,
        val paidCheckoutAllowed: Boolean,
        val liveCallPath: LiveCallPath,
        val customerLabel: String
    )

    fun currentState(): CommercialState = CommercialState(
        previewAvailable = true,
        liveTransformRequired = true,
        liveTransformEngineIntegrated = true,
        webRtcClientIntegrated = true,
        runtimeCallFlowIntegrated = false,
        paidCheckoutAllowed = false,
        liveCallPath = LiveCallPath.SENTINEL_WEBRTC_CLIENT_INTEGRATED_SERVICE_PENDING,
        customerLabel = "Transport WebRTC intégré · session d’appel non raccordée"
    )

    fun mayOfferPaidCheckout(
        ownsVoipMediaPipeline: Boolean,
        liveTransformEngineIntegrated: Boolean,
        webRtcClientIntegrated: Boolean,
        runtimeCallFlowIntegrated: Boolean,
        callTransportValidated: Boolean,
        deviceAudioValidated: Boolean,
        privacyReviewPassed: Boolean
    ): Boolean =
        ownsVoipMediaPipeline &&
            liveTransformEngineIntegrated &&
            webRtcClientIntegrated &&
            runtimeCallFlowIntegrated &&
            callTransportValidated &&
            deviceAudioValidated &&
            privacyReviewPassed
}

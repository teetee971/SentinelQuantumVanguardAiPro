package com.sentinel.quantum.security

/**
 * Truthful capability model for Sentinel Call Recorder.
 *
 * This model never equates microphone access with guaranteed two-party call capture.
 * The actual recorder must prove the captured channels on the current device before
 * presenting a recording as a complete call.
 */
object CallRecorderCapability {
    enum class CaptureLevel {
        UNAVAILABLE,
        MICROPHONE_ONLY,
        DEVICE_DEPENDENT_CALL_AUDIO,
        VERIFIED_TWO_PARTY
    }

    data class Inputs(
        val hasMicrophone: Boolean,
        val recordAudioGranted: Boolean,
        val holdsDialerRole: Boolean,
        val deviceStrategyAvailable: Boolean,
        val twoPartyProbePassed: Boolean
    )

    data class Result(
        val level: CaptureLevel,
        val canStartRecording: Boolean,
        val canClaimTwoPartyAudio: Boolean,
        val reason: String
    )

    fun evaluate(input: Inputs): Result {
        if (!input.hasMicrophone) return Result(
            CaptureLevel.UNAVAILABLE, false, false, "Microphone indisponible"
        )
        if (!input.recordAudioGranted) return Result(
            CaptureLevel.UNAVAILABLE, false, false, "Autorisation microphone requise"
        )
        if (!input.holdsDialerRole) return Result(
            CaptureLevel.MICROPHONE_ONLY, true, false, "Sentinel n'est pas le téléphone par défaut"
        )
        if (!input.deviceStrategyAvailable) return Result(
            CaptureLevel.MICROPHONE_ONLY, true, false, "Capture d'appel non validée sur cet appareil"
        )
        if (!input.twoPartyProbePassed) return Result(
            CaptureLevel.DEVICE_DEPENDENT_CALL_AUDIO, true, false, "Deux interlocuteurs non vérifiés"
        )
        return Result(
            CaptureLevel.VERIFIED_TWO_PARTY, true, true, "Capture des deux interlocuteurs vérifiée"
        )
    }
}

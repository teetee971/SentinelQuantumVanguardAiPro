package com.sentinel.quantum.security

/**
 * Truthful capability map for covert-device detection.
 *
 * Sentinel must distinguish signals that Android can observe directly from detections
 * that require the phone camera or dedicated RF hardware. A capability status is not
 * evidence that a nearby object is malicious.
 */
enum class CovertDetectionChannel {
    BLUETOOTH_TRACKER,
    WIRELESS_CAMERA_HINT,
    OPTICAL_LENS_SWEEP,
    RF_SPECTRUM
}

enum class CovertDetectionSupport {
    ACTIVE_LOCAL,
    ACTIVE_HEURISTIC,
    USER_CAMERA_REQUIRED,
    EXTERNAL_RF_HARDWARE_REQUIRED
}

data class CovertDetectionCapability(
    val channel: CovertDetectionChannel,
    val support: CovertDetectionSupport,
    val label: String,
    val limitation: String
)

object CovertDeviceDetectionCapabilities {
    val channels: List<CovertDetectionCapability> = listOf(
        CovertDetectionCapability(
            channel = CovertDetectionChannel.BLUETOOTH_TRACKER,
            support = CovertDetectionSupport.ACTIVE_LOCAL,
            label = "AirTags et traceurs Bluetooth",
            limitation = "Analyse locale des annonces BLE visibles. Un appareil silencieux, éteint ou non compatible BLE peut rester invisible."
        ),
        CovertDetectionCapability(
            channel = CovertDetectionChannel.WIRELESS_CAMERA_HINT,
            support = CovertDetectionSupport.ACTIVE_HEURISTIC,
            label = "Caméras et enregistreurs sans fil",
            limitation = "Indices Wi-Fi/Bluetooth uniquement. Un nom compatible avec une caméra n'est jamais une preuve de dispositif espion."
        ),
        CovertDetectionCapability(
            channel = CovertDetectionChannel.OPTICAL_LENS_SWEEP,
            support = CovertDetectionSupport.USER_CAMERA_REQUIRED,
            label = "Objectifs cachés — balayage optique",
            limitation = "Nécessite un module caméra explicite et une autorisation utilisateur. Le balayage optique n'est pas encore actif dans cette version."
        ),
        CovertDetectionCapability(
            channel = CovertDetectionChannel.RF_SPECTRUM,
            support = CovertDetectionSupport.EXTERNAL_RF_HARDWARE_REQUIRED,
            label = "Détecteur radiofréquences (RF)",
            limitation = "Android ne fournit pas de spectromètre RF large bande. Une sonde RF/SDR externe compatible est nécessaire."
        )
    )

    fun supportFor(channel: CovertDetectionChannel): CovertDetectionSupport =
        channels.first { it.channel == channel }.support
}

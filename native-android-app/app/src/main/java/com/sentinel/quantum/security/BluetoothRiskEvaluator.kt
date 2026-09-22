package com.sentinel.quantum.security

/**
 * Heuristiques locales pour qualifier un appareil Bluetooth environnant.
 *
 * L'évaluation est indicative : elle aide l'utilisateur à repérer un traceur inconnu,
 * sans prétendre identifier formellement un appareil.
 */
enum class BluetoothDeviceKind {
    AUDIO,
    PHONE,
    COMPUTER,
    WEARABLE,
    UNKNOWN
}

data class BluetoothRiskAssessment(
    val riskLevel: NetworkRiskLevel,
    val likelyTracker: Boolean,
    val likelyCameraOrRecorder: Boolean,
    val likelyBeacon: Boolean,
    val reasons: List<String>
)

object BluetoothRiskEvaluator {

    const val TRACKER_ADVICE =
        "Si vous trouvez un traceur Bluetooth inconnu, vérifiez vos effets personnels et contactez les autorités si besoin."

    private val trackerNameMarkers = listOf(
        "airtag",
        "smarttag",
        "smart tag",
        "tile",
        "chipolo",
        "pebblebee",
        "itag",
        "nut tracker",
        "cube tracker",
        "find my",
        "trackr",
        "gps tracker",
        "gps-tracker"
    )

    private val cameraOrRecorderNameMarkers = listOf(
        "hidden camera",
        "spy camera",
        "mini camera",
        "mini cam",
        "minicam",
        "wifi cam",
        "wifi camera",
        "ip cam",
        "ipcam",
        "nanny cam",
        "lookcam",
        "hdwificam",
        "v380",
        "voice recorder",
        "audio recorder",
        "dictaphone"
    )

    private val beaconNameMarkers = listOf(
        "ibeacon",
        "beacon",
        "estimote",
        "eddystone"
    )

    fun isLikelyTracker(deviceName: String?): Boolean =
        matchesAny(deviceName, trackerNameMarkers)

    fun isLikelyCameraOrRecorder(deviceName: String?): Boolean =
        matchesAny(deviceName, cameraOrRecorderNameMarkers)

    fun isLikelyBeacon(deviceName: String?): Boolean =
        matchesAny(deviceName, beaconNameMarkers)

    private fun matchesAny(deviceName: String?, markers: List<String>): Boolean {
        val normalized = deviceName?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return markers.any { marker -> normalized.contains(marker) }
    }

    fun evaluate(
        deviceName: String?,
        kind: BluetoothDeviceKind = BluetoothDeviceKind.UNKNOWN,
        bonded: Boolean = false
    ): BluetoothRiskAssessment {
        val likelyTracker = isLikelyTracker(deviceName)
        val likelyCameraOrRecorder = isLikelyCameraOrRecorder(deviceName)
        val likelyBeacon = isLikelyBeacon(deviceName)
        val namedDevice = !deviceName.isNullOrBlank()
        val reasons = mutableListOf<String>()

        if (likelyTracker) {
            reasons += "Nom compatible avec un traceur d'objet sans appairage."
            reasons += TRACKER_ADVICE
        }
        if (likelyCameraOrRecorder) {
            reasons += "Nom compatible avec une caméra ou un enregistreur sans fil. Ce signal est indicatif et ne prouve pas un dispositif espion."
        }
        if (likelyBeacon) {
            reasons += "Balise BLE potentielle détectée. Une balise peut être légitime ; vérifiez sa présence si elle est inattendue."
        }
        if (!namedDevice) reasons += "Appareil sans nom diffusé (adresse possiblement aléatoire)."
        if (bonded) reasons += "Appareil déjà appairé avec ce téléphone."

        val riskLevel = when {
            likelyTracker -> NetworkRiskLevel.HIGH
            likelyCameraOrRecorder || likelyBeacon -> NetworkRiskLevel.MEDIUM
            kind == BluetoothDeviceKind.UNKNOWN -> NetworkRiskLevel.MEDIUM
            !namedDevice -> NetworkRiskLevel.MEDIUM
            else -> NetworkRiskLevel.LOW
        }

        if (riskLevel == NetworkRiskLevel.MEDIUM && !likelyTracker) {
            reasons += "Type d'appareil inconnu à proximité : à surveiller s'il vous suit dans plusieurs lieux."
        }
        if (riskLevel == NetworkRiskLevel.LOW) {
            reasons += "Type d'appareil courant (téléphone, audio, ordinateur ou montre)."
        }

        return BluetoothRiskAssessment(
            riskLevel = riskLevel,
            likelyTracker = likelyTracker,
            likelyCameraOrRecorder = likelyCameraOrRecorder,
            likelyBeacon = likelyBeacon,
            reasons = reasons.toList()
        )
    }
}

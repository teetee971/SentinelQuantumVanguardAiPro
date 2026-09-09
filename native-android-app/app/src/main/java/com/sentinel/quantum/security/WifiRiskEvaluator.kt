package com.sentinel.quantum.security

/**
 * Heuristiques locales et défensives pour qualifier un réseau WiFi environnant.
 *
 * Aucune donnée n'est envoyée sur le réseau : l'évaluation est purement locale et
 * indicative (pédagogique). Elle ne prétend pas détecter un point d'accès malveillant
 * avec certitude.
 */
enum class WifiSecurityType {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA2_ENTERPRISE,
    WPA3,
    UNKNOWN
}

enum class WifiBand {
    BAND_2_4_GHZ,
    BAND_5_GHZ,
    BAND_6_GHZ,
    UNKNOWN
}

enum class NetworkRiskLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class WifiRiskAssessment(
    val securityType: WifiSecurityType,
    val riskLevel: NetworkRiskLevel,
    val captivePortalSuspected: Boolean,
    val reasons: List<String>
)

object WifiRiskEvaluator {

    const val HIDDEN_SSID_LABEL = "(unknown ssid)"

    const val OPEN_NETWORK_ADVICE =
        "Un réseau ouvert peut intercepter votre trafic. Évitez d'y saisir des identifiants sensibles."

    private const val VERY_STRONG_SIGNAL_DBM = -45

    private val genericSsids = setOf(
        "wifi",
        "wi-fi",
        "free wifi",
        "free_wifi",
        "freewifi",
        "public wifi",
        "public",
        "guest",
        "invite",
        "invités",
        "hotspot",
        "default",
        "linksys",
        "netgear",
        "dlink",
        "tp-link",
        "internet",
        "android ap",
        "androidap"
    )

    fun securityType(capabilities: String?): WifiSecurityType {
        val normalized = capabilities?.uppercase().orEmpty()
        return when {
            normalized.isBlank() -> WifiSecurityType.UNKNOWN
            normalized.contains("WPA3") || normalized.contains("SAE") || normalized.contains("OWE") ->
                WifiSecurityType.WPA3
            normalized.contains("EAP") -> WifiSecurityType.WPA2_ENTERPRISE
            normalized.contains("RSN") || normalized.contains("WPA2") -> WifiSecurityType.WPA2
            normalized.contains("WPA") -> WifiSecurityType.WPA
            normalized.contains("WEP") -> WifiSecurityType.WEP
            else -> WifiSecurityType.OPEN
        }
    }

    fun band(frequencyMhz: Int): WifiBand = when (frequencyMhz) {
        in 2401..2499 -> WifiBand.BAND_2_4_GHZ
        in 4900..5899 -> WifiBand.BAND_5_GHZ
        in 5925..7125 -> WifiBand.BAND_6_GHZ
        else -> WifiBand.UNKNOWN
    }

    fun isHiddenSsid(ssid: String?): Boolean {
        val normalized = ssid?.trim().orEmpty()
        return normalized.isEmpty() || normalized.equals(HIDDEN_SSID_LABEL, ignoreCase = true)
    }

    fun isGenericSsid(ssid: String?): Boolean {
        val normalized = ssid?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return genericSsids.contains(normalized)
    }

    fun evaluate(
        ssid: String?,
        capabilities: String?,
        rssiDbm: Int,
        blocked: Boolean = false,
        allowed: Boolean = false
    ): WifiRiskAssessment {
        val securityType = securityType(capabilities)
        val hidden = isHiddenSsid(ssid)
        val generic = isGenericSsid(ssid)
        val open = securityType == WifiSecurityType.OPEN
        val unknownEncryption = securityType == WifiSecurityType.UNKNOWN
        val veryStrongSignal = rssiDbm >= VERY_STRONG_SIGNAL_DBM
        val reasons = mutableListOf<String>()

        if (blocked) reasons += "Réseau marqué comme bloqué par vos soins."
        if (open) reasons += "Réseau sans chiffrement : trafic interceptable."
        if (hidden) reasons += "SSID masqué ou inconnu."
        if (securityType == WifiSecurityType.WEP) reasons += "Chiffrement WEP obsolète et cassable."
        if (generic) reasons += "Nom générique fréquemment usurpé dans les lieux publics."
        if (veryStrongSignal && (open || unknownEncryption)) {
            reasons += "Signal très fort sans chiffrement connu : point d'accès possiblement à proximité immédiate."
        }
        if (securityType == WifiSecurityType.WPA3 || securityType == WifiSecurityType.WPA2) {
            reasons += "Chiffrement moderne détecté."
        }

        val riskLevel = when {
            blocked -> NetworkRiskLevel.HIGH
            open || hidden -> NetworkRiskLevel.HIGH
            veryStrongSignal && unknownEncryption -> NetworkRiskLevel.HIGH
            allowed -> NetworkRiskLevel.LOW
            securityType == WifiSecurityType.WEP || generic -> NetworkRiskLevel.MEDIUM
            securityType == WifiSecurityType.WPA || unknownEncryption -> NetworkRiskLevel.MEDIUM
            else -> NetworkRiskLevel.LOW
        }

        val captivePortalSuspected = open && (generic || hidden || veryStrongSignal)
        if (captivePortalSuspected) {
            reasons += "Portail captif possible : page de connexion susceptible d'imiter un service légitime."
        }

        return WifiRiskAssessment(
            securityType = securityType,
            riskLevel = riskLevel,
            captivePortalSuspected = captivePortalSuspected,
            reasons = reasons.toList()
        )
    }
}

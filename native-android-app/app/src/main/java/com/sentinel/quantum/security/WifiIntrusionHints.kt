package com.sentinel.quantum.security

import android.content.Context
import java.security.MessageDigest

data class WifiIntrusionFinding(val level: NetworkRiskLevel, val text: String)

/**
 * Hints from already visible Wi-Fi results. Nothing here is an observed attack.
 */
object WifiIntrusionHints {
    fun assess(networks: List<DiscoveredWifiNetwork>, joined: WifiJoinedLink?): List<WifiIntrusionFinding> {
        val findings = mutableListOf<WifiIntrusionFinding>()
        val named = networks.filter { it.ssid.isNotBlank() && !WifiRiskEvaluator.isHiddenSsid(it.ssid) }
        named.groupBy { it.ssid.trim().lowercase() }.forEach { (_, group) ->
            val distinct = group.distinctBy { it.bssid.lowercase() }
            if (distinct.size < 2) return@forEach
            val strong = distinct.filter { it.assessment.securityType in STRONG }
            val weak = distinct.filter { it.assessment.securityType in WEAK }
            if (strong.isEmpty() || weak.isEmpty()) return@forEach
            val name = distinct.first().ssid
            val joinedToWeak = joined != null &&
                joined.ssid.trim().equals(name, ignoreCase = true) &&
                weak.any { it.bssid.equals(joined.bssid, ignoreCase = true) }
            if (joinedToWeak) {
                findings += WifiIntrusionFinding(
                    NetworkRiskLevel.HIGH,
                    "Ce téléphone est relié à la copie ouverte ou WEP de « $name ». Une autre borne du même nom est chiffrée. Vérifiez la borne avant de continuer."
                )
            } else {
                findings += WifiIntrusionFinding(
                    NetworkRiskLevel.MEDIUM,
                    "Le nom « $name » est annoncé à la fois chiffré et en ouvert ou en WEP. Cela peut être une copie. Ce n’est pas une intrusion constatée."
                )
            }
        }
        val joinedNetwork = joined?.let { link ->
            networks.firstOrNull { it.bssid.equals(link.bssid, ignoreCase = true) }
        }
        if (joinedNetwork?.radio?.securityDetail?.contains("WPS activé") == true) {
            findings += WifiIntrusionFinding(
                NetworkRiskLevel.MEDIUM,
                "WPS est activé sur le réseau rejoint. Une personne proche peut tenter le code. Aucune tentative n’a été vue."
            )
        }
        if (joinedNetwork?.radio?.pmfLabel == "PMF non annoncé" &&
            joinedNetwork.assessment.securityType == WifiSecurityType.WPA2
        ) {
            findings += WifiIntrusionFinding(
                NetworkRiskLevel.MEDIUM,
                "La borne rejointe n’annonce pas de protection des trames de gestion. Une déconnexion forcée ne serait pas visible ici."
            )
        }
        if (joinedNetwork?.radio?.securityDetail?.contains("TKIP") == true) {
            findings += WifiIntrusionFinding(
                NetworkRiskLevel.MEDIUM,
                "Le réseau rejoint annonce TKIP, un chiffrement ancien. Ce n’est pas une intrusion en cours."
            )
        }
        if (joinedNetwork?.assessment?.securityType == WifiSecurityType.OPEN &&
            findings.none { it.text.contains("copie ouverte") }
        ) {
            findings += WifiIntrusionFinding(
                NetworkRiskLevel.HIGH,
                "Le réseau rejoint n’a pas de chiffrement. Le trafic peut être lu à proximité. Aucune interception n’a été constatée."
            )
        }
        return findings
    }

    fun gatewayChanged(previous: String?, next: String?): Boolean =
        !previous.isNullOrBlank() && !next.isNullOrBlank() && previous != next

    private val STRONG = setOf(
        WifiSecurityType.WPA2,
        WifiSecurityType.WPA3,
        WifiSecurityType.WPA2_ENTERPRISE
    )
    private val WEAK = setOf(WifiSecurityType.OPEN, WifiSecurityType.WEP)
}

class GatewayChangeMemory(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun note(ssid: String, gateway: String?): String? {
        val name = ssid.trim()
        val gate = gateway?.trim().orEmpty()
        if (name.isBlank() || gate.isBlank() || WifiRiskEvaluator.isHiddenSsid(name)) return null
        val key = sha256(name.lowercase())
        val next = sha256(gate)
        val previous = preferences.getString(key, null)
        preferences.edit().putString(key, next).apply()
        if (!WifiIntrusionHints.gatewayChanged(previous, next)) return null
        return "La passerelle de « $name » a changé depuis la dernière lecture. Cela peut être un nouveau routeur, ou un autre appareil qui répond à sa place. Pas une preuve."
    }

    fun noteDns(ssid: String, dns: List<String>): String? {
        val name = ssid.trim()
        val servers = dns.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (name.isBlank() || servers.isEmpty() || WifiRiskEvaluator.isHiddenSsid(name)) return null
        val key = "dns:" + sha256(name.lowercase())
        val next = sha256(servers.joinToString(","))
        val previous = preferences.getString(key, null)
        preferences.edit().putString(key, next).apply()
        if (!WifiIntrusionHints.gatewayChanged(previous, next)) return null
        return "Les DNS de « $name » ont changé depuis la dernière lecture. Cela peut être le routeur, ou un autre résolveur. Pas une preuve."
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFERENCES = "gateway_change"
    }
}

package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiRiskEvaluatorTest {

    @Test
    fun detectsSecurityTypesFromCapabilities() {
        assertEquals(WifiSecurityType.OPEN, WifiRiskEvaluator.securityType("[ESS]"))
        assertEquals(WifiSecurityType.WEP, WifiRiskEvaluator.securityType("[WEP][ESS]"))
        assertEquals(WifiSecurityType.WPA, WifiRiskEvaluator.securityType("[WPA-PSK-TKIP][ESS]"))
        assertEquals(WifiSecurityType.WPA2, WifiRiskEvaluator.securityType("[WPA2-PSK-CCMP][RSN][ESS]"))
        assertEquals(WifiSecurityType.WPA3, WifiRiskEvaluator.securityType("[RSN-SAE-CCMP][ESS]"))
        assertEquals(
            WifiSecurityType.WPA2_ENTERPRISE,
            WifiRiskEvaluator.securityType("[WPA2-EAP-CCMP][ESS]")
        )
        assertEquals(WifiSecurityType.UNKNOWN, WifiRiskEvaluator.securityType(null))
    }

    @Test
    fun mapsFrequenciesToBands() {
        assertEquals(WifiBand.BAND_2_4_GHZ, WifiRiskEvaluator.band(2437))
        assertEquals(WifiBand.BAND_5_GHZ, WifiRiskEvaluator.band(5180))
        assertEquals(WifiBand.BAND_6_GHZ, WifiRiskEvaluator.band(6135))
        assertEquals(WifiBand.UNKNOWN, WifiRiskEvaluator.band(0))
    }

    @Test
    fun openNetworkIsHighRiskAndFlagsCaptivePortal() {
        val assessment = WifiRiskEvaluator.evaluate(
            ssid = "Free WiFi",
            capabilities = "[ESS]",
            rssiDbm = -60
        )

        assertEquals(NetworkRiskLevel.HIGH, assessment.riskLevel)
        assertEquals(WifiSecurityType.OPEN, assessment.securityType)
        assertTrue(assessment.captivePortalSuspected)
    }

    @Test
    fun hiddenSsidIsHighRisk() {
        val assessment = WifiRiskEvaluator.evaluate(
            ssid = WifiRiskEvaluator.HIDDEN_SSID_LABEL,
            capabilities = "[WPA2-PSK-CCMP][RSN][ESS]",
            rssiDbm = -70
        )

        assertEquals(NetworkRiskLevel.HIGH, assessment.riskLevel)
    }

    @Test
    fun veryStrongSignalWithUnknownEncryptionIsHighRisk() {
        val assessment = WifiRiskEvaluator.evaluate(
            ssid = "Bureau-5G",
            capabilities = null,
            rssiDbm = -30
        )

        assertEquals(NetworkRiskLevel.HIGH, assessment.riskLevel)
        assertFalse(assessment.captivePortalSuspected)
    }

    @Test
    fun wepIsMediumRisk() {
        val assessment = WifiRiskEvaluator.evaluate(
            ssid = "Maison-Durand",
            capabilities = "[WEP][ESS]",
            rssiDbm = -65
        )

        assertEquals(NetworkRiskLevel.MEDIUM, assessment.riskLevel)
    }

    @Test
    fun genericEncryptedSsidIsMediumRisk() {
        val assessment = WifiRiskEvaluator.evaluate(
            ssid = "hotspot",
            capabilities = "[WPA2-PSK-CCMP][RSN][ESS]",
            rssiDbm = -66
        )

        assertEquals(NetworkRiskLevel.MEDIUM, assessment.riskLevel)
        assertTrue(WifiRiskEvaluator.isGenericSsid("Hotspot"))
        assertFalse(WifiRiskEvaluator.isGenericSsid("Maison-Durand"))
    }

    @Test
    fun modernEncryptionWithReasonableNameIsLowRisk() {
        val wpa3 = WifiRiskEvaluator.evaluate(
            ssid = "Maison-Durand",
            capabilities = "[RSN-SAE-CCMP][ESS]",
            rssiDbm = -55
        )
        val wpa2 = WifiRiskEvaluator.evaluate(
            ssid = "Bureau-Etage-2",
            capabilities = "[WPA2-PSK-CCMP][RSN][ESS]",
            rssiDbm = -55
        )

        assertEquals(NetworkRiskLevel.LOW, wpa3.riskLevel)
        assertEquals(NetworkRiskLevel.LOW, wpa2.riskLevel)
    }

    @Test
    fun userBlocklistForcesHighRiskAndAllowlistNeverDowngradesOpenNetwork() {
        val blocked = WifiRiskEvaluator.evaluate(
            ssid = "Maison-Durand",
            capabilities = "[RSN-SAE-CCMP][ESS]",
            rssiDbm = -55,
            blocked = true
        )
        val allowedButOpen = WifiRiskEvaluator.evaluate(
            ssid = "Maison-Durand",
            capabilities = "[ESS]",
            rssiDbm = -55,
            allowed = true
        )

        assertEquals(NetworkRiskLevel.HIGH, blocked.riskLevel)
        assertEquals(NetworkRiskLevel.HIGH, allowedButOpen.riskLevel)
    }

    @Test
    fun allowlistDowngradesGenericEncryptedNetwork() {
        val assessment = WifiRiskEvaluator.evaluate(
            ssid = "guest",
            capabilities = "[WPA2-PSK-CCMP][RSN][ESS]",
            rssiDbm = -60,
            allowed = true
        )

        assertEquals(NetworkRiskLevel.LOW, assessment.riskLevel)
    }
}

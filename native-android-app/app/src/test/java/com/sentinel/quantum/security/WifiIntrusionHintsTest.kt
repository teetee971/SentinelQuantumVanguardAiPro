package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiIntrusionHintsTest {
    @Test
    fun sameNameWithOpenAndWpa2IsACopyHintNotAnObservedAttack() {
        val secure = network("Livebox", "2c:93:fb:00:00:01", "[WPA2-PSK-CCMP]")
        val open = network("Livebox", "00:11:22:00:00:02", "[ESS]")
        val transition = listOf(
            network("Livebox", "2c:93:fb:00:00:01", "[WPA2-PSK-CCMP][WPA3-SAE]"),
            network("Livebox", "2c:93:fb:00:00:03", "[WPA3-SAE-CCMP]")
        )
        val findings = WifiIntrusionHints.assess(listOf(secure, open), joined = null)
        assertEquals(1, findings.size)
        assertTrue(findings[0].text.contains("pas une intrusion constatée"))
        assertTrue(WifiIntrusionHints.assess(transition, null).isEmpty())
        assertFalse(WifiIntrusionHints.gatewayChanged(null, "aa"))
        assertTrue(WifiIntrusionHints.gatewayChanged("aa", "bb"))
    }

    private fun network(ssid: String, bssid: String, capabilities: String) = DiscoveredWifiNetwork(
        ssid = ssid,
        bssid = bssid,
        rssiDbm = -40,
        frequencyMhz = 5500,
        band = WifiBand.BAND_5_GHZ,
        assessment = WifiRiskEvaluator.evaluate(ssid, capabilities, -40),
        radio = WifiRadioAnalyzer.describe(5500, 5500, 2, 0, capabilities, bssid, -40)
    )
}

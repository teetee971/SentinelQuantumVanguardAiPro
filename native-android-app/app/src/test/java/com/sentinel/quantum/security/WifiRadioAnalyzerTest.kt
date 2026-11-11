package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiRadioAnalyzerTest {
    @Test
    fun channelsFollowTheStandardNumbering() {
        assertEquals(6, WifiRadioAnalyzer.channel(2437))
        assertEquals(14, WifiRadioAnalyzer.channel(2484))
        assertEquals(36, WifiRadioAnalyzer.channel(5180))
        assertEquals(1, WifiRadioAnalyzer.channel(5955))
        assertEquals(null, WifiRadioAnalyzer.channel(1000))
    }

    @Test
    fun locallyAdministeredAddressHidesTheVendor() {
        assertEquals(
            "adresse privée · fabricant masqué",
            WifiRadioAnalyzer.vendorLabel("02:00:00:00:00:01")
        )
        assertEquals("Ubiquiti", WifiRadioAnalyzer.vendorLabel("24:A4:3C:10:20:30"))
        assertEquals("fabricant hors table locale", WifiRadioAnalyzer.vendorLabel("00:11:22:33:44:55"))
    }

    @Test
    fun securityAndPmfAreReadFromCapabilities() {
        val capabilities = "[WPA2-PSK-CCMP][WPA3-SAE-CCMP][MFPC][MFPR]"
        assertTrue(WifiRadioAnalyzer.securityDetail(capabilities).contains("WPA3-SAE"))
        assertEquals("PMF obligatoire", WifiRadioAnalyzer.pmfLabel(capabilities))
        assertEquals("WEP", WifiRadioAnalyzer.securityDetail("[WEP]"))
    }

    @Test
    fun surveyCountsCoChannelAndOverlapping24GhzNetworks() {
        val first = sample(ssid = "A", bssid = "24:a4:3c:00:00:01", frequency = 2437)
        val second = sample(ssid = "B", bssid = "24:a4:3c:00:00:02", frequency = 2437)
        val adjacent = sample(ssid = "C", bssid = "24:a4:3c:00:00:03", frequency = 2452)
        val networks = listOf(first, second, adjacent)
        assertEquals(2, WifiRadioAnalyzer.sameChannelCount(6, WifiBand.BAND_2_4_GHZ, networks))
        assertEquals(1, WifiRadioAnalyzer.overlapping24Count(6, networks))
        val survey = WifiRadioAnalyzer.survey(networks)
        assertEquals(3, survey.accessPointCount)
        assertEquals(3, survey.distinctSsids)
        assertEquals(6, survey.busiest.first().channel)
        assertEquals(2, survey.busiest.first().accessPoints)
    }

    private fun sample(ssid: String, bssid: String, frequency: Int): DiscoveredWifiNetwork {
        val radio = WifiRadioAnalyzer.describe(
            frequencyMhz = frequency,
            channelWidth = 0,
            wifiStandard = 4,
            capabilities = "[WPA2-PSK-CCMP]",
            bssid = bssid,
            rssiDbm = -55
        )
        return DiscoveredWifiNetwork(
            ssid = ssid,
            bssid = bssid,
            rssiDbm = -55,
            frequencyMhz = frequency,
            band = WifiRiskEvaluator.band(frequency),
            assessment = WifiRiskEvaluator.evaluate(ssid, "[WPA2-PSK-CCMP]", -55),
            radio = radio
        )
    }
}

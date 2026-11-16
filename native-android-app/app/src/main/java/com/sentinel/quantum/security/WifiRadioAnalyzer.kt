package com.sentinel.quantum.security

/**
 * Local radio detail for every access point Android already returned.
 * This is not a client list and not a scan of a network the phone has not joined.
 */
data class WifiAccessPointRadio(
    val channel: Int?,
    val widthLabel: String,
    val standardLabel: String,
    val securityDetail: String,
    val pmfLabel: String,
    val vendorLabel: String,
    val signalLabel: String,
    val occupiedLabel: String = "plage non déduite",
    val beaconLoadLabel: String = "charge non annoncée par la borne",
    val phyDetailLabel: String = "détails Wi-Fi 6/7 non annoncés"
)

data class WifiChannelLoad(
    val band: WifiBand,
    val channel: Int,
    val accessPoints: Int
)

data class WifiRadioSurvey(
    val accessPointCount: Int,
    val distinctSsids: Int,
    val openCount: Int,
    val busiest: List<WifiChannelLoad>
)

object WifiRadioAnalyzer {
    fun channel(frequencyMhz: Int): Int? = when (frequencyMhz) {
        2484 -> 14
        in 2412..2472 -> (frequencyMhz - 2407) / 5
        in 5000..5895 -> (frequencyMhz - 5000) / 5
        in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> null
    }

    fun widthLabel(channelWidth: Int): String = when (channelWidth) {
        0 -> "20 MHz"
        1 -> "40 MHz"
        2 -> "80 MHz"
        3 -> "160 MHz"
        4 -> "80+80 MHz"
        5 -> "320 MHz"
        else -> "largeur non indiquée"
    }

    /** Contiguous width around the center Android announced. Not a measured spectrum. */
    fun occupiedLabel(centerMhz: Int, channelWidth: Int): String {
        val width = when (channelWidth) {
            0 -> 20
            1 -> 40
            2 -> 80
            3 -> 160
            5 -> 320
            else -> return "plage non déduite"
        }
        if (centerMhz !in 2400..7125) return "plage non déduite"
        val half = width / 2
        return "${centerMhz - half}–${centerMhz + half} MHz · centre annoncé $centerMhz"
    }

    /** BSS Load and TPC as the beacon sent them. Not a count of devices we discovered. */
    fun beaconLoadLabel(elements: List<Pair<Int, ByteArray>>): String {
        val load = elements.firstOrNull { it.first == 11 }?.second
        val clients = load?.let(::stationCount)
        val utilization = load?.let(::utilizationPercent)
        val power = elements.firstOrNull { it.first == 35 }?.second?.let(::transmitPowerDbm)
        if (clients == null && utilization == null && power == null) {
            return "charge non annoncée par la borne"
        }
        return buildString {
            if (clients != null) append("$clients client(s) annoncé(s) par la borne")
            if (utilization != null) {
                if (isNotEmpty()) append(" · ")
                append("occupation annoncée $utilization %")
            }
            if (power != null) {
                if (isNotEmpty()) append(" · ")
                append("puissance annoncée $power dBm")
            }
        }
    }

    fun stationCount(payload: ByteArray): Int? {
        if (payload.size < 2) return null
        return (payload[0].toInt() and 0xff) or ((payload[1].toInt() and 0xff) shl 8)
    }

    fun utilizationPercent(payload: ByteArray): Int? {
        if (payload.size < 3) return null
        return ((payload[2].toInt() and 0xff) * 100) / 255
    }

    fun transmitPowerDbm(payload: ByteArray): Int? {
        if (payload.isEmpty()) return null
        return payload[0].toInt()
    }

    fun standardLabel(wifiStandard: Int, frequencyMhz: Int = 0): String = when {
        wifiStandard == 6 && frequencyMhz in 5925..7125 -> "Wi-Fi 6E · 802.11ax"
        wifiStandard == 1 -> "802.11a/b/g"
        wifiStandard == 4 -> "Wi-Fi 4 · 802.11n"
        wifiStandard == 5 -> "Wi-Fi 5 · 802.11ac"
        wifiStandard == 6 -> "Wi-Fi 6 · 802.11ax"
        wifiStandard == 7 -> "802.11ad"
        wifiStandard == 8 -> "Wi-Fi 7 · 802.11be"
        else -> "norme non indiquée"
    }

    /** HE/EHT fields the beacon actually carries. Streams are announced, not measured. */
    fun phyDetailLabel(elements: List<Pair<Int, ByteArray>>): String {
        val parts = mutableListOf<String>()
        extension(elements, 35)?.let(::heSpatialStreams)?.let { parts += "jusqu'à $it flux annoncés" }
        extension(elements, 35)?.let(::heWidthLabel)?.let { parts += it }
        extension(elements, 36)?.let(::heBssColor)?.let { parts += it }
        if (extension(elements, 108) != null) parts += "capacités Wi-Fi 7 annoncées"
        if (elements.any { it.first == 107 }) parts += "MLO annoncé"
        if (parts.isEmpty()) return "détails Wi-Fi 6/7 non annoncés"
        return parts.joinToString(" · ")
    }

    fun heSpatialStreams(payload: ByteArray): Int? {
        if (payload.isEmpty() || payload[0].toInt() != 35 || payload.size < 20) return null
        return spatialStreams(payload.copyOfRange(18, 20))
    }

    fun heWidthLabel(payload: ByteArray): String? {
        if (payload.isEmpty() || payload[0].toInt() != 35 || payload.size < 8) return null
        val width = payload[7].toInt() and 0xff
        return when {
            width and 0x10 != 0 -> "160 ou 80+80 MHz annoncé"
            width and 0x08 != 0 -> "160 MHz annoncé"
            width and 0x04 != 0 -> "80 MHz annoncé"
            width and 0x02 != 0 -> "40 MHz annoncé en 2,4 GHz"
            else -> null
        }
    }

    fun heBssColor(payload: ByteArray): String? {
        if (payload.isEmpty() || payload[0].toInt() != 36 || payload.size < 5) return null
        val info = payload[4].toInt() and 0xff
        if (info and 0x80 != 0) return "couleur BSS désactivée"
        return "couleur BSS annoncée ${info and 0x3f}"
    }

    fun spatialStreams(rxMap: ByteArray): Int? {
        if (rxMap.size < 2) return null
        val value = (rxMap[0].toInt() and 0xff) or ((rxMap[1].toInt() and 0xff) shl 8)
        var max = 0
        for (index in 0 until 8) {
            val supported = (value shr (index * 2)) and 0x3
            if (supported != 3) max = index + 1
        }
        return max.takeIf { it > 0 }
    }

    private fun extension(elements: List<Pair<Int, ByteArray>>, extensionId: Int): ByteArray? =
        elements.firstOrNull { it.first == 255 && it.second.firstOrNull()?.toInt() == extensionId }?.second

    fun securityDetail(
        capabilities: String?,
        elements: List<Pair<Int, ByteArray>> = emptyList()
    ): String {
        val normalized = capabilities?.uppercase().orEmpty()
        if (normalized.isBlank()) return wpsAnnounced(elements) ?: "sécurité non indiquée"
        val parts = mutableListOf<String>()
        when {
            normalized.contains("SAE") || normalized.contains("WPA3") -> parts += "WPA3-SAE"
            normalized.contains("OWE") -> parts += "OWE · ouvert chiffré"
            normalized.contains("RSN") || normalized.contains("WPA2") -> parts += "WPA2"
            normalized.contains("WPA") -> parts += "WPA"
            normalized.contains("WEP") -> parts += "WEP"
            else -> parts += "ouvert"
        }
        if (normalized.contains("EAP")) parts += "802.1X"
        when {
            normalized.contains("GCMP-256") -> parts += "GCMP-256"
            normalized.contains("GCMP") -> parts += "GCMP"
            normalized.contains("CCMP") -> parts += "CCMP"
        }
        if (normalized.contains("TKIP")) parts += "TKIP"
        parts += wpsAnnounced(elements) ?: if (normalized.contains("WPS")) "WPS activé" else ""
        return parts.filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** Reads the WPS announcement. No code is sent. */
    fun wpsAnnounced(elements: List<Pair<Int, ByteArray>>): String? {
        val body = elements.firstNotNullOfOrNull { (id, value) ->
            if (id != 221 || value.size < 4) return@firstNotNullOfOrNull null
            val wps = value[0] == 0x00.toByte() &&
                value[1] == 0x50.toByte() &&
                value[2] == 0xF2.toByte() &&
                value[3] == 0x04.toByte()
            if (wps) value.copyOfRange(4, value.size) else null
        } ?: return null
        var methods: Int? = null
        var locked = false
        var offset = 0
        while (offset + 4 <= body.size) {
            val type = ((body[offset].toInt() and 0xff) shl 8) or (body[offset + 1].toInt() and 0xff)
            val length = ((body[offset + 2].toInt() and 0xff) shl 8) or (body[offset + 3].toInt() and 0xff)
            if (offset + 4 + length > body.size) break
            val start = offset + 4
            when (type) {
                0x1008 -> if (length >= 2) {
                    methods = ((body[start].toInt() and 0xff) shl 8) or (body[start + 1].toInt() and 0xff)
                }
                0x1057 -> if (length >= 1 && (body[start].toInt() and 0xff) == 0x01) locked = true
            }
            offset += 4 + length
        }
        if (locked) return "WPS annoncé verrouillé"
        val bits = methods ?: return "WPS annoncé, mode non précisé"
        val pin = bits and 0x610C != 0
        val button = bits and 0x0680 != 0
        return when {
            pin && button -> "WPS annoncé : code et bouton"
            pin -> "WPS annoncé : code"
            button -> "WPS annoncé : bouton"
            else -> "WPS annoncé, mode non précisé"
        }
    }

    fun pmfLabel(capabilities: String?): String {
        val normalized = capabilities?.uppercase().orEmpty()
        return when {
            normalized.contains("MFPR") -> "PMF obligatoire"
            normalized.contains("MFPC") -> "PMF possible"
            normalized.isBlank() -> "PMF non indiqué"
            else -> "PMF non annoncé"
        }
    }

    fun signalLabel(rssiDbm: Int): String = when {
        rssiDbm >= -50 -> "excellent"
        rssiDbm >= -60 -> "bon"
        rssiDbm >= -70 -> "moyen"
        rssiDbm >= -80 -> "faible"
        else -> "très faible"
    }

    fun vendorLabel(bssid: String): String {
        val octets = bssid.trim().lowercase().split(":", "-")
        if (octets.size < 3) return "fabricant non lu"
        val first = octets[0].toIntOrNull(16) ?: return "fabricant non lu"
        if (first and 0x02 != 0) return "adresse privée · fabricant masqué"
        val prefix = octets.take(3).joinToString(":")
        return VENDORS[prefix] ?: "fabricant hors table locale"
    }

    fun ageMs(bootTimestampUs: Long, elapsedRealtimeMs: Long): Long {
        if (bootTimestampUs <= 0L || elapsedRealtimeMs < 0L) return -1L
        return elapsedRealtimeMs - bootTimestampUs / 1_000L
    }

    fun seenAgeLabel(ageMs: Long): String = when {
        ageMs < 0L -> "âge non lu"
        ageMs < 5_000L -> "vu à l'instant"
        ageMs < 60_000L -> "vu il y a ${ageMs / 1_000L} s"
        ageMs < 3_600_000L -> "vu il y a ${ageMs / 60_000L} min"
        else -> "vu il y a ${ageMs / 3_600_000L} h"
    }

    fun describe(
        frequencyMhz: Int,
        centerMhz: Int,
        channelWidth: Int,
        wifiStandard: Int,
        capabilities: String?,
        bssid: String,
        rssiDbm: Int,
        informationElements: List<Pair<Int, ByteArray>> = emptyList()
    ): WifiAccessPointRadio = WifiAccessPointRadio(
        channel = channel(frequencyMhz),
        widthLabel = widthLabel(channelWidth),
        standardLabel = standardLabel(wifiStandard, frequencyMhz),
        securityDetail = securityDetail(capabilities, informationElements),
        pmfLabel = pmfLabel(capabilities),
        vendorLabel = vendorLabel(bssid),
        signalLabel = signalLabel(rssiDbm),
        occupiedLabel = occupiedLabel(centerMhz, channelWidth),
        beaconLoadLabel = beaconLoadLabel(informationElements),
        phyDetailLabel = phyDetailLabel(informationElements)
    )

    fun distinctAccessPoints(networks: List<DiscoveredWifiNetwork>): List<DiscoveredWifiNetwork> {
        val (identified, hidden) = networks.partition { it.bssid.isNotBlank() }
        val strongest = identified
            .groupBy { it.bssid.lowercase() }
            .map { (_, group) -> group.maxBy { it.rssiDbm } }
        return strongest + hidden
    }

    fun sameChannelCount(
        channel: Int?,
        band: WifiBand,
        networks: List<DiscoveredWifiNetwork>,
        exceptBssid: String? = null
    ): Int {
        if (channel == null) return 0
        return networks.count { network ->
            network.band == band &&
                network.radio.channel == channel &&
                (exceptBssid == null || !network.bssid.equals(exceptBssid, ignoreCase = true))
        }
    }

    fun overlapping24Count(channel: Int?, networks: List<DiscoveredWifiNetwork>): Int {
        if (channel == null) return 0
        return networks.count { other ->
            other.band == WifiBand.BAND_2_4_GHZ &&
                other.radio.channel != null &&
                other.radio.channel != channel &&
                kotlin.math.abs(other.radio.channel - channel) <= 4
        }
    }

    fun quietestChannel(band: WifiBand, networks: List<DiscoveredWifiNetwork>): Int? {
        val seen = networks.mapNotNull { network ->
            network.radio.channel?.takeIf { network.band == band }
        }
        val candidates = when (band) {
            WifiBand.BAND_2_4_GHZ -> listOf(1, 6, 11)
            WifiBand.BAND_5_GHZ -> (listOf(36, 40, 44, 48, 100, 104, 108, 112) + seen).distinct()
            else -> return null
        }
        return candidates.minByOrNull { channel ->
            val same = sameChannelCount(channel, band, networks)
            val overlap = if (band == WifiBand.BAND_2_4_GHZ) overlapping24Count(channel, networks) else 0
            same * 3 + overlap
        }
    }

    fun survey(networks: List<DiscoveredWifiNetwork>): WifiRadioSurvey {
        val loads = networks
            .filter { it.radio.channel != null }
            .groupBy { it.band to it.radio.channel }
            .map { (key, group) ->
                WifiChannelLoad(band = key.first, channel = key.second ?: 0, accessPoints = group.size)
            }
            .sortedByDescending { it.accessPoints }
        return WifiRadioSurvey(
            accessPointCount = networks.size,
            distinctSsids = networks.map { it.ssid.lowercase() }.distinct().size,
            openCount = networks.count { it.assessment.securityType == WifiSecurityType.OPEN },
            busiest = loads.take(6)
        )
    }

    private val VENDORS = mapOf(
        "00:1a:2b" to "Ayecom",
        "2c:93:fb" to "Sercomm",
        "18:e8:29" to "Ubiquiti",
        "24:a4:3c" to "Ubiquiti",
        "74:83:c2" to "Ubiquiti",
        "b4:fb:e4" to "Ubiquiti",
        "f4:92:bf" to "Ubiquiti",
        "50:c7:bf" to "TP-Link",
        "60:32:b1" to "TP-Link",
        "98:da:c4" to "TP-Link",
        "c0:06:c3" to "TP-Link",
        "b0:4e:26" to "TP-Link",
        "20:e5:2a" to "Netgear",
        "08:02:8e" to "Netgear",
        "e0:46:9a" to "Netgear",
        "04:d4:c4" to "ASUS",
        "1c:87:2c" to "ASUS",
        "2c:4d:54" to "ASUS",
        "ac:9e:17" to "ASUS",
        "00:0c:e6" to "Cisco",
        "00:1c:0e" to "Cisco",
        "58:6d:8f" to "Cisco Meraki",
        "e0:55:3d" to "Cisco Meraki",
        "00:1f:33" to "Netgear",
        "7c:10:c9" to "Apple",
        "a4:83:e7" to "Apple",
        "f0:18:98" to "Apple",
        "3c:22:fb" to "Apple",
        "88:66:a5" to "Apple",
        "8c:85:90" to "Apple",
        "b8:27:eb" to "Raspberry Pi",
        "dc:a6:32" to "Raspberry Pi",
        "e4:5f:01" to "Raspberry Pi",
        "24:0a:c4" to "Espressif",
        "30:ae:a4" to "Espressif",
        "3c:61:05" to "Espressif",
        "84:f3:eb" to "Espressif",
        "a4:cf:12" to "Espressif",
        "c8:c9:a3" to "Espressif",
        "10:d5:61" to "Tuya",
        "d8:1f:12" to "Tuya",
        "c0:f8:53" to "Hikvision",
        "44:19:b6" to "Hikvision",
        "18:68:cb" to "Hangzhou Hikvision",
        "e0:50:8b" to "Zhejiang Dahua",
        "3c:ef:8c" to "Zhejiang Dahua",
        "00:12:41" to "Dahua",
        "54:ef:44" to "Google",
        "3c:5a:b4" to "Google",
        "f4:f5:d8" to "Google",
        "00:fc:8b" to "Amazon",
        "fc:65:de" to "Amazon",
        "68:54:fd" to "Amazon",
        "48:ad:08" to "Huawei",
        "00:9a:cd" to "Huawei",
        "04:b0:e7" to "Huawei",
        "64:b4:73" to "Xiaomi",
        "28:6c:07" to "Xiaomi",
        "50:64:2b" to "Xiaomi",
        "cc:32:e5" to "TP-Link",
        "e8:94:f6" to "TP-Link",
        "14:cc:20" to "TP-Link",
        "c4:e9:84" to "TP-Link",
        "00:1d:7e" to "Cisco-Linksys",
        "c0:56:27" to "Belkin",
        "94:10:3e" to "Belkin",
        "00:1b:2f" to "AVM",
        "38:10:d5" to "AVM",
        "c8:0e:14" to "AVM",
        "00:24:d4" to "Freebox",
        "f4:ca:e5" to "Freebox",
        "34:27:92" to "Freebox",
        "00:19:70" to "Z-Com",
        "00:07:cb" to "Freebox",
        "e8:df:70" to "Huawei",
        "70:4f:57" to "TP-Link",
        "a8:42:a1" to "Samsung",
        "64:1c:ae" to "Samsung",
        "c0:bd:d1" to "Samsung",
        "08:d4:0c" to "Samsung",
        "84:25:3f" to "Silex",
        "00:0e:8e" to "SparkLAN",
        "00:1e:58" to "D-Link",
        "1c:7e:e5" to "D-Link",
        "28:10:7b" to "D-Link",
        "c8:d3:ff" to "Hewlett Packard",
        "3c:d9:2b" to "Hewlett Packard",
        "9c:8e:cd" to "AmpliFi / Ubiquiti",
        "fc:ec:da" to "Ubiquiti",
        "44:d9:e7" to "Ubiquiti",
        "78:8a:20" to "Ubiquiti",
        "ac:8b:a9" to "Ubiquiti",
        "dc:9f:db" to "Ubiquiti",
        "f0:9f:c2" to "Ubiquiti",
        "74:ac:b9" to "Ubiquiti",
        "e0:63:da" to "Ubiquiti",
    )
}

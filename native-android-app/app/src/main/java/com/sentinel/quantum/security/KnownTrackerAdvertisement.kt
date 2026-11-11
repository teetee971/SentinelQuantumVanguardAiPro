package com.sentinel.quantum.security

/**
 * Public advertisement checks used by AirGuard for Tile, Chipolo and Samsung SmartTag.
 * Google's FEAA filter is not copied: that UUID is also used by ordinary Eddystone beacons.
 */
data class KnownTrackerAdvertisement(
    val kind: String,
    val label: String,
    val identityPayload: ByteArray?
)

object KnownTrackerAdvertisements {
    fun match(serviceUuids: List<String>, serviceData: Map<String, ByteArray>): KnownTrackerAdvertisement? {
        val uuids = serviceUuids.map(::normalize).toSet()
        val data = serviceData.mapKeys { normalize(it.key) }
        val samsung = data[SAMSUNG]
        if (samsung != null && samsung.isNotEmpty() && (samsung[0].toInt() and 0xF8) == 0x10) {
            return KnownTrackerAdvertisement(
                kind = "samsung",
                label = "Annonce compatible avec un SmartTag Samsung. Pas une preuve de suivi.",
                identityPayload = samsung.takeIf { it.size >= 4 }
            )
        }
        val tile = data[TILE]
        if (tile != null && tile.size >= 2 && tile[0] == 0x02.toByte() && tile[1] == 0x00.toByte()) {
            return KnownTrackerAdvertisement(
                kind = "tile",
                label = "Annonce Tile. Pas une preuve de suivi.",
                identityPayload = tile.takeIf { it.size >= 4 }
            )
        }
        if (CHIPOLO in uuids || data.containsKey(CHIPOLO)) {
            return KnownTrackerAdvertisement(
                kind = "chipolo",
                label = "Annonce compatible avec un Chipolo. Pas une preuve de suivi.",
                identityPayload = data[CHIPOLO]?.takeIf { it.size >= 4 }
            )
        }
        if (PEBBLEBEE in uuids || data.containsKey(PEBBLEBEE)) {
            return KnownTrackerAdvertisement(
                kind = "pebblebee",
                label = "Annonce compatible avec un Pebblebee. Pas une preuve de suivi.",
                identityPayload = data[PEBBLEBEE]?.takeIf { it.size >= 4 }
            )
        }
        return null
    }

    private fun normalize(uuid: String): String = uuid.trim().lowercase()

    private const val SAMSUNG = "0000fd5a-0000-1000-8000-00805f9b34fb"
    private const val TILE = "0000feed-0000-1000-8000-00805f9b34fb"
    private const val CHIPOLO = "0000fe33-0000-1000-8000-00805f9b34fb"
    private const val PEBBLEBEE = "0000fa25-0000-1000-8000-00805f9b34fb"
}

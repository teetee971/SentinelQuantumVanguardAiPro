package com.sentinel.quantum.wearable.contract

/** Platform/transport facts only; this type grants no Sentinel trust or session authority. */
enum class WearableTransportKind { BLUETOOTH_CLASSIC, BLUETOOTH_LE, WEAR_OS, VENDOR_BRIDGE }
enum class WearablePlatformLinkKind { NONE, DISCOVERED, BONDED, COMPANION_ASSOCIATED }

data class WearablePlatformObservation(
    val stableIdHint: String?,
    val displayName: String?,
    val transport: WearableTransportKind,
    val platformLink: WearablePlatformLinkKind
) {
    init {
        require(stableIdHint == null || stableIdHint.isNotBlank())
        require(displayName == null || displayName.isNotBlank())
    }
}

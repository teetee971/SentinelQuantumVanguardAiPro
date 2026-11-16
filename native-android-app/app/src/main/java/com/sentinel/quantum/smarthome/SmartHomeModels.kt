package com.sentinel.quantum.smarthome

enum class SmartHomeBrand {
    IMOU, PHILIPS_HUE, LSC_SMART_CONNECT, MATTER, TUYA, TP_LINK_TAPO_KASA,
    SAMSUNG_SMARTTHINGS, GOOGLE_HOME, AMAZON_ALEXA, APPLE_HOME,
    HOME_ASSISTANT, IKEA_HOME_SMART, XIAOMI_AQARA, GENERIC
}
enum class SmartHomeTransport {
    WIFI, ETHERNET, BLUETOOTH_LE, MATTER_WIFI, MATTER_THREAD,
    THREAD, ZIGBEE_BRIDGE, Z_WAVE_BRIDGE, CLOUD, UNKNOWN
}
enum class SmartHomeDeviceType {
    CAMERA, LIGHT, PLUG, SWITCH, SENSOR, THERMOSTAT, LOCK, DOORBELL,
    TV, SPEAKER, WATCH, WEARABLE, HEADPHONES, HUB, ROUTER, MESH_NODE,
    NAS, PRINTER, CONSOLE, APPLIANCE, ROBOT_VACUUM, BLIND, SMOKE_CO,
    WATER_DEVICE, ENERGY_METER, EV_CHARGER, PHONE, TABLET, COMPUTER, OTHER
}
enum class SmartHomeConfidence { DETECTED, IDENTIFIED, CONNECTED, CONTROLLABLE, VERIFIED }

data class SmartHomeNode(
    val id: String,
    val name: String,
    val brand: SmartHomeBrand,
    val type: SmartHomeDeviceType,
    val transports: Set<SmartHomeTransport>,
    val parentId: String? = null,
    val relation: String? = null,
    val confidence: SmartHomeConfidence = SmartHomeConfidence.DETECTED,
    val verified: Boolean = false
)

data class SmartHomeTopology(val nodes: List<SmartHomeNode>) {
    init {
        require(nodes.map { it.id }.distinct().size == nodes.size) { "Duplicate smart-home node id" }
        val ids = nodes.map { it.id }.toSet()
        require(nodes.none { it.parentId != null && it.parentId !in ids }) { "Unknown parent node" }
        require(nodes.none { it.parentId == it.id }) { "Self-referencing smart-home node" }
        require(nodes.none { node ->
            var current = node.parentId
            val seen = mutableSetOf(node.id)
            while (current != null && seen.add(current)) {
                current = nodes.firstOrNull { it.id == current }?.parentId
            }
            current != null
        }) { "Cyclic smart-home topology" }
    }

    fun childrenOf(id: String): List<SmartHomeNode> = nodes.filter { it.parentId == id }
    fun roots(): List<SmartHomeNode> = nodes.filter { it.parentId == null }
}

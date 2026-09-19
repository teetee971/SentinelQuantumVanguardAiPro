package com.sentinel.quantum.smarthome

enum class SmartHomeBrand { IMOU, PHILIPS_HUE, LSC_SMART_CONNECT, MATTER, GENERIC }
enum class SmartHomeTransport { WIFI, ETHERNET, BLUETOOTH_LE, MATTER_WIFI, MATTER_THREAD, ZIGBEE_BRIDGE, CLOUD, UNKNOWN }
enum class SmartHomeDeviceType { CAMERA, LIGHT, PLUG, SWITCH, SENSOR, THERMOSTAT, LOCK, DOORBELL, TV, SPEAKER, WATCH, HUB, ROUTER, PHONE, OTHER }

data class SmartHomeNode(
    val id: String,
    val name: String,
    val brand: SmartHomeBrand,
    val type: SmartHomeDeviceType,
    val transports: Set<SmartHomeTransport>,
    val parentId: String? = null,
    val relation: String? = null,
    val verified: Boolean = false
)

data class SmartHomeTopology(val nodes: List<SmartHomeNode>) {
    init {
        require(nodes.map { it.id }.distinct().size == nodes.size) { "Duplicate smart-home node id" }
        val ids = nodes.map { it.id }.toSet()
        require(nodes.none { it.parentId != null && it.parentId !in ids }) { "Unknown parent node" }
        require(nodes.none { it.parentId == it.id }) { "Self-referencing smart-home node" }
    }

    fun childrenOf(id: String): List<SmartHomeNode> = nodes.filter { it.parentId == id }
    fun roots(): List<SmartHomeNode> = nodes.filter { it.parentId == null }
}

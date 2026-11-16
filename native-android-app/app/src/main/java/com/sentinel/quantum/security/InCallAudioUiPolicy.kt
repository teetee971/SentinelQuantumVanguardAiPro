package com.sentinel.quantum.security

/**
 * Pure presentation policy for in-call audio routes.
 * Android endpoint identifiers stay opaque; the UI only receives bounded French labels.
 */
object InCallAudioUiPolicy {
    enum class Kind { EARPIECE, BLUETOOTH, WIRED_HEADSET, SPEAKER, STREAMING, UNKNOWN }

    data class Route(
        val id: String,
        val kind: Kind,
        val deviceName: String? = null,
        val selected: Boolean = false
    )

    data class PresentedRoute(
        val id: String,
        val label: String,
        val selected: Boolean
    )

    fun present(routes: List<Route>): List<PresentedRoute> {
        val seen = HashSet<String>()
        return routes
            .asSequence()
            .filter { it.id.isNotBlank() && seen.add(it.id) }
            .sortedWith(compareBy<Route>({ rank(it.kind) }, { label(it.kind, it.deviceName) }, { it.id }))
            .map { PresentedRoute(it.id, label(it.kind, it.deviceName), it.selected) }
            .toList()
    }

    fun label(kind: Kind, deviceName: String? = null): String {
        val safeName = deviceName?.trim()?.take(80)?.takeIf { it.isNotBlank() }
        return when (kind) {
            Kind.EARPIECE -> "Écouteur"
            Kind.BLUETOOTH -> safeName?.let { "Bluetooth · $it" } ?: "Bluetooth"
            Kind.WIRED_HEADSET -> safeName?.let { "Casque filaire · $it" } ?: "Casque filaire"
            Kind.SPEAKER -> "Haut-parleur"
            Kind.STREAMING -> safeName?.let { "Appareil externe · $it" } ?: "Appareil externe"
            Kind.UNKNOWN -> safeName ?: "Sortie audio"
        }
    }

    private fun rank(kind: Kind): Int = when (kind) {
        Kind.EARPIECE -> 0
        Kind.SPEAKER -> 1
        Kind.BLUETOOTH -> 2
        Kind.WIRED_HEADSET -> 3
        Kind.STREAMING -> 4
        Kind.UNKNOWN -> 5
    }
}

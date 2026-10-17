package com.sentinel.quantum.security

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.security.MessageDigest

/**
 * Phone-side Wear OS Data Layer adapter.
 *
 * This adapter reports only untrusted platform reachability. It never turns a connected Wear OS
 * node into Sentinel authentication and never exposes the raw Google Play services node id outside
 * this boundary. The secure Sentinel channel remains owned by the handshake/session layer.
 */
class WearOsDataLayerAdapter(
    context: Context,
    private val runtimeCoordinator: WearableRuntimeCoordinator = WearableRuntimeCoordinator()
) {
    data class Snapshot(
        val observations: List<WearableAdapterObservation>,
        val failureReason: String? = null
    )

    private val appContext = context.applicationContext
    private val nodeClient by lazy { Wearable.getNodeClient(appContext) }

    /**
     * Observes currently connected Data Layer nodes. Completion is asynchronous and bounded by
     * Google Play services; failures fail closed as an unavailable adapter observation.
     */
    fun observeConnectedNodes(onComplete: (Snapshot) -> Unit) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                val observations = nodes
                    .take(MAX_CONNECTED_NODES)
                    .map(::toObservation)
                observations.forEach { runtimeCoordinator.observePlatform(it) }
                onComplete(Snapshot(observations))
            }
            .addOnFailureListener {
                val unavailable = unavailableObservation()
                runtimeCoordinator.observePlatform(unavailable)
                onComplete(Snapshot(emptyList(), FAILURE_DATA_LAYER_UNAVAILABLE))
            }
    }

    internal fun toObservation(node: Node): WearableAdapterObservation = WearableAdapterObservation(
        stableIdHint = pseudonymousStableId(node.id),
        displayName = node.displayName?.trim()?.take(MAX_DISPLAY_NAME_CHARS),
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
        availability = WearableAdapterAvailability.AVAILABLE,
        observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

    private fun unavailableObservation(): WearableAdapterObservation = WearableAdapterObservation(
        stableIdHint = null,
        displayName = null,
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.NONE,
        availability = WearableAdapterAvailability.UNAVAILABLE,
        observedCapabilities = emptySet()
    )

    companion object {
        internal const val FAILURE_DATA_LAYER_UNAVAILABLE = "WEAR_OS_DATA_LAYER_UNAVAILABLE"
        private const val MAX_CONNECTED_NODES = 16
        private const val MAX_DISPLAY_NAME_CHARS = 80

        /** The raw Data Layer node id is never persisted or surfaced as Sentinel stable identity. */
        internal fun pseudonymousStableId(rawNodeId: String): String? {
            val normalized = rawNodeId.trim()
            if (normalized.isEmpty() || normalized.length > 512) return null
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("sentinel-wearos-node-v1:$normalized".toByteArray(Charsets.UTF_8))
            return "wearos-" + digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}

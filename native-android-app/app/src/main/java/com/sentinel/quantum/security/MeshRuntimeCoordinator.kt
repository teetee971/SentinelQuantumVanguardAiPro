package com.sentinel.quantum.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Application-facing composition root for Sentinel Private Mesh.
 *
 * The UI should use this coordinator instead of accessing the node credential store,
 * WireGuard identity store or tunnel backend directly.
 */
class MeshRuntimeCoordinator(
    context: Context,
    endpoint: String,
    allowedHosts: Set<String>
) {
    data class RuntimeSnapshot(
        val enrolled: Boolean,
        val identityPublicKey: String?,
        val identityFingerprint: String?,
        val tunnelState: MeshTunnelController.RuntimeState
    )

    data class PlanResult(
        val accepted: Boolean,
        val reason: String,
        val plan: MeshTunnelController.TunnelPlan? = null
    )

    data class DirectConnectResult(
        val accepted: Boolean,
        val reason: String,
        val tunnel: MeshTunnelController.OperationResult? = null
    )

    private val appContext = context.applicationContext
    private val credentialStore = MeshNodeCredentialStore(appContext)
    private val identityStore = MeshWireGuardIdentityStore(appContext)
    private val controlPlane = MeshControlPlaneClient(
        endpoint = endpoint,
        allowedHosts = allowedHosts,
        credentialStore = credentialStore
    )
    private val tunnelController = MeshTunnelController(
        context = appContext,
        identityStore = identityStore
    )

    fun ensureIdentity(): MeshWireGuardIdentityStore.Identity =
        identityStore.getOrCreateIdentity()

    fun enroll(nodeId: String, invitationCode: String): MeshControlPlaneClient.Result {
        val identity = ensureIdentity()
        return controlPlane.enroll(
            nodeId = nodeId,
            invitationCode = invitationCode,
            publicKeyFingerprint = identity.publicKeyFingerprint
        )
    }

    fun fetchSelf(): MeshControlPlaneClient.Result = controlPlane.fetchSelf()

    fun fetchPeers(): MeshControlPlaneClient.Result = controlPlane.fetchPeers()

    fun announceCandidates(
        endpoints: List<String>,
        wireGuardPort: Int,
        ttlMs: Long = 120_000L
    ): MeshControlPlaneClient.Result =
        controlPlane.announceCandidates(endpoints, wireGuardPort, ttlMs)

    fun fetchPath(targetNodeId: String, region: String? = null): MeshControlPlaneClient.Result =
        controlPlane.fetchPath(targetNodeId, region)

    fun createNegotiation(
        targetNodeId: String,
        preferredRegion: String? = null
    ): MeshControlPlaneClient.Result =
        controlPlane.createNegotiation(targetNodeId, preferredRegion)

    fun reportDirectResult(
        sessionId: String,
        candidateEndpoint: String,
        success: Boolean,
        latencyMs: Long? = null,
        error: String? = null
    ): MeshControlPlaneClient.Result =
        controlPlane.reportDirectResult(
            sessionId = sessionId,
            candidateEndpoint = candidateEndpoint,
            success = success,
            latencyMs = latencyMs,
            error = error
        )

    fun finalizeNegotiation(sessionId: String): MeshControlPlaneClient.Result =
        controlPlane.finalizeNegotiation(sessionId)

    fun claimRelay(negotiationId: String): MeshControlPlaneClient.Result =
        controlPlane.claimRelay(negotiationId)

    suspend fun buildDirectPlan(
        targetNodeId: String,
        region: String? = null
    ): PlanResult = withContext(Dispatchers.IO) {
        if (!MeshNodeCredentialStore.validNodeId(targetNodeId)) {
            return@withContext PlanResult(false, "MESH_TARGET_NODE_INVALID")
        }

        val localIdentity = identityStore.loadPublicIdentity()
            ?: return@withContext PlanResult(false, "MESH_IDENTITY_MISSING")

        val selfResult = controlPlane.fetchSelf()
        if (!selfResult.accepted || selfResult.body == null) {
            return@withContext PlanResult(false, selfResult.reason)
        }
        val peersResult = controlPlane.fetchPeers()
        if (!peersResult.accepted || peersResult.body == null) {
            return@withContext PlanResult(false, peersResult.reason)
        }
        val pathResult = controlPlane.fetchPath(targetNodeId, region)
        if (!pathResult.accepted || pathResult.body == null) {
            return@withContext PlanResult(false, pathResult.reason)
        }

        return@withContext runCatching {
            val selfNode = JSONObject(selfResult.body).getJSONObject("node")
            val selfNodeId = selfNode.getString("id")
            val registeredPublicKey = selfNode.getString("publicKey")
            if (registeredPublicKey != localIdentity.publicKeyBase64) {
                return@runCatching PlanResult(false, "MESH_IDENTITY_MISMATCH")
            }
            val localAddresses = jsonStringArray(selfNode.getJSONArray("meshAddresses"))
            if (localAddresses.isEmpty()) {
                return@runCatching PlanResult(false, "MESH_LOCAL_ADDRESS_MISSING")
            }

            val peers = JSONObject(peersResult.body).getJSONArray("peers")
            var peerObject: JSONObject? = null
            for (index in 0 until peers.length()) {
                val candidate = peers.getJSONObject(index)
                if (candidate.getString("id") == targetNodeId) {
                    peerObject = candidate
                    break
                }
            }
            val peer = peerObject
                ?: return@runCatching PlanResult(false, "MESH_TARGET_NOT_AUTHORIZED")

            val peerKey = peer.getString("publicKey")
            val peerAddresses = jsonStringArray(peer.getJSONArray("meshAddresses"))
            if (peerAddresses.isEmpty()) {
                return@runCatching PlanResult(false, "MESH_TARGET_ADDRESS_MISSING")
            }

            val path = JSONObject(pathResult.body)
            val mode = path.getString("mode")
            if (mode != "direct") {
                return@runCatching PlanResult(
                    false,
                    if (mode == "relay") "MESH_RELAY_PATH_REQUIRES_RELAY_CLIENT"
                    else "MESH_DIRECT_PATH_UNAVAILABLE"
                )
            }
            val endpoints = jsonStringArray(path.getJSONArray("targetEndpoints"))
            val endpoint = endpoints.firstOrNull()
                ?: return@runCatching PlanResult(false, "MESH_DIRECT_ENDPOINT_MISSING")

            val plan = MeshTunnelController.TunnelPlan(
                localNodeId = selfNodeId,
                localMeshAddresses = localAddresses,
                peers = listOf(
                    MeshTunnelController.PeerPlan(
                        nodeId = targetNodeId,
                        publicKeyBase64 = peerKey,
                        meshAddresses = peerAddresses,
                        endpoint = endpoint
                    )
                )
            )
            MeshTunnelController.buildAndValidateConfig(
                plan,
                identityStore.loadPrivateKeyBase64()
                    ?: return@runCatching PlanResult(false, "MESH_PRIVATE_KEY_UNAVAILABLE")
            )
            PlanResult(true, "MESH_DIRECT_PLAN_READY", plan)
        }.getOrElse {
            PlanResult(false, "MESH_CONTROL_PLANE_RESPONSE_INVALID")
        }
    }

    suspend fun connectDirectPeer(
        targetNodeId: String,
        region: String? = null
    ): DirectConnectResult {
        val planned = buildDirectPlan(targetNodeId, region)
        val plan = planned.plan
            ?: return DirectConnectResult(false, planned.reason)
        val result = tunnelController.connect(plan)
        return DirectConnectResult(
            accepted = result.state == MeshTunnelController.RuntimeState.CONNECTED,
            reason = result.reason,
            tunnel = result
        )
    }

    suspend fun connect(plan: MeshTunnelController.TunnelPlan): MeshTunnelController.OperationResult =
        tunnelController.connect(plan)

    suspend fun disconnect(): MeshTunnelController.OperationResult =
        tunnelController.disconnect()

    fun prepareConsentIntent() = tunnelController.prepareConsentIntent()

    fun snapshot(): RuntimeSnapshot {
        val identity = identityStore.loadPublicIdentity()
        return RuntimeSnapshot(
            enrolled = credentialStore.hasCredential(),
            identityPublicKey = identity?.publicKeyBase64,
            identityFingerprint = identity?.publicKeyFingerprint,
            tunnelState = tunnelController.currentState()
        )
    }

    fun clearEnrollment() {
        credentialStore.clear()
    }

    fun rotateIdentity(): MeshWireGuardIdentityStore.Identity {
        credentialStore.clear()
        return identityStore.rotate()
    }

    private fun jsonStringArray(array: JSONArray): List<String> {
        require(array.length() <= 128) { "MESH_JSON_ARRAY_TOO_LARGE" }
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.getString(index)
                require(value.length <= 512) { "MESH_JSON_VALUE_TOO_LARGE" }
                add(value)
            }
        }
    }
}

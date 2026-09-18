package com.sentinel.quantum.security

import android.content.Context

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
}

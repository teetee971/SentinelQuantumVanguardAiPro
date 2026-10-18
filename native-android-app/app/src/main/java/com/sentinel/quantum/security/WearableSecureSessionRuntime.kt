package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.VerifiedWearableHandshake

class WearableSecureSessionRuntime(
    private val runtimeCoordinator: WearableRuntimeCoordinator = WearableRuntimeCoordinator(),
    private val livenessTtlMs: Long = DEFAULT_LIVENESS_TTL_MS
) {
    private data class ActiveSession(
        val session: WearableSession,
        val proof: VerifiedWearableHandshake,
        var observation: WearableAdapterObservation,
        val activatedAtMs: Long,
        var lastAuthenticatedAtMs: Long? = null
    )

    private val lock = Any()
    private var active: ActiveSession? = null

    init {
        require(livenessTtlMs in 1_000L..300_000L) { "wearable liveness TTL out of bounds" }
    }

    /** Establishes session authority but deliberately keeps product truth locked until HPKE traffic. */
    fun activate(
        handshake: WearableSecureHandshakeResult,
        observation: WearableAdapterObservation,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val session = handshake.session ?: return false
        val proof = handshake.verifiedProof ?: return false
        if (handshake.state != WearableSecureHandshakeState.ACTIVE || !proof.secureSessionBound) return false
        if (nowMs < 0 || observation.availability != WearableAdapterAvailability.AVAILABLE) return false
        if (proof.sessionId != session.sessionId || proof.protocolVersion != session.protocolVersion) return false
        if (proof.capabilities != session.negotiatedCapabilities.map { it.name }.toSet()) return false

        val state = ActiveSession(session, proof, observation, activatedAtMs = nowMs)
        active = state
        runtimeCoordinator.observePlatform(observation, nowMs)
        true
    }

    fun onAuthenticatedTraffic(
        sessionId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val state = active ?: return false
        val previous = state.lastAuthenticatedAtMs
        if (sessionId != state.session.sessionId || nowMs < state.activatedAtMs ||
            (previous != null && nowMs < previous)
        ) return false
        state.lastAuthenticatedAtMs = nowMs
        publishAuthenticated(state, nowMs)
        true
    }

    fun observePlatform(
        observation: WearableAdapterObservation,
        nowMs: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val state = active
        if (state == null) {
            runtimeCoordinator.observePlatform(observation, nowMs)
        } else {
            state.observation = observation
            if (state.lastAuthenticatedAtMs == null) {
                runtimeCoordinator.observePlatform(observation, nowMs)
            } else {
                publishAuthenticated(state, nowMs)
            }
        }
    }

    fun hasFreshAuthenticatedProof(sessionId: String, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val state = active ?: return false
        val lastProof = state.lastAuthenticatedAtMs ?: return false
        sessionId == state.session.sessionId && nowMs >= lastProof &&
            nowMs - lastProof < livenessTtlMs
    }

    fun expireIfStale(nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val state = active ?: return false
        val lastProof = state.lastAuthenticatedAtMs ?: state.activatedAtMs
        val stale = nowMs < lastProof || nowMs - lastProof >= livenessTtlMs
        if (!stale) return false
        active = null
        runtimeCoordinator.revokeAuthenticatedChannel(nowMs)
        true
    }

    fun disconnect(nowMs: Long = System.currentTimeMillis()) = revoke(nowMs)
    fun authenticationFailure(nowMs: Long = System.currentTimeMillis()) = revoke(nowMs)

    private fun revoke(nowMs: Long) = synchronized(lock) {
        active = null
        runtimeCoordinator.revokeAuthenticatedChannel(nowMs)
    }

    private fun publishAuthenticated(state: ActiveSession, nowMs: Long) {
        val lastProof = state.lastAuthenticatedAtMs ?: return
        runtimeCoordinator.observeAuthenticatedChannel(
            observation = state.observation,
            channelProof = WearableChannelProof(
                state = WearableChannelState.ACTIVE,
                lastProofAtMs = lastProof,
                ttlMs = livenessTtlMs
            ),
            negotiatedCapabilities = state.session.negotiatedCapabilities,
            nowMs = nowMs
        )
    }

    companion object {
        const val DEFAULT_LIVENESS_TTL_MS = 30_000L
    }
}

internal object SentinelWearableRuntime {
    private val secureRuntime = WearableSecureSessionRuntime()

    fun observePlatform(observation: WearableAdapterObservation, nowMs: Long = System.currentTimeMillis()) =
        secureRuntime.observePlatform(observation, nowMs)
    fun activate(handshake: WearableSecureHandshakeResult, observation: WearableAdapterObservation, nowMs: Long = System.currentTimeMillis()): Boolean =
        secureRuntime.activate(handshake, observation, nowMs)
    fun authenticatedTraffic(sessionId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        secureRuntime.onAuthenticatedTraffic(sessionId, nowMs)
    fun disconnect(nowMs: Long = System.currentTimeMillis()) = secureRuntime.disconnect(nowMs)
    fun authenticationFailure(nowMs: Long = System.currentTimeMillis()) = secureRuntime.authenticationFailure(nowMs)
    fun expireIfStale(nowMs: Long = System.currentTimeMillis()): Boolean = secureRuntime.expireIfStale(nowMs)
    fun hasFreshAuthenticatedProof(sessionId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        secureRuntime.hasFreshAuthenticatedProof(sessionId, nowMs)
}

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
        var lastAuthenticatedAtMs: Long
    )

    private val lock = Any()
    private var active: ActiveSession? = null

    init {
        require(livenessTtlMs in 1_000L..300_000L) { "wearable liveness TTL out of bounds" }
    }

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

        val state = ActiveSession(session, proof, observation, nowMs)
        active = state
        publishAuthenticated(state, nowMs)
        true
    }

    fun onAuthenticatedTraffic(
        sessionId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val state = active ?: return false
        if (sessionId != state.session.sessionId || nowMs < state.lastAuthenticatedAtMs) return false
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
            publishAuthenticated(state, nowMs)
        }
    }

    fun expireIfStale(nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val state = active ?: return false
        val proof = channelProof(state)
        if (proof.isFresh(nowMs)) return false
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
        runtimeCoordinator.observeAuthenticatedChannel(
            observation = state.observation,
            channelProof = channelProof(state),
            negotiatedCapabilities = state.session.negotiatedCapabilities,
            nowMs = nowMs
        )
    }

    private fun channelProof(state: ActiveSession) = WearableChannelProof(
        state = WearableChannelState.ACTIVE,
        lastProofAtMs = state.lastAuthenticatedAtMs,
        ttlMs = livenessTtlMs
    )

    companion object {
        const val DEFAULT_LIVENESS_TTL_MS = 30_000L
    }
}

/** Process-local composition point. A process restart intentionally starts with no secure session. */
internal object SentinelWearableRuntime {
    private val secureRuntime = WearableSecureSessionRuntime()

    fun observePlatform(
        observation: WearableAdapterObservation,
        nowMs: Long = System.currentTimeMillis()
    ) = secureRuntime.observePlatform(observation, nowMs)

    fun activate(
        handshake: WearableSecureHandshakeResult,
        observation: WearableAdapterObservation,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = secureRuntime.activate(handshake, observation, nowMs)

    fun authenticatedTraffic(sessionId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        secureRuntime.onAuthenticatedTraffic(sessionId, nowMs)

    fun disconnect(nowMs: Long = System.currentTimeMillis()) = secureRuntime.disconnect(nowMs)

    fun authenticationFailure(nowMs: Long = System.currentTimeMillis()) =
        secureRuntime.authenticationFailure(nowMs)

    fun expireIfStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        secureRuntime.expireIfStale(nowMs)
}

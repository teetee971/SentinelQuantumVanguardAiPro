package com.sentinel.quantum.security

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-local production composition for the phone side of wearable protocol v2.
 *
 * Raw Data Layer node identifiers and HPKE session material remain memory-only. First contact
 * stops at an explicit confirmation request; only an already trusted identity can reconnect
 * without a new confirmation. CONNECTED truth is published only after an HPKE liveness echo is
 * authenticated by [PhoneWearableLivenessRuntime].
 */
internal object SentinelWearableSecureHandshakeRuntime {
    private const val LIVENESS_INTERVAL_MS = 15_000L

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var runtime: PhoneWearableSecureHandshakeRuntime? = null
    @Volatile private var livenessRuntime: PhoneWearableLivenessRuntime? = null
    @Volatile private var messageRuntime: PhoneWearableSecureMessageRuntime? = null

    private var livenessJob: Job? = null
    private var activeNodeId: String? = null

    fun start(
        context: Context,
        nodeId: String,
        observation: WearableAdapterObservation
    ): Boolean = ensureInstalled(context).start(
        nodeId = nodeId,
        observation = observation,
        capabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

    fun pendingConfirmation(): PhoneWearableSecureHandshakeRuntime.PendingConfirmation? =
        runtime?.pendingConfirmation()

    fun confirm(stableId: String, confirmedAtMs: Long = System.currentTimeMillis()): WearableSecureHandshakeResult? =
        runtime?.confirm(stableId, confirmedAtMs)

    fun reject(stableId: String): Boolean = runtime?.reject(stableId) == true

    fun disconnect(nodeId: String) {
        messageRuntime?.disconnect(nodeId)
        runtime?.disconnect(nodeId)
        val live = livenessRuntime
        synchronized(lock) {
            if (activeNodeId == nodeId) {
                livenessJob?.cancel()
                livenessJob = null
                activeNodeId = null
            }
        }
        live?.disconnect(nodeId)
    }

    private fun ensureInstalled(context: Context): PhoneWearableSecureHandshakeRuntime {
        runtime?.let { return it }
        synchronized(lock) {
            runtime?.let { return it }
            val appContext = context.applicationContext
            val messageClient = Wearable.getMessageClient(appContext)
            val authority = WearableSessionAuthority()
            val liveness = PhoneWearableLivenessRuntime(
                transport = PhoneWearableLivenessTransport { nodeId, path, payload, onFailure ->
                    messageClient.sendMessage(nodeId, path, payload)
                        .addOnFailureListener { onFailure() }
                },
                authenticatedTraffic = { sessionId, observedAtMs ->
                    SentinelWearableRuntime.authenticatedTraffic(sessionId, observedAtMs)
                },
                revokeAuthenticatedChannel = { observedAtMs ->
                    SentinelWearableRuntime.disconnect(observedAtMs)
                }
            )
            livenessRuntime = liveness
            val messages = PhoneWearableSecureMessageRuntime(
                authority = authority,
                livenessActive = liveness::isActive,
                freshAuthenticatedProof = SentinelWearableRuntime::hasFreshAuthenticatedProof
            )
            messageRuntime = messages

            val created = PhoneWearableSecureHandshakeRuntime(
                pairingStore = WearablePairingStore.forApplicationContext(appContext),
                replayGuard = WearableHandshakeReplayGuard(),
                sessionAuthority = authority,
                hpkeFactory = PhoneWearableSecureHandshakeRuntime.tinkHpkeFactory(),
                transport = PhoneWearableHandshakeTransport { nodeId, path, payload, onFailure ->
                    messageClient.sendMessage(nodeId, path, payload)
                        .addOnFailureListener { onFailure() }
                },
                activate = { nodeId, handshake, observation, material ->
                    val session = handshake.session
                    val wearablePublic = handshake.wearableHpkePublicKeyEncoded
                    if (session == null || wearablePublic == null) {
                        false
                    } else if (!SentinelWearableRuntime.activate(handshake, observation)) {
                        false
                    } else if (!liveness.activate(nodeId, session.sessionId, wearablePublic, material)) {
                        SentinelWearableRuntime.authenticationFailure()
                        false
                    } else if (!messages.activate(
                            nodeId,
                            requireNotNull(handshake.verifiedProof).stableId,
                            session,
                            wearablePublic,
                            material
                        )) {
                        liveness.disconnect(nodeId)
                        SentinelWearableRuntime.authenticationFailure()
                        false
                    } else {
                        startLivenessLoop(nodeId, session.sessionId, liveness)
                        true
                    }
                }
            )
            val handler = WearableSecureFrameHandler { sourceNodeId, path, frame ->
                when (path) {
                    WearableProtocolV2.HANDSHAKE_PATH -> created.handleHandshake(sourceNodeId, frame)
                    WearableProtocolV2.LIVENESS_PATH -> liveness.handleResponse(sourceNodeId, frame)
                    WearableProtocolV2.MESSAGE_PATH -> messages.handleFrame(sourceNodeId, frame)
                }
            }
            WearableSecureTransportInbox.install(handler)
            runtime = created
            return created
        }
    }

    private fun startLivenessLoop(
        nodeId: String,
        sessionId: String,
        liveness: PhoneWearableLivenessRuntime
    ) {
        synchronized(lock) {
            livenessJob?.cancel()
            activeNodeId = nodeId
            livenessJob = scope.launch {
                while (isActive && liveness.isActive(nodeId, sessionId)) {
                    liveness.sendProbe(nodeId)
                    delay(LIVENESS_INTERVAL_MS)
                }
            }
        }
    }
}

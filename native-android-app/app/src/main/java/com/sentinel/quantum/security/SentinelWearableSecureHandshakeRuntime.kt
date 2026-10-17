package com.sentinel.quantum.security

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard

/**
 * Process-local production composition for the phone side of wearable protocol v2.
 *
 * Raw Data Layer node identifiers and HPKE session material remain memory-only. First contact
 * stops at an explicit confirmation request; only an already trusted identity can reconnect
 * without a new confirmation. This component never publishes CONNECTED by receiving a handshake:
 * authenticated liveness remains mandatory in [WearableSecureSessionRuntime].
 */
internal object SentinelWearableSecureHandshakeRuntime {
    private val lock = Any()
    @Volatile private var runtime: PhoneWearableSecureHandshakeRuntime? = null
    @Volatile private var installedHandler: WearableSecureFrameHandler? = null

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

    private fun ensureInstalled(context: Context): PhoneWearableSecureHandshakeRuntime {
        runtime?.let { return it }
        synchronized(lock) {
            runtime?.let { return it }
            val appContext = context.applicationContext
            val messageClient = Wearable.getMessageClient(appContext)
            val authority = WearableSessionAuthority()
            val created = PhoneWearableSecureHandshakeRuntime(
                pairingStore = WearablePairingStore.forApplicationContext(appContext),
                replayGuard = WearableHandshakeReplayGuard(),
                sessionAuthority = authority,
                hpkeFactory = PhoneWearableSecureHandshakeRuntime.tinkHpkeFactory(),
                transport = PhoneWearableHandshakeTransport { nodeId, path, payload, onFailure ->
                    messageClient.sendMessage(nodeId, path, payload)
                        .addOnFailureListener { onFailure() }
                },
                activate = { handshake, observation, _ ->
                    SentinelWearableRuntime.activate(handshake, observation)
                }
            )
            val handler = WearableSecureFrameHandler { sourceNodeId, path, frame ->
                if (path == WearableProtocolV2.HANDSHAKE_PATH) {
                    created.handleHandshake(sourceNodeId, frame)
                }
            }
            WearableSecureTransportInbox.install(handler)
            installedHandler = handler
            runtime = created
            return created
        }
    }
}

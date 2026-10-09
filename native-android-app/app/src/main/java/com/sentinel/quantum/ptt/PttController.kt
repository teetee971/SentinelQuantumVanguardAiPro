package com.sentinel.quantum.ptt

/**
 * Transport-agnostic half-duplex push-to-talk state machine.
 *
 * This class does not own Android microphone permissions or audio routing. Those platform
 * concerns belong to a runtime adapter. A user press only requests publication; TRANSMITTING is
 * reported after the transport explicitly confirms LocalTransmissionStarted.
 */
class PttController(
    private val transport: PttTransport
) {
    var state: PttState = PttState.DISCONNECTED
        private set

    var lastFailure: String? = null
        private set

    // A callback from an earlier connection must not control a later session.
    private var listenerGeneration = 0L
    private var telecomCallPresent = false
    private var remoteAudioPending = false

    init {
        runCatching { registerListener() }
            .onFailure {
                lastFailure = "listener_registration_failed"
                state = PttState.ERROR
            }
    }

    private fun registerListener() {
        val generation = listenerGeneration
        transport.setEventListener { event ->
            if (generation == listenerGeneration) onTransportEvent(event)
        }
    }

    fun connect() {
        if (telecomCallPresent) {
            lastFailure = "telecom_call_active"
            return
        }
        if (state != PttState.DISCONNECTED && state != PttState.ERROR) return

        lastFailure = null
        remoteAudioPending = false
        state = PttState.CONNECTING
        listenerGeneration += 1

        if (runCatching { registerListener() }.isFailure) {
            lastFailure = "listener_registration_failed"
            state = PttState.ERROR
            disconnectTransportBestEffort()
            return
        }

        runCatching { transport.connect() }
            .onFailure {
                listenerGeneration += 1
                disconnectTransportBestEffort()
                lastFailure = "connect_failed"
                state = PttState.ERROR
            }
    }

    fun disconnect() {
        listenerGeneration += 1
        val stopFailed = state.requiresLocalTransmitStop() && !requestStopTransmissionSafely()
        val disconnectFailed = !disconnectTransportBestEffort()
        remoteAudioPending = false

        if (stopFailed || disconnectFailed) {
            lastFailure = if (stopFailed) "transmit_stop_request_failed" else "disconnect_failed"
            state = PttState.ERROR
        } else {
            state = PttState.DISCONNECTED
        }
    }

    /**
     * Telecom always has priority over PTT. A live carrier call tears down any PTT session and
     * blocks reconnect until Telecom reports no calls. Clearing the call never reconnects
     * automatically; another user action is required.
     */
    fun onTelecomCallPresenceChanged(present: Boolean) {
        telecomCallPresent = present
        if (!present) return

        if (state in setOf(
                PttState.CONNECTING,
                PttState.READY,
                PttState.TRANSMIT_REQUESTED,
                PttState.TRANSMITTING,
                PttState.TRANSMIT_STOPPING,
                PttState.RECEIVING
            )
        ) {
            disconnect()
        }

        if (state == PttState.DISCONNECTED) {
            lastFailure = "telecom_call_active"
        }
    }

    /** Returns true only when a transmit request was accepted or synchronously confirmed. */
    fun pressToTalk(): Boolean {
        if (telecomCallPresent || state != PttState.READY) return false

        // Move to the pending state before invoking the adapter. Some deterministic or LAN
        // transports may acknowledge synchronously from inside requestStartTransmitting().
        state = PttState.TRANSMIT_REQUESTED
        val accepted = requestStartTransmissionSafely()
        if (!accepted && state == PttState.TRANSMIT_REQUESTED) {
            state = PttState.READY
            lastFailure = "transmit_start_request_failed"
            return false
        }

        return state == PttState.TRANSMIT_REQUESTED || state == PttState.TRANSMITTING
    }

    fun releaseToTalk() {
        if (state != PttState.TRANSMIT_REQUESTED && state != PttState.TRANSMITTING) return

        // Set STOPPING first for the same synchronous-callback reason as pressToTalk().
        state = PttState.TRANSMIT_STOPPING
        val accepted = requestStopTransmissionSafely()
        if (!accepted && state == PttState.TRANSMIT_STOPPING) {
            failClosedAfterStopRequestFailure()
        }
    }

    private fun onTransportEvent(event: PttTransport.Event) {
        when (event) {
            PttTransport.Event.Connected -> {
                if (state != PttState.CONNECTING || telecomCallPresent) return
                lastFailure = null
                state = PttState.READY
            }

            is PttTransport.Event.Disconnected -> {
                // Once the current transport declares itself disconnected, invalidate its
                // generation immediately so any trailing media callbacks are stale.
                listenerGeneration += 1
                if (state.requiresLocalTransmitStop()) requestStopTransmissionSafely()
                remoteAudioPending = false
                lastFailure = event.reason
                state = PttState.DISCONNECTED
            }

            PttTransport.Event.LocalTransmissionStarted -> {
                when (state) {
                    PttState.TRANSMIT_REQUESTED -> {
                        if (telecomCallPresent) {
                            state = PttState.TRANSMIT_STOPPING
                            requestStopWhileStoppingOrFailClosed()
                        } else {
                            state = PttState.TRANSMITTING
                        }
                    }

                    // Release may have raced a slow microphone publication. Re-issue the stop
                    // request and never expose TRANSMITTING after the user already released.
                    PttState.TRANSMIT_STOPPING -> requestStopWhileStoppingOrFailClosed()
                    else -> Unit
                }
            }

            PttTransport.Event.LocalTransmissionStopped -> {
                when (state) {
                    PttState.TRANSMIT_STOPPING -> {
                        state = if (remoteAudioPending) PttState.RECEIVING else PttState.READY
                        remoteAudioPending = false
                    }

                    // A transport may autonomously revoke publication. Explicit stopped truth
                    // wins over stale local state and closes the microphone gate.
                    PttState.TRANSMITTING,
                    PttState.TRANSMIT_REQUESTED -> state = PttState.READY
                    else -> Unit
                }
            }

            PttTransport.Event.RemoteAudioStarted -> {
                when (state) {
                    PttState.READY -> state = PttState.RECEIVING

                    // Remote wins half-duplex arbitration. Do not claim RECEIVING until local
                    // microphone publication is explicitly confirmed stopped.
                    PttState.TRANSMIT_REQUESTED,
                    PttState.TRANSMITTING -> {
                        remoteAudioPending = true
                        state = PttState.TRANSMIT_STOPPING
                        requestStopWhileStoppingOrFailClosed()
                    }

                    PttState.TRANSMIT_STOPPING -> remoteAudioPending = true
                    else -> Unit
                }
            }

            PttTransport.Event.RemoteAudioStopped -> {
                if (state == PttState.RECEIVING) state = PttState.READY
                if (state == PttState.TRANSMIT_STOPPING) remoteAudioPending = false
            }

            is PttTransport.Event.Failure -> {
                listenerGeneration += 1
                if (state.requiresLocalTransmitStop()) requestStopTransmissionSafely()
                disconnectTransportBestEffort()
                remoteAudioPending = false
                lastFailure = event.reason
                state = PttState.ERROR
            }
        }
    }

    private fun requestStopWhileStoppingOrFailClosed() {
        val accepted = requestStopTransmissionSafely()
        if (!accepted && state == PttState.TRANSMIT_STOPPING) {
            failClosedAfterStopRequestFailure()
        }
    }

    private fun failClosedAfterStopRequestFailure() {
        listenerGeneration += 1
        disconnectTransportBestEffort()
        remoteAudioPending = false
        lastFailure = "transmit_stop_request_failed"
        state = PttState.ERROR
    }

    private fun requestStartTransmissionSafely(): Boolean =
        runCatching { transport.requestStartTransmitting() }.isSuccess

    private fun requestStopTransmissionSafely(): Boolean =
        runCatching { transport.requestStopTransmitting() }.isSuccess

    private fun disconnectTransportBestEffort(): Boolean =
        runCatching { transport.disconnect() }.isSuccess

    private fun PttState.requiresLocalTransmitStop(): Boolean =
        this == PttState.TRANSMIT_REQUESTED ||
            this == PttState.TRANSMITTING ||
            this == PttState.TRANSMIT_STOPPING
}

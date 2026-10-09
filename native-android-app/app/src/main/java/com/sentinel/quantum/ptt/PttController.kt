package com.sentinel.quantum.ptt

/**
 * Transport-agnostic half-duplex push-to-talk state machine.
 *
 * This class does not own Android microphone permissions or audio routing. Those
 * platform concerns must be wired by a runtime adapter and may only start media
 * after this controller has entered TRANSMITTING.
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
        state = PttState.CONNECTING
        listenerGeneration += 1

        val listenerRegistered = runCatching { registerListener() }.isSuccess
        if (!listenerRegistered) {
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

        val stopFailed = state == PttState.TRANSMITTING && !stopTransmissionSafely()
        val disconnectFailed = !disconnectTransportBestEffort()

        if (stopFailed || disconnectFailed) {
            lastFailure = if (stopFailed) "transmit_stop_failed" else "disconnect_failed"
            state = PttState.ERROR
        } else {
            state = PttState.DISCONNECTED
        }
    }

    /**
     * Telecom always has priority over PTT. A live call tears down any PTT session and
     * blocks reconnect until Telecom reports no calls. Clearing the call never reconnects
     * automatically; a new user action is required.
     */
    fun onTelecomCallPresenceChanged(present: Boolean) {
        telecomCallPresent = present
        if (!present) return

        if (state in setOf(
                PttState.CONNECTING,
                PttState.READY,
                PttState.TRANSMITTING,
                PttState.RECEIVING
            )
        ) {
            disconnect()
        }

        if (state == PttState.DISCONNECTED) {
            lastFailure = "telecom_call_active"
        }
    }

    fun pressToTalk(): Boolean {
        if (telecomCallPresent || state != PttState.READY) return false

        val started = runCatching { transport.startTransmitting() }.getOrDefault(false)
        if (!started) {
            lastFailure = "transmit_start_failed"
            return false
        }

        state = PttState.TRANSMITTING
        return true
    }

    fun releaseToTalk() {
        if (state != PttState.TRANSMITTING) return
        if (stopTransmissionSafely()) {
            state = PttState.READY
            return
        }

        // If muting the transport cannot be proven, invalidate the session and
        // tear it down best-effort. Never report READY while microphone state is unknown.
        listenerGeneration += 1
        disconnectTransportBestEffort()
        lastFailure = "transmit_stop_failed"
        state = PttState.ERROR
    }

    private fun onTransportEvent(event: PttTransport.Event) {
        when (event) {
            PttTransport.Event.Connected -> {
                // A late success from an already cancelled connection attempt must never
                // resurrect a READY session. READY is valid only while CONNECTING.
                if (state != PttState.CONNECTING || telecomCallPresent) return
                lastFailure = null
                state = PttState.READY
            }

            is PttTransport.Event.Disconnected -> {
                if (state == PttState.TRANSMITTING && !stopTransmissionSafely()) {
                    listenerGeneration += 1
                    disconnectTransportBestEffort()
                    lastFailure = "transmit_stop_failed"
                    state = PttState.ERROR
                    return
                }
                lastFailure = event.reason
                state = PttState.DISCONNECTED
            }

            PttTransport.Event.RemoteAudioStarted -> {
                // Remote media is meaningful only for a transport-confirmed live session.
                // Ignore stale callbacks received before connection or after failure/teardown.
                if (state != PttState.READY && state != PttState.TRANSMITTING) return

                // Remote wins a collision. If local capture cannot be stopped, fail closed
                // instead of pretending the channel is safely half-duplex.
                if (state == PttState.TRANSMITTING && !stopTransmissionSafely()) {
                    listenerGeneration += 1
                    disconnectTransportBestEffort()
                    lastFailure = "transmit_stop_failed"
                    state = PttState.ERROR
                    return
                }
                state = PttState.RECEIVING
            }

            PttTransport.Event.RemoteAudioStopped -> {
                if (state == PttState.RECEIVING) state = PttState.READY
            }

            is PttTransport.Event.Failure -> {
                if (state == PttState.TRANSMITTING && !stopTransmissionSafely()) {
                    listenerGeneration += 1
                    disconnectTransportBestEffort()
                    lastFailure = "transmit_stop_failed"
                    state = PttState.ERROR
                    return
                }
                lastFailure = event.reason
                state = PttState.ERROR
            }
        }
    }

    private fun stopTransmissionSafely(): Boolean =
        runCatching { transport.stopTransmitting() }.isSuccess

    private fun disconnectTransportBestEffort(): Boolean =
        runCatching { transport.disconnect() }.isSuccess
}

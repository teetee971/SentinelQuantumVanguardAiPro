package com.sentinel.quantum.ptt

/**
 * Transport-agnostic half-duplex push-to-talk state machine.
 *
 * This class does not own Android microphone permissions or audio routing. Those
 * platform concerns must be wired by a later runtime adapter and may only start
 * media after this controller has entered TRANSMITTING.
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

    init {
        registerListener()
    }

    private fun registerListener() {
        val generation = listenerGeneration
        transport.setEventListener { event ->
            if (generation == listenerGeneration) onTransportEvent(event)
        }
    }

    fun connect() {
        if (state != PttState.DISCONNECTED && state != PttState.ERROR) return
        lastFailure = null
        state = PttState.CONNECTING
        listenerGeneration += 1
        registerListener()
        transport.connect()
    }

    fun disconnect() {
        listenerGeneration += 1
        stopTransmissionIfNeeded()
        transport.disconnect()
        state = PttState.DISCONNECTED
    }

    fun pressToTalk(): Boolean {
        if (state != PttState.READY) return false

        val started = transport.startTransmitting()
        if (!started) {
            lastFailure = "transmit_start_failed"
            return false
        }

        state = PttState.TRANSMITTING
        return true
    }

    fun releaseToTalk() {
        if (state != PttState.TRANSMITTING) return
        transport.stopTransmitting()
        state = PttState.READY
    }

    private fun onTransportEvent(event: PttTransport.Event) {
        when (event) {
            PttTransport.Event.Connected -> {
                // A late success from an already cancelled connection attempt must never
                // resurrect a READY session. READY is valid only while CONNECTING.
                if (state != PttState.CONNECTING) return
                lastFailure = null
                state = PttState.READY
            }

            is PttTransport.Event.Disconnected -> {
                stopTransmissionIfNeeded()
                lastFailure = event.reason
                state = PttState.DISCONNECTED
            }

            PttTransport.Event.RemoteAudioStarted -> {
                // Remote media is meaningful only for a transport-confirmed live session.
                // Ignore stale callbacks received before connection or after failure/teardown.
                if (state != PttState.READY && state != PttState.TRANSMITTING) return
                // Remote wins a collision so local capture is stopped before receive state.
                stopTransmissionIfNeeded()
                state = PttState.RECEIVING
            }

            PttTransport.Event.RemoteAudioStopped -> {
                if (state == PttState.RECEIVING) state = PttState.READY
            }

            is PttTransport.Event.Failure -> {
                stopTransmissionIfNeeded()
                lastFailure = event.reason
                state = PttState.ERROR
            }
        }
    }

    private fun stopTransmissionIfNeeded() {
        if (state == PttState.TRANSMITTING) {
            transport.stopTransmitting()
        }
    }
}

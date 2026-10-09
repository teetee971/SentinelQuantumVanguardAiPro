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

    init {
        transport.setEventListener(::onTransportEvent)
    }

    fun connect() {
        if (state != PttState.DISCONNECTED && state != PttState.ERROR) return
        lastFailure = null
        state = PttState.CONNECTING
        transport.connect()
    }

    fun disconnect() {
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
                lastFailure = null
                state = PttState.READY
            }

            is PttTransport.Event.Disconnected -> {
                stopTransmissionIfNeeded()
                lastFailure = event.reason
                state = PttState.DISCONNECTED
            }

            PttTransport.Event.RemoteAudioStarted -> {
                // Enforce half-duplex even if a transport reports remote media
                // while the local floor was active.
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

package com.sentinel.quantum.ptt

/**
 * Runtime truth for the push-to-talk core.
 *
 * READY means that the transport has explicitly confirmed a live session. A local transmit
 * request is also split from actual microphone publication: TRANSMIT_REQUESTED and
 * TRANSMIT_STOPPING are transitional states, while TRANSMITTING is entered only after an
 * explicit LocalTransmissionStarted transport event. DISCONNECTING likewise means teardown was
 * requested but the transport has not yet confirmed Disconnected.
 */
enum class PttState {
    DISCONNECTED,
    CONNECTING,
    DISCONNECTING,
    READY,
    TRANSMIT_REQUESTED,
    TRANSMITTING,
    TRANSMIT_STOPPING,
    RECEIVING,
    ERROR
}

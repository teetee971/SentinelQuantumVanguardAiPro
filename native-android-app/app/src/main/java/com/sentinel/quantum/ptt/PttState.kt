package com.sentinel.quantum.ptt

/**
 * Runtime truth for the push-to-talk core.
 *
 * READY means that the transport has explicitly confirmed a live session. It must
 * never be inferred from a button press, requested connection, or UI lifecycle.
 */
enum class PttState {
    DISCONNECTED,
    CONNECTING,
    READY,
    TRANSMITTING,
    RECEIVING,
    ERROR
}

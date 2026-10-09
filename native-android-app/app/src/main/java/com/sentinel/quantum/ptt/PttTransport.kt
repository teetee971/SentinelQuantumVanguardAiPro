package com.sentinel.quantum.ptt

/**
 * Media/signaling boundary for push-to-talk.
 *
 * Implementations may use LiveKit, a LAN transport, or a deterministic test
 * transport. The controller only trusts explicit transport events for session
 * truth and never assumes that connect() succeeded synchronously.
 */
interface PttTransport {
    sealed interface Event {
        data object Connected : Event
        data class Disconnected(val reason: String? = null) : Event
        data object RemoteAudioStarted : Event
        data object RemoteAudioStopped : Event
        data class Failure(val reason: String) : Event
    }

    fun setEventListener(listener: (Event) -> Unit)
    fun connect()
    fun disconnect()
    fun startTransmitting(): Boolean
    fun stopTransmitting()
}

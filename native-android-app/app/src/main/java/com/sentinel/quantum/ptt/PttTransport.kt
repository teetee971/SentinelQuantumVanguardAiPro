package com.sentinel.quantum.ptt

/**
 * Media/signaling boundary for push-to-talk.
 *
 * Implementations may use LiveKit, a LAN transport, or a deterministic test transport. Commands
 * request work but do not prove media state synchronously. Session and microphone truth come only
 * from explicit events so coroutine/WebRTC adapters never have to block a UI thread or invent a
 * successful microphone transition before the underlying media API confirms it.
 */
interface PttTransport {
    sealed interface Event {
        data object Connected : Event
        data class Disconnected(val reason: String? = null) : Event
        data object LocalTransmissionStarted : Event
        data object LocalTransmissionStopped : Event
        data object RemoteAudioStarted : Event
        data object RemoteAudioStopped : Event
        data class Failure(val reason: String) : Event
    }

    fun setEventListener(listener: (Event) -> Unit)
    fun connect()
    fun disconnect()
    fun requestStartTransmitting()
    fun requestStopTransmitting()
}

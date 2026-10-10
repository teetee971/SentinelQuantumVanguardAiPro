package com.sentinel.quantum.voice

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground push-to-talk state machine.
 *
 * A successful [Transport.connect] must leave the microphone unpublished. Audio is published
 * only between [press] and [release]. Every transition that leaves TRANSMITTING attempts to
 * disable the microphone first so the controller fails closed.
 */
class PttSessionController(
    private val transport: Transport
) {
    interface Transport {
        /** Connect successfully only when the session is ready and microphone publication is off. */
        suspend fun connect(): Result<Unit>
        suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit>
        suspend fun disconnect()
    }

    enum class State {
        IDLE,
        CONNECTING,
        READY,
        TRANSMITTING,
        FAILED
    }

    private val mutex = Mutex()

    @Volatile
    private var state: State = State.IDLE

    fun state(): State = state

    suspend fun connect(): Result<Unit> = mutex.withLock {
        if (state != State.IDLE && state != State.FAILED) {
            return Result.failure(IllegalStateException("PTT session is already active"))
        }

        state = State.CONNECTING
        val connection = transport.connect()
        if (connection.isFailure) {
            state = State.FAILED
            return connection
        }

        state = State.READY
        Result.success(Unit)
    }

    suspend fun press(): Result<Unit> = mutex.withLock {
        if (state != State.READY) {
            return Result.failure(IllegalStateException("PTT is not ready"))
        }

        val enabled = transport.setMicrophoneEnabled(true)
        if (enabled.isFailure) {
            runCatching { transport.setMicrophoneEnabled(false) }
            state = State.FAILED
            return enabled
        }

        state = State.TRANSMITTING
        Result.success(Unit)
    }

    suspend fun release() = mutex.withLock {
        stopTransmissionLocked()
    }

    suspend fun cancel() = mutex.withLock {
        stopTransmissionLocked()
    }

    private suspend fun stopTransmissionLocked() {
        if (state != State.TRANSMITTING) return

        val disabled = transport.setMicrophoneEnabled(false)
        state = if (disabled.isSuccess) State.READY else State.FAILED
    }

    suspend fun disconnect() = mutex.withLock {
        runCatching { transport.setMicrophoneEnabled(false) }
        runCatching { transport.disconnect() }
        state = State.IDLE
    }
}

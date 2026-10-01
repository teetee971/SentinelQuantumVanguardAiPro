package com.sentinel.quantum.security

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process, PII-free delivery feedback for the currently visible SMS compose surface.
 *
 * Durable audit remains in PhonePrivateTimelineStore. This bus carries only an opaque send token,
 * the local Android provider row id, part coordinates and callback outcome; it never carries a
 * destination or message body.
 *
 * A bounded replay closes the race where Android reports SENT/DELIVERED before the compose screen
 * has received the opaque send token returned by SmsManager submission. The cache contains no
 * destination or message body, and consumers still filter strictly by sendToken. The bound covers
 * two callbacks per maximum multipart part plus an equal interleaving margin for other sends.
 */
object SmsDeliveryStatusBus {
    enum class Stage { SENT, DELIVERED }

    data class Event(
        val sendToken: Int,
        val providerMessageId: Long,
        val partIndex: Int,
        val partCount: Int,
        val stage: Stage,
        val successful: Boolean,
        val providerWriteSucceeded: Boolean = true
    )

    const val CALLBACK_REPLAY_CAPACITY = SmsCallbackProgress.MAX_PARTS * 4

    private val mutableEvents = MutableSharedFlow<Event>(
        replay = CALLBACK_REPLAY_CAPACITY,
        extraBufferCapacity = 64
    )
    val events = mutableEvents.asSharedFlow()

    fun publish(event: Event) {
        mutableEvents.tryEmit(event)
    }
}


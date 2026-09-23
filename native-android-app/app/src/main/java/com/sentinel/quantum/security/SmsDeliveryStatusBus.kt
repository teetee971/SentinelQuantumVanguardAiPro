package com.sentinel.quantum.security

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process, PII-free delivery feedback for the currently visible SMS compose surface.
 *
 * Durable audit remains in PhonePrivateTimelineStore. This bus carries only an opaque send token,
 * part coordinates and callback outcome; it never carries a destination or message body.
 *
 * replay=0 is deliberate: a newly opened screen must not receive stale callbacks from an earlier
 * send. Durable state lives in the Android SMS provider and the bounded callback store.
 */
object SmsDeliveryStatusBus {
    enum class Stage { SENT, DELIVERED }

    data class Event(
        val sendToken: Int,
        val partIndex: Int,
        val partCount: Int,
        val stage: Stage,
        val successful: Boolean
    )

    private val mutableEvents = MutableSharedFlow<Event>(replay = 0, extraBufferCapacity = 64)
    val events = mutableEvents.asSharedFlow()

    fun publish(event: Event) {
        mutableEvents.tryEmit(event)
    }
}

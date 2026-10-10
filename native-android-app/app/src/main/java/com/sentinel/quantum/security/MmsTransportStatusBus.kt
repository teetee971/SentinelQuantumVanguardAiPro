package com.sentinel.quantum.security

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process, PII-free transport feedback for the visible MMS compose surface.
 *
 * The durable journal/provider remain authoritative. This replayed bus only carries the opaque
 * transaction token, provider row id and Android transport result so a callback racing the sender
 * return or activity recomposition cannot leave the customer UI stuck on "submitted".
 */
object MmsTransportStatusBus {
    data class Event(
        val token: String,
        val providerMessageId: Long,
        val successful: Boolean,
        val providerWriteSucceeded: Boolean,
        val signal: String
    )

    private val mutableEvents = MutableSharedFlow<Event>(
        replay = 8,
        extraBufferCapacity = 32
    )
    val events = mutableEvents.asSharedFlow()

    fun publish(event: Event) {
        mutableEvents.tryEmit(event)
    }
}

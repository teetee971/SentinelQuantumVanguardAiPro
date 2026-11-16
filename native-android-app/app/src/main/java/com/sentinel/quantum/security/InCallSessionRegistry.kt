package com.sentinel.quantum.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One atomic UI observation, owned by one live Telecom service instance. */
internal class InCallSessionRegistry<T> {
    data class Session<T>(val primary: T?, val calls: List<T>, val serviceConnected: Boolean)
    private var owner: Any? = null
    private val mutable = MutableStateFlow(Session<T>(null, emptyList(), false))
    val sessions: StateFlow<Session<T>> = mutable.asStateFlow()

    @Synchronized fun attach(candidate: Any) {
        if (owner === candidate) return
        owner = candidate
        mutable.value = Session(null, emptyList(), false)
    }

    @Synchronized fun publish(candidate: Any, primary: T?, calls: List<T>): Boolean {
        if (owner !== candidate) return false
        mutable.value = Session(primary, calls.toList(), true)
        return true
    }

    @Synchronized fun detach(candidate: Any): Boolean {
        if (owner !== candidate) return false
        owner = null
        mutable.value = Session(null, emptyList(), false)
        return true
    }
}

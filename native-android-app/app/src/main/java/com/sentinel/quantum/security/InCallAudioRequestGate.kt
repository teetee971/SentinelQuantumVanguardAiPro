package com.sentinel.quantum.security

/** Main-thread gate: only the latest endpoint request for the current call may update UI. */
internal class InCallAudioRequestGate {
    data class Token(val callId: String, val generation: Long)
    private var generation = 0L
    fun begin(callId: String): Token = Token(callId, ++generation)
    fun invalidate() { generation++ }
    fun accepts(token: Token, currentCallId: String?): Boolean =
        token.generation == generation && token.callId == currentCallId
}

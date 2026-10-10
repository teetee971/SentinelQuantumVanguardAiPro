package com.sentinel.quantum.talkiewalkie

data class ChannelPolicy(
    val expiresAtMs: Long?,
    val canTalk: Boolean,
    val secureReplayAllowed: Boolean
) {
    fun isExpired(nowMs: Long): Boolean = expiresAtMs?.let { nowMs >= it } ?: false
}

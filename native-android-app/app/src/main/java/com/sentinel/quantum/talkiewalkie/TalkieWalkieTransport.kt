package com.sentinel.quantum.talkiewalkie

import java.net.URI

interface TalkieWalkieTransport {
    data class Credentials(
        val serverUrl: String,
        val accessToken: String
    ) {
        fun validate(): Boolean {
            val uri = runCatching { URI(serverUrl) }.getOrNull() ?: return false
            return uri.scheme.equals("wss", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                accessToken.length in 32..16_384 &&
                accessToken.none(Char::isWhitespace)
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    suspend fun connect(credentials: Credentials): Result<Unit>

    suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit>

    suspend fun disconnect()

    fun isMicrophoneEnabled(): Boolean

    fun connectionState(): ConnectionState
}

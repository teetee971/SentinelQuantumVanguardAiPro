package com.sentinel.quantum.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

interface VoiceSessionApi {
    fun createSession(
        accessToken: String,
        request: VoiceSessionRequest,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<VoiceSession>

    fun closeSession(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<Unit>
}

interface VoiceSessionTransportPort {
    fun configureVoice(effect: VoiceAddonPolicy.Effect)

    suspend fun connectForPushToTalk(
        credentials: SentinelLiveKitCallTransport.Credentials
    ): Result<PushToTalkSession>

    suspend fun disconnect()
}

/**
 * Owns the authenticated voice session independently of an Activity lifecycle.
 *
 * Keeping this ownership in one component is required before a foreground service can safely
 * host a PTT session. The coordinator never persists access tokens or LiveKit credentials.
 */
class VoiceSessionCoordinator(
    private val api: VoiceSessionApi,
    private val transport: VoiceSessionTransportPort
) {
    data class ActiveSession(
        val sessionId: String,
        val authAccessToken: String,
        val pushToTalk: PushToTalkSession
    )

    private val mutex = Mutex()
    private var activeSession: ActiveSession? = null

    suspend fun connect(
        accessToken: String,
        request: VoiceSessionRequest,
        effect: VoiceAddonPolicy.Effect
    ): Result<ActiveSession> = mutex.withLock {
        if (activeSession != null) {
            return Result.failure(IllegalStateException("VOICE_SESSION_ALREADY_CONNECTED"))
        }

        transport.configureVoice(effect)
        val created = withContext(Dispatchers.IO) {
            api.createSession(accessToken, request)
        }
        created.fold(
            onSuccess = { session ->
                val connected = transport.connectForPushToTalk(
                    SentinelLiveKitCallTransport.Credentials(
                        serverUrl = session.transport.serverUrl,
                        accessToken = session.transport.accessToken
                    )
                )
                connected.fold(
                    onSuccess = { pushToTalk ->
                        val active = ActiveSession(
                            sessionId = session.sessionId,
                            authAccessToken = accessToken,
                            pushToTalk = pushToTalk
                        )
                        activeSession = active
                        Result.success(active)
                    },
                    onFailure = { failure ->
                        runCatching { transport.disconnect() }
                        withContext(Dispatchers.IO) {
                            api.closeSession(accessToken, session.sessionId)
                        }
                        Result.failure(failure)
                    }
                )
            },
            onFailure = { failure -> Result.failure(failure) }
        )
    }

    suspend fun disconnect(): Result<Unit> = mutex.withLock {
        val active = activeSession ?: return Result.success(Unit)
        activeSession = null

        val closeResult = runCatching { active.pushToTalk.close() }
            .getOrElse { Result.failure(it) }
        runCatching { transport.disconnect() }
        val remoteResult = withContext(Dispatchers.IO) {
            api.closeSession(active.authAccessToken, active.sessionId)
        }
        if (closeResult.isFailure) closeResult else remoteResult
    }

    fun isConnected(): Boolean = activeSession != null
}

package com.sentinel.quantum.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionConnectFailureTest {

    @Test
    fun ordinaryTransportExceptionCleansRemoteSession() = runBlocking {
        var remoteCloseCalls = 0
        var transportDisconnectCalls = 0

        val api = object : VoiceSessionApi {
            override fun createSession(
                accessToken: String,
                request: VoiceSessionRequest,
                idempotencyKey: String
            ): Result<VoiceSession> = Result.success(
                VoiceSession(
                    sessionId = "failure-test-session",
                    transport = VoiceSessionTransport(
                        serverUrl = "wss://voice.example.test",
                        accessToken = "x".repeat(64),
                        expiresAt = "2026-12-31T00:00:00Z"
                    ),
                    voiceTransformRequired = true,
                    pttRequired = true
                )
            )

            override fun closeSession(
                accessToken: String,
                sessionId: String,
                idempotencyKey: String
            ): Result<Unit> {
                remoteCloseCalls++
                return Result.success(Unit)
            }
        }

        val transport = object : VoiceSessionTransportPort {
            override fun configureVoice(effect: VoiceAddonPolicy.Effect) = Unit

            override suspend fun connectForPushToTalk(
                credentials: SentinelLiveKitCallTransport.Credentials
            ): Result<PushToTalkSession> {
                throw IllegalStateException("Simulated LiveKit failure")
            }

            override suspend fun disconnect() {
                transportDisconnectCalls++
            }
        }

        val coordinator = VoiceSessionCoordinator(api, transport)

        val result = coordinator.connect(
            accessToken = "auth-token",
            request = VoiceSessionRequest(
                "demo-room",
                "device-1",
                "1.0.6"
            ),
            effect = VoiceAddonPolicy.Effect.DEEP
        )

        assertTrue(result.isFailure)
        assertEquals(1, remoteCloseCalls)
        assertEquals(1, transportDisconnectCalls)
        assertFalse(coordinator.isConnected())
    }
}

package com.sentinel.quantum.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionCoordinatorTest {
    @Test
    fun connects_and_keeps_the_live_session_owned_outside_the_activity() = runBlocking {
        val api = FakeSessionApi()
        val transport = FakeSessionTransport()
        val coordinator = VoiceSessionCoordinator(api, transport)

        val result = coordinator.connect(
            accessToken = "auth-token",
            request = VoiceSessionRequest("demo-room", "device-1", "1.0.6"),
            effect = VoiceAddonPolicy.Effect.DEEP
        )

        assertTrue(result.isSuccess)
        assertEquals("session-1", result.getOrThrow().sessionId)
        assertEquals(VoiceAddonPolicy.Effect.DEEP, transport.configuredEffect)
        assertEquals(1, api.createCalls)
        assertEquals(1, transport.connectCalls)
        assertTrue(coordinator.isConnected())
    }

    @Test
    fun cancellation_after_backend_creation_closes_remote_and_transport_state() = runBlocking {
        val api = FakeSessionApi()
        val transport = CancellingSessionTransport()
        val coordinator = VoiceSessionCoordinator(api, transport)

        val connectJob = launch {
            coordinator.connect(
                accessToken = "auth-token",
                request = VoiceSessionRequest("demo-room", "device-1", "1.0.6"),
                effect = VoiceAddonPolicy.Effect.DEEP
            )
        }

        transport.connectStarted.await()
        connectJob.cancelAndJoin()

        assertEquals(1, api.createCalls)
        assertEquals(1, api.closeCalls)
        assertEquals(1, transport.disconnectCalls)
        assertFalse(coordinator.isConnected())
    }

    private class FakeSessionApi : VoiceSessionApi {
        var createCalls = 0
        var closeCalls = 0

        override fun createSession(
            accessToken: String,
            request: VoiceSessionRequest,
            idempotencyKey: String
        ): Result<VoiceSession> {
            createCalls += 1
            return Result.success(
                VoiceSession(
                    sessionId = "session-1",
                    transport = VoiceSessionTransport(
                        serverUrl = "wss://livekit.example.test",
                        accessToken = "a".repeat(32),
                        expiresAt = "2026-10-11T00:00:00Z"
                    ),
                    voiceTransformRequired = true,
                    pttRequired = true
                )
            )
        }

        override fun closeSession(
            accessToken: String,
            sessionId: String,
            idempotencyKey: String
        ): Result<Unit> {
            closeCalls += 1
            return Result.success(Unit)
        }
    }

    private class FakeSessionTransport : VoiceSessionTransportPort {
        var configuredEffect: VoiceAddonPolicy.Effect? = null
        var connectCalls = 0

        override fun configureVoice(effect: VoiceAddonPolicy.Effect) {
            configuredEffect = effect
        }

        override suspend fun connectForPushToTalk(
            credentials: SentinelLiveKitCallTransport.Credentials
        ): Result<PushToTalkSession> {
            connectCalls += 1
            return Result.success(PushToTalkSession { Result.success(Unit) })
        }

        override suspend fun disconnect() = Unit
    }

    private class CancellingSessionTransport : VoiceSessionTransportPort {
        val connectStarted = CompletableDeferred<Unit>()
        var disconnectCalls = 0

        override fun configureVoice(effect: VoiceAddonPolicy.Effect) = Unit

        override suspend fun connectForPushToTalk(
            credentials: SentinelLiveKitCallTransport.Credentials
        ): Result<PushToTalkSession> {
            connectStarted.complete(Unit)
            awaitCancellation()
        }

        override suspend fun disconnect() {
            disconnectCalls += 1
        }
    }
}

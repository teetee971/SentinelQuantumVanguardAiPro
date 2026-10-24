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

    @Test
    fun cancellation_during_disconnect_still_closes_transport_and_remote_session() = runBlocking {
        val api = FakeSessionApi()
        val transport = CancellingDisconnectTransport()
        val coordinator = VoiceSessionCoordinator(api, transport)

        val active = coordinator.connect(
            accessToken = "auth-token",
            request = VoiceSessionRequest("demo-room", "device-1", "1.0.6"),
            effect = VoiceAddonPolicy.Effect.DEEP
        ).getOrThrow()
        assertTrue(active.pushToTalk.press().isSuccess)

        val disconnectJob = launch {
            coordinator.disconnect()
        }

        transport.muteStarted.await()
        disconnectJob.cancelAndJoin()

        assertEquals(1, api.closeCalls)
        assertEquals(1, transport.disconnectCalls)
        assertFalse(coordinator.isConnected())
    }

    @Test
    fun thrownTransportConnectionErrorClosesRemoteSession() = runBlocking {
        val api = FakeSessionApi()
        val transport = ThrowingSessionTransport()
        val coordinator = VoiceSessionCoordinator(api, transport)

        val result = coordinator.connect(
            accessToken = "auth-token",
            request = VoiceSessionRequest("demo-room", "device-1", "1.0.6"),
            effect = VoiceAddonPolicy.Effect.DEEP
        )

        assertTrue(result.isFailure)
        assertEquals(1, api.createCalls)
        assertEquals(1, api.closeCalls)
        assertEquals(1, transport.disconnectCalls)
        assertFalse(coordinator.isConnected())
    }

    private class ThrowingSessionTransport : VoiceSessionTransportPort {
        var disconnectCalls = 0

        override fun configureVoice(effect: VoiceAddonPolicy.Effect) = Unit

        override suspend fun connectForPushToTalk(
            credentials: SentinelLiveKitCallTransport.Credentials
        ): Result<PushToTalkSession> {
            throw IllegalStateException("TRANSPORT_CONNECT_FAILED")
        }

        override suspend fun disconnect() {
            disconnectCalls++
        }
    }

    @Test
    fun disconnectReportsTransportFailureAndStillClosesRemote() = runBlocking {
        val api = FakeSessionApi()
        val transport = FailingDisconnectTransport()
        val coordinator = VoiceSessionCoordinator(api, transport)

        assertTrue(coordinator.connect(
            accessToken = "auth-token",
            request = VoiceSessionRequest("demo-room", "device-1", "1.0.6"),
            effect = VoiceAddonPolicy.Effect.DEEP
        ).isSuccess)

        val result = coordinator.disconnect()

        assertTrue(result.isFailure)
        assertEquals("TRANSPORT_DISCONNECT_FAILED", result.exceptionOrNull()?.message)
        assertEquals(1, transport.disconnectCalls)
        assertEquals(1, api.closeCalls)
        assertFalse(coordinator.isConnected())
    }

    private class FailingDisconnectTransport : VoiceSessionTransportPort {
        var disconnectCalls = 0

        override fun configureVoice(effect: VoiceAddonPolicy.Effect) = Unit

        override suspend fun connectForPushToTalk(
            credentials: SentinelLiveKitCallTransport.Credentials
        ): Result<PushToTalkSession> = Result.success(
            PushToTalkSession { Result.success(Unit) }
        )

        override suspend fun disconnect() {
            disconnectCalls++
            throw IllegalStateException("TRANSPORT_DISCONNECT_FAILED")
        }
    }

    @Test
    fun failedRemoteCloseBlocksReconnectUntilExplicitRetry() = runBlocking {
        val api = RetriableCloseSessionApi()
        val coordinator = VoiceSessionCoordinator(api, FakeSessionTransport())
        val request = VoiceSessionRequest("demo-room", "device-1", "1.0.6")

        assertTrue(coordinator.connect("auth-token", request, VoiceAddonPolicy.Effect.DEEP).isSuccess)
        assertTrue(coordinator.disconnect().isFailure)
        assertFalse(coordinator.isConnected())

        val blocked = coordinator.connect("auth-token", request, VoiceAddonPolicy.Effect.DEEP)
        assertTrue(blocked.isFailure)
        assertEquals("VOICE_REMOTE_CLOSE_PENDING", blocked.exceptionOrNull()?.message)
        assertEquals(1, api.createCalls)

        api.allowClose = true
        assertTrue(coordinator.disconnect().isSuccess)
        assertEquals(2, api.closeCalls)
        assertTrue(coordinator.connect("auth-token", request, VoiceAddonPolicy.Effect.DEEP).isSuccess)
        assertEquals(2, api.createCalls)
    }

    private class RetriableCloseSessionApi : VoiceSessionApi {
        private val delegate = FakeSessionApi()
        var createCalls = 0
        var closeCalls = 0
        var allowClose = false

        override fun createSession(
            accessToken: String,
            request: VoiceSessionRequest,
            idempotencyKey: String
        ): Result<VoiceSession> {
            createCalls++
            return delegate.createSession(accessToken, request, idempotencyKey)
        }

        override fun closeSession(
            accessToken: String,
            sessionId: String,
            idempotencyKey: String
        ): Result<Unit> {
            closeCalls++
            return if (allowClose) Result.success(Unit)
                else Result.failure(IllegalStateException("REMOTE_CLOSE_FAILED"))
        }
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

    private class CancellingDisconnectTransport : VoiceSessionTransportPort {
        val muteStarted = CompletableDeferred<Unit>()
        var disconnectCalls = 0

        override fun configureVoice(effect: VoiceAddonPolicy.Effect) = Unit

        override suspend fun connectForPushToTalk(
            credentials: SentinelLiveKitCallTransport.Credentials
        ): Result<PushToTalkSession> = Result.success(
            PushToTalkSession { enabled ->
                if (enabled) {
                    Result.success(Unit)
                } else {
                    muteStarted.complete(Unit)
                    awaitCancellation()
                }
            }
        )

        override suspend fun disconnect() {
            disconnectCalls += 1
        }
    }
}

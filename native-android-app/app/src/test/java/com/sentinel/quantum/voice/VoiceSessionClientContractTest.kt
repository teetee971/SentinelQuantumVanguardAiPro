package com.sentinel.quantum.voice

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class VoiceSessionClientContractTest {
    @Test
    fun accepts_the_render_root_endpoint_and_appends_the_voice_session_path() {
        val endpoint = VoiceSessionClient.endpoint(VoiceSessionClient.DEFAULT_ENDPOINT)

        assertEquals(
            "/v1/voice/sessions",
            endpoint.encodedPath
        )
    }

    @Test
    fun builds_the_authenticated_ptt_room_request() {
        val request = VoiceSessionRequest(
            roomId = "demo-room",
            deviceId = "android-device-1",
            appVersion = "1.0.6"
        )

        val json = JSONObject(request.toJson())

        assertEquals("PTT_ROOM", json.getString("channel"))
        assertEquals("sentinel_room", json.getJSONObject("destination").getString("type"))
        assertEquals("demo-room", json.getJSONObject("destination").getString("room_id"))
        assertEquals("android-device-1", json.getJSONObject("client").getString("device_id"))
        assertEquals("1.0.6", json.getJSONObject("client").getString("app_version"))
        assertEquals("android", json.getJSONObject("client").getString("platform"))
    }

    @Test
    fun accepts_only_secure_livekit_transport_credentials() {
        assertTrue(
            VoiceSessionTransport(
                serverUrl = "wss://livekit.example.test",
                accessToken = "a".repeat(32),
                expiresAt = "2026-10-11T00:00:00Z"
            ).isValid()
        )
    }

    @Test
    fun retries_a_transient_timeout_with_the_same_idempotency_key() {
        val idempotencyKeys = mutableListOf<String>()
        var attempts = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                idempotencyKeys += requireNotNull(chain.request().header("Idempotency-Key"))
                attempts += 1
                if (attempts == 1) {
                    throw SocketTimeoutException("timeout")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body(
                        """
                        {
                          "session_id": "session-1",
                          "transport": {
                            "server_url": "wss://livekit.example.test",
                            "access_token": "${"a".repeat(32)}",
                            "expires_at": "2026-10-11T00:00:00Z"
                          },
                          "media": {
                            "voice_transform_required": true,
                            "ptt_required": true
                          }
                        }
                        """.trimIndent().toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()

        val result = VoiceSessionClient(client = client).createSession(
            accessToken = "auth-token",
            request = VoiceSessionRequest("demo-room", "device-1", "1.0.6"),
            idempotencyKey = "idempotency-key-1234"
        )

        assertTrue(result.isSuccess)
        assertEquals(2, attempts)
        assertEquals(idempotencyKeys[0], idempotencyKeys[1])
    }
}

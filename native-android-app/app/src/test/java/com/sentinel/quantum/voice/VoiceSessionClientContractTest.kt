package com.sentinel.quantum.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

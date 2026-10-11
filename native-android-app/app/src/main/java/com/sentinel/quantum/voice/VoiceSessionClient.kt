package com.sentinel.quantum.voice

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

data class VoiceSessionRequest(
    val roomId: String,
    val deviceId: String,
    val appVersion: String
) {
    fun toJson(): String = JSONObject()
        .put("channel", "PTT_ROOM")
        .put(
            "destination",
            JSONObject()
                .put("type", "sentinel_room")
                .put("room_id", roomId)
        )
        .put(
            "client",
            JSONObject()
                .put("device_id", deviceId)
                .put("app_version", appVersion)
                .put("platform", "android")
        )
        .toString()
}

data class VoiceSessionTransport(
    val serverUrl: String,
    val accessToken: String,
    val expiresAt: String
) {
    fun isValid(): Boolean {
        val uri = runCatching { URI(serverUrl) }.getOrNull() ?: return false
        return uri.scheme.equals("wss", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            accessToken.length in 32..16_384 &&
            accessToken.none(Char::isWhitespace) &&
            expiresAt.isNotBlank()
    }
}

data class VoiceSession(
    val sessionId: String,
    val transport: VoiceSessionTransport,
    val voiceTransformRequired: Boolean,
    val pttRequired: Boolean
)

/** Authenticated client for ephemeral Sentinel LiveKit session credentials. */
class VoiceSessionClient(
    endpointUrl: String = DEFAULT_ENDPOINT,
    private val client: OkHttpClient = defaultClient()
) {
    private val endpoint: HttpUrl = endpoint(endpointUrl)

    fun createSession(
        accessToken: String,
        request: VoiceSessionRequest,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<VoiceSession> {
        if (accessToken.isBlank() || accessToken.any(Char::isWhitespace)) {
            return Result.failure(IllegalArgumentException("VOICE_ACCESS_TOKEN_INVALID"))
        }
        if (idempotencyKey.length !in 16..256 || idempotencyKey.any(Char::isWhitespace)) {
            return Result.failure(IllegalArgumentException("VOICE_IDEMPOTENCY_KEY_INVALID"))
        }
        if (!ROOM_ID_PATTERN.matches(request.roomId) || request.deviceId.isBlank()) {
            return Result.failure(IllegalArgumentException("VOICE_SESSION_REQUEST_INVALID"))
        }

        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("Authorization", "Bearer $accessToken")
            .header("Idempotency-Key", idempotencyKey)
            .post(request.toJson().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(httpRequest).execute().use { response ->
                if (response.request.url != endpoint) {
                    return Result.failure(SecurityException("VOICE_SESSION_ORIGIN_CHANGED"))
                }
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("VOICE_SESSION_HTTP_${response.code}"))
                }
                val body = response.body
                if (body.contentLength() !in 2..MAX_RESPONSE_BYTES) {
                    return Result.failure(IllegalStateException("VOICE_SESSION_RESPONSE_SIZE_INVALID"))
                }
                parseSession(body.string())
            }
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun closeSession(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<Unit> {
        if (accessToken.isBlank() || accessToken.any(Char::isWhitespace)) {
            return Result.failure(IllegalArgumentException("VOICE_ACCESS_TOKEN_INVALID"))
        }
        if (sessionId.isBlank() || sessionId.length > 128) {
            return Result.failure(IllegalArgumentException("VOICE_SESSION_ID_INVALID"))
        }
        if (idempotencyKey.length !in 16..256 || idempotencyKey.any(Char::isWhitespace)) {
            return Result.failure(IllegalArgumentException("VOICE_IDEMPOTENCY_KEY_INVALID"))
        }

        val closeEndpoint = endpoint.newBuilder()
            .addPathSegment(sessionId)
            .addPathSegment("close")
            .build()
        val httpRequest = Request.Builder()
            .url(closeEndpoint)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .header("Idempotency-Key", idempotencyKey)
            .post("".toRequestBody(null))
            .build()

        return try {
            client.newCall(httpRequest).execute().use { response ->
                if (response.request.url != closeEndpoint) {
                    return Result.failure(SecurityException("VOICE_SESSION_ORIGIN_CHANGED"))
                }
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("VOICE_SESSION_CLOSE_HTTP_${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private fun parseSession(raw: String): Result<VoiceSession> = runCatching {
        val root = JSONObject(raw)
        val transportJson = root.getJSONObject("transport")
        val mediaJson = root.getJSONObject("media")
        val session = VoiceSession(
            sessionId = root.getString("session_id"),
            transport = VoiceSessionTransport(
                serverUrl = transportJson.getString("server_url"),
                accessToken = transportJson.getString("access_token"),
                expiresAt = transportJson.getString("expires_at")
            ),
            voiceTransformRequired = mediaJson.getBoolean("voice_transform_required"),
            pttRequired = mediaJson.getBoolean("ptt_required")
        )
        require(session.sessionId.isNotBlank() && session.transport.isValid()) {
            "VOICE_SESSION_RESPONSE_INVALID"
        }
        require(session.voiceTransformRequired && session.pttRequired) {
            "VOICE_SESSION_MEDIA_POLICY_INVALID"
        }
        session
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    companion object {
        const val DEFAULT_ENDPOINT = "https://sentinel-voice-api.onrender.com"
        private const val VOICE_HOST = "sentinel-voice-api.onrender.com"
        private const val MAX_RESPONSE_BYTES = 65_536L
        private val ROOM_ID_PATTERN = Regex("^[A-Za-z0-9._:-]{1,128}$")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun endpoint(endpointUrl: String): HttpUrl {
            val parsed = endpointUrl.trim().trimEnd('/').toHttpUrlOrNull()
                ?: throw IllegalArgumentException("VOICE_ENDPOINT_INVALID")
            require(
                parsed.isHttps &&
                    parsed.port == 443 &&
                    parsed.host == VOICE_HOST &&
                    parsed.encodedPath.isEmpty() &&
                    parsed.query == null &&
                    parsed.fragment == null &&
                    parsed.username.isEmpty() &&
                    parsed.password.isEmpty()
            ) { "VOICE_ENDPOINT_NOT_ALLOWED" }
            return parsed.newBuilder().encodedPath("/v1/voice/sessions").build()
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

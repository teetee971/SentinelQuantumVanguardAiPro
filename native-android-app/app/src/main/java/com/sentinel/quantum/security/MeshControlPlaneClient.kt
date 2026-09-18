package com.sentinel.quantum.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MeshControlPlaneClient(
    endpoint: String,
    allowedHosts: Set<String>,
    private val credentialStore: MeshNodeCredentialStore,
    private val client: OkHttpClient = defaultClient()
) {
    data class Result(
        val accepted: Boolean,
        val reason: String,
        val statusCode: Int? = null,
        val body: String? = null
    )

    private val baseUrl: HttpUrl

    init {
        val parsed = endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("MESH_ENDPOINT_INVALID")
        val hosts = allowedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        require(hosts.isNotEmpty() && hosts.size <= MAX_ALLOWED_HOSTS) { "MESH_ALLOWED_HOSTS_INVALID" }
        require(parsed.isHttps && parsed.host.lowercase() in hosts) { "MESH_ENDPOINT_NOT_ALLOWED" }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "MESH_ENDPOINT_CREDENTIALS_FORBIDDEN" }
        require(parsed.fragment == null && parsed.query == null) { "MESH_ENDPOINT_COMPONENTS_INVALID" }
        require(!client.followRedirects && !client.followSslRedirects) { "MESH_REDIRECTS_MUST_BE_DISABLED" }
        baseUrl = parsed.newBuilder()
            .encodedPath(if (parsed.encodedPath.endsWith("/")) parsed.encodedPath else parsed.encodedPath + "/")
            .build()
    }

    fun enroll(
        nodeId: String,
        invitationCode: String,
        publicKeyFingerprint: String
    ): Result {
        if (!MeshNodeCredentialStore.validNodeId(nodeId)) {
            return Result(false, "MESH_NODE_ID_INVALID")
        }
        if (!validInvitationCode(invitationCode)) {
            return Result(false, "MESH_INVITATION_CODE_INVALID")
        }
        val fingerprint = publicKeyFingerprint.trim().lowercase()
        if (!fingerprint.matches(Regex("[a-f0-9]{64}"))) {
            return Result(false, "MESH_PUBLIC_KEY_FINGERPRINT_INVALID")
        }

        val body = """{"nodeId":${quote(nodeId)},"code":${quote(invitationCode)},"publicKeyFingerprint":${quote(fingerprint)}}"""
        val result = postUnauthenticated("v1/enroll", body)
        if (!result.accepted || result.body == null) return result

        val credential = parseEnrollmentCredential(result.body)
            ?: return Result(false, "MESH_ENROLLMENT_RESPONSE_INVALID", result.statusCode)

        if (credential.nodeId != nodeId) {
            return Result(false, "MESH_ENROLLMENT_NODE_MISMATCH", result.statusCode)
        }
        return runCatching {
            credentialStore.save(credential.nodeId, credential.token)
            Result(true, "MESH_ENROLLED", result.statusCode, result.body)
        }.getOrElse {
            Result(false, "MESH_CREDENTIAL_STORE_FAILED", result.statusCode)
        }
    }

    fun announceCandidates(
        endpoints: List<String>,
        wireGuardPort: Int,
        ttlMs: Long = 120_000L
    ): Result {
        if (endpoints.size > MAX_CANDIDATES) return Result(false, "MESH_TOO_MANY_CANDIDATES")
        if (wireGuardPort !in 1..65535) return Result(false, "MESH_WIREGUARD_PORT_INVALID")
        if (ttlMs !in 10_000L..600_000L) return Result(false, "MESH_TTL_INVALID")
        if (endpoints.any { !validEndpoint(it) }) return Result(false, "MESH_CANDIDATE_INVALID")

        val body = """{"endpoints":[${endpoints.joinToString(",") { quote(it) }}],"wireGuardPort":$wireGuardPort,"ttlMs":$ttlMs}"""
        return post("v1/node/transport/candidates", body)
    }

    fun fetchSelf(): Result = get("v1/node/self")

    fun fetchPeers(): Result = get("v1/node/peers")

    fun fetchNatMapping(): Result = get("v1/node/nat-mapping")

    fun fetchPath(targetNodeId: String, region: String? = null): Result {
        if (!MeshNodeCredentialStore.validNodeId(targetNodeId)) {
            return Result(false, "MESH_TARGET_NODE_INVALID")
        }
        val builder = url("v1/node/transport/path").newBuilder()
            .addQueryParameter("target", targetNodeId)
        if (!region.isNullOrBlank()) {
            builder.addQueryParameter("region", region.take(MAX_REGION_CHARS))
        }
        return execute(Request.Builder().url(builder.build()).get())
    }

    fun createNegotiation(targetNodeId: String, preferredRegion: String? = null): Result {
        if (!MeshNodeCredentialStore.validNodeId(targetNodeId)) {
            return Result(false, "MESH_TARGET_NODE_INVALID")
        }
        val region = preferredRegion?.takeIf { it.isNotBlank() }?.take(MAX_REGION_CHARS)
        val body = if (region == null) {
            """{"targetNodeId":${quote(targetNodeId)}}"""
        } else {
            """{"targetNodeId":${quote(targetNodeId)},"preferredRegion":${quote(region)}}"""
        }
        return post("v1/node/negotiations", body)
    }

    fun reportDirectResult(
        sessionId: String,
        candidateEndpoint: String,
        success: Boolean,
        latencyMs: Long? = null,
        error: String? = null
    ): Result {
        if (!validSessionId(sessionId)) return Result(false, "MESH_SESSION_ID_INVALID")
        if (!validEndpoint(candidateEndpoint)) return Result(false, "MESH_CANDIDATE_INVALID")
        if (latencyMs != null && latencyMs !in 0L..60_000L) return Result(false, "MESH_LATENCY_INVALID")

        val parts = mutableListOf(
            "\"sessionId\":${quote(sessionId)}",
            "\"endpoint\":${quote(candidateEndpoint)}",
            "\"success\":$success"
        )
        if (latencyMs != null) parts += "\"latencyMs\":$latencyMs"
        if (!error.isNullOrBlank()) parts += "\"error\":${quote(error.take(MAX_ERROR_CHARS))}"

        return post("v1/node/negotiations/direct-result", "{${parts.joinToString(",")}}")
    }

    fun finalizeNegotiation(sessionId: String): Result =
        sessionPost("v1/node/negotiations/finalize", sessionId)

    fun keepAlive(sessionId: String): Result =
        sessionPost("v1/node/negotiations/keepalive", sessionId)

    fun claimRelay(negotiationId: String): Result {
        if (!validSessionId(negotiationId)) return Result(false, "MESH_NEGOTIATION_ID_INVALID")
        return post(
            "v1/node/relay/claim",
            """{"negotiationId":${quote(negotiationId)}}"""
        )
    }

    private fun sessionPost(path: String, sessionId: String): Result {
        if (!validSessionId(sessionId)) return Result(false, "MESH_SESSION_ID_INVALID")
        return post(path, """{"sessionId":${quote(sessionId)}}""")
    }

    private fun get(path: String): Result =
        execute(Request.Builder().url(url(path)).get())

    private fun post(path: String, json: String): Result {
        if (json.length > MAX_REQUEST_CHARS) return Result(false, "MESH_REQUEST_TOO_LARGE")
        return execute(
            Request.Builder()
                .url(url(path))
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
        )
    }

    private fun postUnauthenticated(path: String, json: String): Result {
        if (json.length > MAX_REQUEST_CHARS) return Result(false, "MESH_REQUEST_TOO_LARGE")
        val request = Request.Builder()
            .url(url(path))
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .build()
        return executeRequest(request)
    }

    private fun execute(builder: Request.Builder): Result {
        val credential = credentialStore.load()
            ?: return Result(false, "MESH_NODE_CREDENTIAL_MISSING")

        val request = builder
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .header("Authorization", "Bearer ${credential.token}")
            .header("X-Sentinel-Node-Id", credential.nodeId)
            .build()

        return executeRequest(request)
    }

    private fun executeRequest(request: Request): Result = try {
            client.newCall(request).execute().use { response ->
                if (response.request.url.host.lowercase() != baseUrl.host.lowercase() ||
                    response.request.url.scheme != baseUrl.scheme ||
                    response.request.url.port != baseUrl.port) {
                    return Result(false, "MESH_ORIGIN_CHANGED")
                }
                val bytes = readBounded(response.body.byteStream())
                    ?: return Result(false, "MESH_RESPONSE_TOO_LARGE", response.code)
                val text = bytes.toString(Charsets.UTF_8)
                if (response.code !in 200..299) {
                    Result(false, "MESH_HTTP_${response.code}", response.code, text)
                } else {
                    Result(true, "MESH_REQUEST_OK", response.code, text)
                }
            }
        } catch (_: Exception) {
            Result(false, "MESH_NETWORK_ERROR")
        }

    private fun url(relativePath: String): HttpUrl {
        require(relativePath.matches(Regex("[A-Za-z0-9/_-]+"))) { "MESH_PATH_INVALID" }
        return baseUrl.resolve(relativePath) ?: throw IllegalStateException("MESH_URL_RESOLUTION_FAILED")
    }

    private fun readBounded(stream: InputStream): ByteArray? = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) return null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_ALLOWED_HOSTS = 8
        private const val MAX_CANDIDATES = 16
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val MAX_REQUEST_CHARS = 32 * 1024
        private const val MAX_REGION_CHARS = 64
        private const val MAX_ERROR_CHARS = 256

        internal fun validEndpoint(value: String): Boolean {
            if (value.length !in 3..512) return false
            val match = Regex("""^([A-Za-z0-9.-]+|\[[0-9A-Fa-f:]+\]):([1-9][0-9]{0,4})$""").matchEntire(value)
                ?: return false
            return match.groupValues[2].toIntOrNull()?.let { it in 1..65535 } == true
        }

        internal fun validSessionId(value: String): Boolean =
            value.isNotEmpty() && value.length <= 512 && value.none { it.code < 0x20 || it.code == 0x7f }

        internal fun validInvitationCode(value: String): Boolean =
            value.length in 32..128 && value.matches(Regex("[A-Za-z0-9_-]+"))

        internal fun parseEnrollmentCredential(body: String): MeshNodeCredentialStore.Credential? {
            if (body.length > 4096) return null
            val nodeId = Regex("""\"nodeId\"\s*:\s*\"([A-Za-z0-9:_./-]{2,256})\"""")
                .find(body)?.groupValues?.getOrNull(1) ?: return null
            val token = Regex("""\"token\"\s*:\s*\"([A-Za-z0-9_-]{32,128})\"""")
                .find(body)?.groupValues?.getOrNull(1) ?: return null
            return MeshNodeCredentialStore.Credential(nodeId, token)
        }

        internal fun quote(value: String): String = buildString(value.length + 2) {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

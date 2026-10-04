package com.sentinel.quantum.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Canonical public Sentinel API endpoint policy for distributed Android clients.
 *
 * The configured base origin may change independently of client code. Distributed clients
 * are only allowed to call the explicitly public, credential-free routes declared here.
 */
object SentinelApiEndpointPolicy {
    private val publicPaths = setOf(
        "/v1/evaluate-call",
        "/v1/report-call-public",
        "/v1/intelligence/lookup",
        "/v1/intelligence/lookup-fingerprint",
        "/v1/intelligence/report-public"
    )

    fun originHost(baseUrl: String): String = validateOrigin(baseUrl).host.lowercase()

    fun build(
        baseUrl: String,
        path: String,
        allowedHosts: Set<String>? = null
    ): HttpUrl {
        if (path !in publicPaths) {
            throw SecurityException("SENTINEL_API_PATH_NOT_ALLOWED")
        }

        val parsed = validateOrigin(baseUrl)
        val normalizedAllowedHosts = allowedHosts
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()

        if (
            normalizedAllowedHosts != null &&
            (
                normalizedAllowedHosts.isEmpty() ||
                normalizedAllowedHosts.size > 8 ||
                parsed.host.lowercase() !in normalizedAllowedHosts
            )
        ) {
            throw SecurityException("SENTINEL_API_HOST_NOT_ALLOWED")
        }

        return parsed.newBuilder()
            .encodedPath(path)
            .query(null)
            .fragment(null)
            .build()
    }

    private fun validateOrigin(baseUrl: String): HttpUrl {
        val parsed = runCatching { baseUrl.trim().trimEnd('/').toHttpUrl() }
            .getOrElse { throw SecurityException("SENTINEL_API_ORIGIN_INVALID") }

        if (
            parsed.scheme != "https" ||
            parsed.port != 443 ||
            parsed.host.isBlank() ||
            parsed.username.isNotEmpty() ||
            parsed.password.isNotEmpty() ||
            parsed.query != null ||
            parsed.fragment != null ||
            (parsed.encodedPath.isNotEmpty() && parsed.encodedPath != "/")
        ) {
            throw SecurityException("SENTINEL_API_ORIGIN_NOT_ALLOWED")
        }

        return parsed
    }
}

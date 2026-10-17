package com.sentinel.quantum.security

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Canonical Sentinel API endpoint policy for distributed Android clients.
 * Public and authenticated routes are deliberately separated so a Bearer-only endpoint can never
 * become credential-free by being added to the public allowlist.
 */
object SentinelApiEndpointPolicy {
    private val publicPaths = setOf(
        "/v1/evaluate-call",
        "/v1/report-call-public",
        "/v1/intelligence/lookup",
        "/v1/intelligence/lookup-fingerprint",
        "/v1/intelligence/report-public"
    )

    private val authenticatedPaths = setOf(
        "/v1/intelligence/exposures/report-user",
        "/v1/intelligence/exposures/lookup-user"
    )

    fun originHost(baseUrl: String): String = validateOrigin(baseUrl).host.lowercase()

    fun build(
        baseUrl: String,
        path: String,
        allowedHosts: Set<String>? = null
    ): HttpUrl = buildAllowed(baseUrl, path, publicPaths, allowedHosts)

    fun buildAuthenticated(
        baseUrl: String,
        path: String,
        allowedHosts: Set<String>? = null
    ): HttpUrl = buildAllowed(baseUrl, path, authenticatedPaths, allowedHosts)

    private fun buildAllowed(
        baseUrl: String,
        path: String,
        allowedPaths: Set<String>,
        allowedHosts: Set<String>?
    ): HttpUrl {
        if (path !in allowedPaths) {
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

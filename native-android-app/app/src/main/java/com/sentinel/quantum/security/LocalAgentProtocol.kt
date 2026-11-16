package com.sentinel.quantum.security

import java.net.URI

/**
 * Fail-closed protocol contract for a future Sentinel Local Agent.
 *
 * This model validates configuration and bounded metadata only. It does not open
 * sockets, perform discovery, authenticate peers or ingest device identifiers.
 */
data class LocalAgentEndpointPolicy(
    val endpoint: String,
    val allowLoopbackHttpForDevelopment: Boolean = false
)

data class LocalAgentSnapshotMetadata(
    val schemaVersion: Int,
    val generatedAtMs: Long,
    val deviceCount: Int,
    val eventCount: Int,
    val payloadBytes: Long
)

object LocalAgentProtocol {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_DEVICE_COUNT = 4_096
    const val MAX_EVENT_COUNT = 10_000
    const val MAX_PAYLOAD_BYTES = 1_048_576L

    fun isEndpointAllowed(policy: LocalAgentEndpointPolicy): Boolean {
        val uri = runCatching { URI(policy.endpoint.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trim().orEmpty()
        if (host.isEmpty() || uri.userInfo != null || uri.fragment != null) return false

        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> policy.allowLoopbackHttpForDevelopment && isLoopbackHost(host)
            else -> false
        }
    }

    fun isSnapshotMetadataValid(metadata: LocalAgentSnapshotMetadata, nowMs: Long): Boolean {
        if (nowMs < 0L) return false
        if (metadata.schemaVersion != SUPPORTED_SCHEMA_VERSION) return false
        if (metadata.generatedAtMs < 0L || metadata.generatedAtMs > nowMs) return false
        if (metadata.deviceCount !in 0..MAX_DEVICE_COUNT) return false
        if (metadata.eventCount !in 0..MAX_EVENT_COUNT) return false
        if (metadata.payloadBytes !in 0..MAX_PAYLOAD_BYTES) return false
        return true
    }

    private fun isLoopbackHost(host: String): Boolean =
        host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "[::1]"
}

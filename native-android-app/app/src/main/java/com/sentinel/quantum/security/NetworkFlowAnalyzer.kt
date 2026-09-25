package com.sentinel.quantum.security

/**
 * Local metadata analyzer for already-observed network flows.
 *
 * This class does not capture packets, decrypt TLS, inspect payloads or open sockets.
 * A future Android collector (for example a user-approved VpnService path) may feed
 * bounded metadata records into this analyzer.
 */
enum class FlowProtocol {
    TCP,
    UDP,
    ICMP,
    OTHER
}

data class NetworkFlowObservation(
    val appUid: Int?,
    val remoteEndpoint: String,
    val remotePort: Int?,
    val protocol: FlowProtocol,
    val bytesSent: Long,
    val bytesReceived: Long,
    val startedAtMs: Long,
    val endedAtMs: Long
)

data class FlowDestinationSummary(
    val remoteEndpoint: String,
    val protocol: FlowProtocol,
    val remotePort: Int?,
    val flowCount: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val appUids: Set<Int>
)

data class NetworkFlowSummary(
    val acceptedObservations: Int,
    val rejectedObservations: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val destinations: List<FlowDestinationSummary>
)

object NetworkFlowAnalyzer {
    private const val MAX_OBSERVATIONS = 5_000
    private const val MAX_ENDPOINT_LENGTH = 253

    fun summarize(observations: List<NetworkFlowObservation>): NetworkFlowSummary {
        val bounded = observations.take(MAX_OBSERVATIONS)
        val accepted = bounded.filter(::isValid)
        val rejected = bounded.size - accepted.size + (observations.size - bounded.size)

        val destinations = accepted
            .groupBy { Triple(normalizeEndpoint(it.remoteEndpoint), it.protocol, it.remotePort) }
            .map { (key, flows) ->
                val (endpoint, protocol, port) = key
                FlowDestinationSummary(
                    remoteEndpoint = endpoint,
                    protocol = protocol,
                    remotePort = port,
                    flowCount = flows.size,
                    totalBytesSent = flows.sumOf { it.bytesSent },
                    totalBytesReceived = flows.sumOf { it.bytesReceived },
                    firstSeenAtMs = flows.minOf { it.startedAtMs },
                    lastSeenAtMs = flows.maxOf { it.endedAtMs },
                    appUids = flows.mapNotNull { it.appUid }.toSet()
                )
            }
            .sortedWith(
                compareByDescending<FlowDestinationSummary> {
                    saturatingAdd(it.totalBytesSent, it.totalBytesReceived)
                }.thenBy { it.remoteEndpoint }
            )

        return NetworkFlowSummary(
            acceptedObservations = accepted.size,
            rejectedObservations = rejected,
            totalBytesSent = accepted.fold(0L) { total, item -> saturatingAdd(total, item.bytesSent) },
            totalBytesReceived = accepted.fold(0L) { total, item -> saturatingAdd(total, item.bytesReceived) },
            destinations = destinations
        )
    }

    private fun isValid(item: NetworkFlowObservation): Boolean {
        val endpoint = normalizeEndpoint(item.remoteEndpoint)
        if (endpoint.isEmpty()) return false
        if (item.remotePort != null && item.remotePort !in 1..65535) return false
        if (item.appUid != null && item.appUid < 0) return false
        if (item.bytesSent < 0L || item.bytesReceived < 0L) return false
        if (item.startedAtMs < 0L || item.endedAtMs < item.startedAtMs) return false
        return true
    }

    private fun normalizeEndpoint(value: String): String =
        value.trim().lowercase().take(MAX_ENDPOINT_LENGTH)

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

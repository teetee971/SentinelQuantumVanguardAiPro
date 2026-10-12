package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFlowAnalyzerTest {

    @Test
    fun aggregatesFlowMetadataByDestinationProtocolAndPort() {
        val result = NetworkFlowAnalyzer.summarize(
            listOf(
                NetworkFlowObservation(1001, "Example.COM", 443, FlowProtocol.TCP, 100, 500, 1_000, 2_000),
                NetworkFlowObservation(1002, " example.com ", 443, FlowProtocol.TCP, 50, 250, 2_100, 3_000),
                NetworkFlowObservation(1001, "dns.example", 53, FlowProtocol.UDP, 20, 40, 1_500, 1_600)
            )
        )

        assertEquals(3, result.acceptedObservations)
        assertEquals(0, result.rejectedObservations)
        assertEquals(170L, result.totalBytesSent)
        assertEquals(790L, result.totalBytesReceived)
        assertEquals(2, result.destinations.size)

        val https = result.destinations.first { it.remoteEndpoint == "example.com" }
        assertEquals(2, https.flowCount)
        assertEquals(setOf(1001, 1002), https.appUids)
        assertEquals(150L, https.totalBytesSent)
        assertEquals(750L, https.totalBytesReceived)
    }

    @Test
    fun rejectsMalformedMetadataWithoutInspectingPayloads() {
        val result = NetworkFlowAnalyzer.summarize(
            listOf(
                NetworkFlowObservation(null, "", 443, FlowProtocol.TCP, 1, 1, 0, 1),
                NetworkFlowObservation(null, "host", 0, FlowProtocol.TCP, 1, 1, 0, 1),
                NetworkFlowObservation(null, "host", 443, FlowProtocol.TCP, -1, 1, 0, 1),
                NetworkFlowObservation(null, "host", 443, FlowProtocol.TCP, 1, 1, 10, 5)
            )
        )

        assertEquals(0, result.acceptedObservations)
        assertEquals(4, result.rejectedObservations)
        assertTrue(result.destinations.isEmpty())
    }

    @Test
    fun distinctProtocolsRemainSeparate() {
        val result = NetworkFlowAnalyzer.summarize(
            listOf(
                NetworkFlowObservation(null, "1.1.1.1", 53, FlowProtocol.UDP, 10, 10, 0, 1),
                NetworkFlowObservation(null, "1.1.1.1", 53, FlowProtocol.TCP, 10, 10, 0, 1)
            )
        )

        assertEquals(2, result.destinations.size)
    }

    @Test
    fun saturatesPerDestinationCountersInsteadOfOverflowing() {
        val result = NetworkFlowAnalyzer.summarize(
            listOf(
                NetworkFlowObservation(1001, "example.com", 443, FlowProtocol.TCP, Long.MAX_VALUE - 3, Long.MAX_VALUE - 5, 0, 1),
                NetworkFlowObservation(1002, " EXAMPLE.COM ", 443, FlowProtocol.TCP, 10, 10, 2, 3)
            )
        )

        assertEquals(2, result.acceptedObservations)
        assertEquals(0, result.rejectedObservations)
        assertEquals(Long.MAX_VALUE, result.totalBytesSent)
        assertEquals(Long.MAX_VALUE, result.totalBytesReceived)
        assertEquals(1, result.destinations.size)
        assertEquals(Long.MAX_VALUE, result.destinations.single().totalBytesSent)
        assertEquals(Long.MAX_VALUE, result.destinations.single().totalBytesReceived)
    }

    @Test
    fun rejectsOverlongNormalizedEndpointsWithoutSilentTruncation() {
        val validEndpoint = "a".repeat(253)
        val result = NetworkFlowAnalyzer.summarize(
            listOf(
                NetworkFlowObservation(null, validEndpoint, 443, FlowProtocol.TCP, 1, 2, 0, 1),
                NetworkFlowObservation(null, validEndpoint + "x", 443, FlowProtocol.TCP, 3, 4, 0, 1),
                NetworkFlowObservation(null, " " + validEndpoint + "y ", 443, FlowProtocol.TCP, 5, 6, 0, 1)
            )
        )

        assertEquals(1, result.acceptedObservations)
        assertEquals(2, result.rejectedObservations)
        assertEquals(1, result.destinations.size)
        assertEquals(validEndpoint, result.destinations.single().remoteEndpoint)
        assertEquals(1L, result.destinations.single().totalBytesSent)
    }
}

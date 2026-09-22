package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDeviceEventRegistryTest {

    @Test
    fun reducesMultipleSourcesIntoOneLocalDeviceEntry() {
        val result = NetworkDeviceEventRegistry.reduce(
            listOf(
                NetworkDeviceEvent(
                    deviceFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    type = NetworkDeviceEventType.FIRST_SEEN,
                    source = NetworkDeviceObservationSource.ANDROID_WIFI,
                    observedAtMs = 1_000L,
                    confidencePercent = 40
                ),
                NetworkDeviceEvent(
                    deviceFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    type = NetworkDeviceEventType.IDENTITY_CHANGED,
                    source = NetworkDeviceObservationSource.LOCAL_AGENT,
                    observedAtMs = 90_000L,
                    confidencePercent = 75
                )
            )
        )

        assertEquals(2, result.acceptedEvents)
        assertEquals(0, result.rejectedEvents)
        assertEquals(1, result.entries.size)

        val entry = result.entries.single()
        assertEquals(1_000L, entry.firstSeenAtMs)
        assertEquals(90_000L, entry.lastSeenAtMs)
        assertEquals(NetworkDeviceEventType.IDENTITY_CHANGED, entry.lastEventType)
        assertEquals(75, entry.maxConfidencePercent)
        assertEquals(
            setOf(
                NetworkDeviceObservationSource.ANDROID_WIFI,
                NetworkDeviceObservationSource.LOCAL_AGENT
            ),
            entry.sources
        )
    }

    @Test
    fun suppressesSameSignalInsideDuplicateWindow() {
        val result = NetworkDeviceEventRegistry.reduce(
            listOf(
                NetworkDeviceEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkDeviceEventType.FIRST_SEEN,
                    NetworkDeviceObservationSource.ANDROID_BLE,
                    1_000L,
                    50
                ),
                NetworkDeviceEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkDeviceEventType.FIRST_SEEN,
                    NetworkDeviceObservationSource.ANDROID_BLE,
                    30_000L,
                    50
                )
            )
        )

        assertEquals(1, result.acceptedEvents)
        assertEquals(1, result.rejectedEvents)
        assertEquals(1, result.entries.single().eventCount)
    }

    @Test
    fun sameDeviceDifferentEventTypeIsNotSuppressed() {
        val result = NetworkDeviceEventRegistry.reduce(
            listOf(
                NetworkDeviceEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkDeviceEventType.FIRST_SEEN,
                    NetworkDeviceObservationSource.ANDROID_WIFI,
                    1_000L,
                    60
                ),
                NetworkDeviceEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkDeviceEventType.RISK_CHANGED,
                    NetworkDeviceObservationSource.ANDROID_WIFI,
                    2_000L,
                    80
                )
            )
        )

        assertEquals(2, result.acceptedEvents)
        assertEquals(NetworkDeviceEventType.RISK_CHANGED, result.entries.single().lastEventType)
    }

    @Test
    fun invalidRawLikeIdentifierIsRejected() {
        val result = NetworkDeviceEventRegistry.reduce(
            listOf(
                NetworkDeviceEvent(
                    "AA:BB:CC:DD:EE:FF",
                    NetworkDeviceEventType.FIRST_SEEN,
                    NetworkDeviceObservationSource.ANDROID_WIFI,
                    1_000L,
                    60
                )
            )
        )

        assertEquals(0, result.acceptedEvents)
        assertEquals(1, result.rejectedEvents)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun separateFingerprintsRemainSeparateEntries() {
        val result = NetworkDeviceEventRegistry.reduce(
            listOf(
                NetworkDeviceEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkDeviceEventType.FIRST_SEEN,
                    NetworkDeviceObservationSource.ANDROID_WIFI,
                    1_000L,
                    60
                ),
                NetworkDeviceEvent(
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkDeviceEventType.FIRST_SEEN,
                    NetworkDeviceObservationSource.ANDROID_WIFI,
                    2_000L,
                    60
                )
            )
        )

        assertEquals(2, result.entries.size)
    }
}

package com.sentinel.quantum.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSurveillanceTimelineSourceTest {
    private val source: String by lazy {
        File("src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt").readText()
    }

    @Test
    fun screenReadsAndDisplaysTheBoundedNetworkTimeline() {
        assertTrue(source.contains("NetworkTimelineStore"))
        assertTrue(source.contains("timelineSnapshot"))
        assertTrue(source.contains("network-timeline"))
        assertTrue(source.contains("acceptedEvents"))
    }

    @Test
    fun scannersRefreshTheTimelineAfterACompletedObservation() {
        assertTrue(source.contains("onTimelineUpdated"))
        assertTrue(source.contains("timelineSnapshot ="))
    }
}

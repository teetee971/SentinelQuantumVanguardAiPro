package com.sentinel.quantum.telecom

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class TelecomCallScreeningBoundaryTest {
    @Test
    fun screeningCallbackDoesNotConstructOrInvokeTelecomNetworkEnrichment() {
        val source = File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt"
        ).readText()

        assertFalse(source.contains("OkHttpOfficialSourceTransport"))
        assertFalse(source.contains("OfficialSourceProbeAdapter"))
        assertFalse(source.contains("TelecomSourceSyncCoordinator"))
        assertFalse(source.contains("publishTelecomEnrichment"))
    }
}

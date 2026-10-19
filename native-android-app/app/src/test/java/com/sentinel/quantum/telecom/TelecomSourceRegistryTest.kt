package com.sentinel.quantum.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelecomSourceRegistryTest {
    @Test
    fun referenceRegistryHasOfficialHttpsSourcesAndNoEnforcementAuthority() {
        val sources = GlobalTelecomSourceRegistry.sources

        assertEquals(10, sources.size)
        assertTrue(sources.all { it.officialUrl.startsWith("https://") })
        assertTrue(sources.all { it.enforcementEligibility == EnforcementEligibility.NONE })
        assertTrue(sources.all { it.connectorStatus == ConnectorStatus.REFERENCE_ONLY })
        assertTrue(sources.any { it.id == "ITU" })
        assertTrue(sources.any { it.id == "ARCEP" })
        assertFalse(sources.any { it.connectorStatus == ConnectorStatus.SYNCED })
    }

    @Test
    fun invalidSourceMetadataIsRejectedBeforeSync() {
        val result = TelecomSourceValidator.validate(
            TelecomSourceDescriptor(
                id = "TEST",
                jurisdiction = "XX",
                region = TelecomRegion.EUROPE,
                publisher = "Test",
                officialUrl = "https://official.example.test",
                category = TelecomSourceCategory.NUMBERING_REGULATOR
            ).copy(officialUrl = "http://not-tls.example.test")
        )

        assertFalse(result.isValid)
        assertEquals("OFFICIAL_URL_NOT_HTTPS", result.reasonCode)
    }
}

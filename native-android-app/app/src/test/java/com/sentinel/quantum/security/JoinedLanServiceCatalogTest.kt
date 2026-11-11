package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinedLanServiceCatalogTest {
    @Test
    fun announcementUsesTheExistingExposureClassifier() {
        val found = JoinedLanServiceCatalog.announcement(
            name = "printer",
            dnsType = "_smb._tcp.local.",
            host = "192.168.1.20",
            port = 445
        )
        requireNotNull(found)
        assertEquals(64, found.service.subjectFingerprint.length)
        val assessment = NetworkServiceExposureAnalyzer.assess(listOf(found.service))
        assertEquals(1, assessment.acceptedServices)
        assertTrue(assessment.findings.any { it.kind == NetworkServiceExposureKind.FILE_SHARING_SURFACE })
    }

    @Test
    fun unknownServiceTypeIsIgnored() {
        assertNull(
            JoinedLanServiceCatalog.announcement("x", "_unknown._tcp.", "192.168.1.2", 9)
        )
    }

    @Test
    fun defensiveDiscoveryIsAllowedAndTheLabProbeIsNot() {
        assertTrue(NetworkOperationPolicy.permitsDefensiveDiscovery(NetworkOperationMode.DEFENSIVE_LOCAL))
        assertNotNull(JoinedLanServiceCatalog.types)
        val denied = NetworkAuthorizationContext(
            ownsOrAdministersTarget = false,
            explicitTestSession = false,
            localOrIsolatedScope = true,
            entitlement = NetworkFeatureEntitlement.STANDARD
        )
        assertTrue(
            AuthorizedLanProbePolicy.validate(
                NetworkOperationMode.DEFENSIVE_LOCAL,
                denied,
                listOf(AuthorizedProbeTarget("192.168.1.2", listOf(80)))
            ) == null
        )
    }
}

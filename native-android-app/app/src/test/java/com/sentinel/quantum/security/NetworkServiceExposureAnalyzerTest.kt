package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkServiceExposureAnalyzerTest {

    private fun service(
        port: Int,
        transport: NetworkServiceTransport = NetworkServiceTransport.TCP,
        scope: NetworkServiceScope = NetworkServiceScope.LOCAL_LAN,
        hint: String? = null
    ) = ObservedNetworkService(
        subjectFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        port = port,
        transport = transport,
        scope = scope,
        serviceHint = hint
    )

    @Test
    fun telnetIsHighAndAlsoRemoteAdminSurface() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(service(port = 23, hint = "Telnet"))
        )

        assertEquals(1, result.acceptedServices)
        assertEquals(0, result.rejectedServices)
        assertEquals(1, result.highCount)
        assertTrue(
            result.findings.any {
                it.kind == NetworkServiceExposureKind.PLAINTEXT_PROTOCOL &&
                    it.severity == NetworkServiceExposureSeverity.HIGH
            }
        )
        assertTrue(
            result.findings.any {
                it.kind == NetworkServiceExposureKind.REMOTE_ADMIN_SURFACE
            }
        )
    }

    @Test
    fun sshIsReviewOnlyNotPlaintextFinding() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(service(port = 22, hint = "SSH"))
        )

        assertEquals(0, result.highCount)
        assertEquals(1, result.reviewCount)
        assertEquals(
            setOf(NetworkServiceExposureKind.REMOTE_ADMIN_SURFACE),
            result.findings.map { it.kind }.toSet()
        )
    }

    @Test
    fun localDatabaseIsFlaggedForReviewWithoutClaimingVulnerability() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(service(port = 5432, hint = "PostgreSQL"))
        )

        val finding = result.findings.single()
        assertEquals(NetworkServiceExposureKind.DATABASE_SURFACE, finding.kind)
        assertEquals(NetworkServiceExposureSeverity.REVIEW, finding.severity)
        assertTrue(finding.explanation.contains("Vérifier"))
    }

    @Test
    fun discoveryServicesRemainInformational() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(
                service(
                    port = 5353,
                    transport = NetworkServiceTransport.UDP,
                    hint = "mDNS"
                )
            )
        )

        val finding = result.findings.single()
        assertEquals(NetworkServiceExposureKind.DISCOVERY_SURFACE, finding.kind)
        assertEquals(NetworkServiceExposureSeverity.INFO, finding.severity)
        assertEquals(0, result.reviewCount)
        assertEquals(0, result.highCount)
    }

    @Test
    fun loopbackServicesDoNotCreateNetworkExposureFindings() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(
                service(
                    port = 23,
                    scope = NetworkServiceScope.LOOPBACK,
                    hint = "telnet"
                )
            )
        )

        assertEquals(1, result.acceptedServices)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun ftpAndHttpAreReviewSignalsNotAutomaticCompromiseClaims() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(
                service(port = 21, hint = "ftp"),
                service(port = 80, hint = "http")
            )
        )

        assertEquals(2, result.acceptedServices)
        assertEquals(2, result.reviewCount)
        assertEquals(0, result.highCount)
        assertTrue(
            result.findings.all {
                it.kind == NetworkServiceExposureKind.PLAINTEXT_PROTOCOL
            }
        )
    }

    @Test
    fun fileSharingSurfaceIsRecognized() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(service(port = 445, hint = "SMB"))
        )

        val finding = result.findings.single()
        assertEquals(NetworkServiceExposureKind.FILE_SHARING_SURFACE, finding.kind)
        assertEquals(NetworkServiceExposureSeverity.REVIEW, finding.severity)
    }

    @Test
    fun malformedFingerprintAndPortAreRejected() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(
                ObservedNetworkService(
                    subjectFingerprint = "AA:BB:CC:DD:EE:FF",
                    port = 443,
                    transport = NetworkServiceTransport.TCP,
                    scope = NetworkServiceScope.LOCAL_LAN
                ),
                service(port = 0)
            )
        )

        assertEquals(0, result.acceptedServices)
        assertEquals(2, result.rejectedServices)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun serviceHintIsNormalizedAndBounded() {
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(
                service(
                    port = 22,
                    hint = "  SSH   " + "x".repeat(100)
                )
            )
        )

        val finding = result.findings.single()
        assertEquals(64, finding.serviceHint!!.length)
        assertTrue(finding.serviceHint!!.startsWith("ssh"))
    }

    @Test
    fun sameFindingIsDeduplicated() {
        val duplicate = service(port = 445, hint = "smb")
        val result = NetworkServiceExposureAnalyzer.assess(
            listOf(duplicate, duplicate)
        )

        assertEquals(2, result.acceptedServices)
        assertEquals(1, result.findings.size)
    }
}

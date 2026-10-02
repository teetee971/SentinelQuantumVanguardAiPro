package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelPackageRiskHeuristicsTest {

    @Test
    fun systemPackagesAreNeverFlaggedByDeclarationHeuristics() {
        val findings = SentinelPackageRiskHeuristics.evaluate(
            SentinelPackageRiskHeuristics.Signals(
                packageName = "android.system.test",
                isSystemApp = true,
                declaresAccessibilityService = true,
                requestsInstallPackages = true,
                requestsOverlay = true,
                requestsBootCompleted = true,
                requestsReadSms = true,
                requestsReceiveSms = true
            ),
            observedAtEpochMillis = 1L
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun accessibilityInstallerOverlayCombinationIsSuspiciousNotMalicious() {
        val findings = SentinelPackageRiskHeuristics.evaluate(
            SentinelPackageRiskHeuristics.Signals(
                packageName = "example.app",
                isSystemApp = false,
                declaresAccessibilityService = true,
                requestsInstallPackages = true,
                requestsOverlay = true,
                requestsBootCompleted = false,
                requestsReadSms = false,
                requestsReceiveSms = false
            ),
            observedAtEpochMillis = 2L
        )

        assertEquals(1, findings.size)
        assertEquals(SentinelMalwareDiagnostic.Verdict.SUSPICIOUS, findings.single().verdict)
    }

    @Test
    fun ordinaryPackageDeclarationsDoNotCreateNoise() {
        val findings = SentinelPackageRiskHeuristics.evaluate(
            SentinelPackageRiskHeuristics.Signals(
                packageName = "example.normal",
                isSystemApp = false,
                declaresAccessibilityService = false,
                requestsInstallPackages = false,
                requestsOverlay = false,
                requestsBootCompleted = true,
                requestsReadSms = false,
                requestsReceiveSms = false
            ),
            observedAtEpochMillis = 3L
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun accessibilityOverlaySmsCombinationIsSuspicious() {
        val findings = SentinelPackageRiskHeuristics.evaluate(
            SentinelPackageRiskHeuristics.Signals(
                packageName = "example.smsrisk",
                isSystemApp = false,
                declaresAccessibilityService = true,
                requestsInstallPackages = false,
                requestsOverlay = true,
                requestsBootCompleted = false,
                requestsReadSms = true,
                requestsReceiveSms = false
            ),
            observedAtEpochMillis = 4L
        )

        assertEquals(
            SentinelMalwareDiagnostic.Verdict.SUSPICIOUS,
            findings.single().verdict
        )
    }
}

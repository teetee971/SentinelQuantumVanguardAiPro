package com.sentinel.quantum.ai

import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelAiRestartIdentityTest {

    companion object {
        @kotlin.jvm.JvmStatic
        @get:org.junit.ClassRule
        val refuseInstalledPackage = com.sentinel.quantum.security.RefuseInstalledPackageRule()
    }

    private val prepared = SentinelAiRestartIdentity(pid = 4242, startTicks = 9300, bootCount = 13)

    @Test fun sameProcessCannotProveRestartEvenAfterAnotherInstrumentationInvocation() {
        assertFalse(prepared.provesRestartFrom(prepared.copy(), SentinelAiRestartKind.PROCESS))
    }

    @Test fun reusedPidProvesRestartOnlyWithDifferentKernelStartTimeOnSameBoot() {
        assertTrue(prepared.copy(startTicks = 9600).provesRestartFrom(prepared, SentinelAiRestartKind.PROCESS))
        assertTrue(prepared.copy(pid = 4400).provesRestartFrom(prepared, SentinelAiRestartKind.PROCESS))
        assertTrue(prepared.copy(pid = 4400, startTicks = 9300)
            .provesRestartFrom(prepared, SentinelAiRestartKind.PROCESS))
    }

    @Test fun processAndDeviceProofAreSeparate() {
        val nextProcess = prepared.copy(pid = 4400, startTicks = 9600)
        val nextBoot = prepared.copy(bootCount = 14)
        assertFalse(nextProcess.provesRestartFrom(prepared, SentinelAiRestartKind.REBOOT))
        assertFalse(nextBoot.provesRestartFrom(prepared, SentinelAiRestartKind.PROCESS))
        assertTrue(nextBoot.provesRestartFrom(prepared, SentinelAiRestartKind.REBOOT))
        assertFalse(prepared.copy(bootCount = 12, pid = 4400)
            .provesRestartFrom(prepared, SentinelAiRestartKind.PROCESS))
        assertFalse(prepared.copy(bootCount = 12).provesRestartFrom(prepared, SentinelAiRestartKind.REBOOT))
    }

    @Test fun procStatExtractsField22WithSpacesAndParenthesesInProcessName() {
        val stat = "4242 (a tricky ) process) S " + (4..21).joinToString(" ") + " 9300 23 24"
        assertEquals(prepared, SentinelAiRestartIdentity.fromProcStat(stat, 4242, 13))
        assertThrows(IllegalArgumentException::class.java) {
            SentinelAiRestartIdentity.fromProcStat(stat, 4400, 13)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SentinelAiRestartIdentity.fromProcStat(stat.substringBefore(" 9300"), 4242, 13)
        }
    }

    @Test fun malformedOrMissingIdentityCannotBeUsedAsProof() {
        assertEquals(prepared, SentinelAiRestartIdentity.decode(prepared.encode()))
        for (value in listOf("4242|13", "4242|9300|-1", "4242|0|13", "0|9300|13", "4242|9300|013")) {
            assertThrows(IllegalArgumentException::class.java) { SentinelAiRestartIdentity.decode(value) }
        }
    }

    @Test fun metadataAuthenticationRejectsAlteredIdentityWrongKeyAndWrongFixture() {
        val key = SecretKeySpec(ByteArray(32) { 17 }, "HmacSHA256")
        val otherKey = SecretKeySpec(ByteArray(32) { 18 }, "HmacSHA256")
        val fixture = "sentinel_ai_c11_restart_process_fixture"
        val signed = prepared.authenticate(key, fixture)

        assertEquals(prepared, SentinelAiRestartIdentity.verifyAuthenticated(signed, key, fixture))
        assertThrows(IllegalStateException::class.java) {
            SentinelAiRestartIdentity.verifyAuthenticated(signed.replace("9300", "9301"), key, fixture)
        }
        assertThrows(IllegalStateException::class.java) {
            SentinelAiRestartIdentity.verifyAuthenticated(signed, otherKey, fixture)
        }
        assertThrows(IllegalStateException::class.java) {
            SentinelAiRestartIdentity.verifyAuthenticated(signed, key, fixture.replace("process", "reboot"))
        }
    }
}

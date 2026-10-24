package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates a role becoming unavailable while a multi-row MMS projection is in flight.
 *
 * This is a JVM contract test of the compensation engine, not a real RoleManager or
 * TelephonyProvider test. Device instrumentation is still required for OS revocation.
 */
class MmsRoleLossTransactionTest {
    private class RevokedOperations(
        private val loseRoleAt: String,
        private val cleanupPermitted: Boolean
    ) : MmsProviderProjectionTransaction.Operations {
        val calls = mutableListOf<String>()
        private var roleHeld = true

        private fun guarded(operation: String): Boolean {
            calls += operation
            if (operation == loseRoleAt) roleHeld = false
            return roleHeld
        }

        override fun beginJournal(): Boolean = guarded("journal")
        override fun insertRoot(): Long? = if (guarded("root")) 42L else null
        override fun recordRoot(providerMessageId: Long): Boolean = guarded("recordRoot")
        override fun insertAddress(providerMessageId: Long): Boolean = guarded("address")
        override fun insertParts(providerMessageId: Long): Boolean = guarded("parts")
        override fun markReady(providerMessageId: Long): Boolean = guarded("ready")
        override fun deleteRoot(providerMessageId: Long): Boolean {
            calls += "delete"
            return cleanupPermitted
        }
        override fun clearJournal(): Boolean {
            calls += "clear"
            return true
        }
    }

    @Test
    fun roleLossAtEachPostRootBoundaryTriggersCompensation() {
        for (phase in listOf("recordRoot", "address", "parts", "ready")) {
            val ops = RevokedOperations(phase, cleanupPermitted = true)
            val result = MmsProviderProjectionTransaction.execute(ops)
            assertTrue("phase=$phase", result is MmsProviderProjectionTransaction.Result.Rejected)
            result as MmsProviderProjectionTransaction.Result.Rejected
            assertTrue("phase=$phase", result.cleanupConfirmed)
            assertTrue("phase=$phase", ops.calls.indexOf("delete") > ops.calls.indexOf(phase))
            assertTrue("phase=$phase", ops.calls.indexOf("clear") > ops.calls.indexOf("delete"))
        }
    }

    @Test
    fun failedCleanupAfterRoleLossKeepsRecoveryJournal() {
        for (phase in listOf("recordRoot", "address", "parts", "ready")) {
            val ops = RevokedOperations(phase, cleanupPermitted = false)
            val result = MmsProviderProjectionTransaction.execute(ops)
                as MmsProviderProjectionTransaction.Result.Rejected
            assertFalse("phase=$phase", result.cleanupConfirmed)
            assertTrue("phase=$phase", "delete" in ops.calls)
            assertFalse("phase=$phase journal must survive", "clear" in ops.calls)
        }
    }

    @Test
    fun roleLossBeforeFirstMutationCannotProduceReadyResult() {
        val ops = RevokedOperations("root", cleanupPermitted = false)
        val result = MmsProviderProjectionTransaction.execute(ops)
            as MmsProviderProjectionTransaction.Result.Rejected
        assertEquals("MMS_PROVIDER_ROOT_INSERT_FAILED", result.reason)
        assertFalse("ready" in ops.calls)
    }
}

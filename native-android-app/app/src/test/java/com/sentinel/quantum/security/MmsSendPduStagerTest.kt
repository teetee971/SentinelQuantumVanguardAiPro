package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendPduStagerTest {
    @Test fun stagedPduLimitStaysAboveEligibilityPayloadCeiling() {
        assertTrue(
            MmsSendPduStager.MAX_STAGED_PDU_BYTES >
                MmsSendEligibilityPolicy.MAX_TOTAL_ATTACHMENT_BYTES
        )
    }

    @Test fun stagedPduLimitRemainsTightlyBounded() {
        assertEquals(11L * 1024L * 1024L, MmsSendPduStager.MAX_STAGED_PDU_BYTES)
        assertFalse(MmsSendPduStager.MAX_STAGED_PDU_BYTES >= 17L * 1024L * 1024L)
    }

    @Test fun stagedPduRetentionIsBoundedToOneHour() {
        assertEquals(60L * 60L * 1000L, MmsSendPduStager.STAGED_PDU_TTL_MS)
        assertTrue(MmsSendPduStager.STAGED_PDU_TTL_MS > 0L)
        assertFalse(MmsSendCleanupWorker.WORK_TAG.isBlank())
    }

    @Test fun cleanupWorkIsTokenScopedSoNewerSendsCannotPostponeOlderDeadlines() {
        val first = "11111111-1111-1111-1111-111111111111"
        val second = "22222222-2222-2222-2222-222222222222"
        val firstName = MmsSendCleanupWorker.workNameForToken(first)
        val secondName = MmsSendCleanupWorker.workNameForToken(second)
        assertTrue(firstName?.contains(first) == true)
        assertTrue(secondName?.contains(second) == true)
        assertNotEquals(firstName, secondName)
        assertNull(MmsSendCleanupWorker.workNameForToken("not-a-token"))
    }
}

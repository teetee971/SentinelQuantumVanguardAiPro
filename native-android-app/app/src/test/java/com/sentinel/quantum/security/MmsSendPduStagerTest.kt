package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}

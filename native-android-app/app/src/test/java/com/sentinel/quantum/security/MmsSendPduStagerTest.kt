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

    @Test fun stagedPduRetentionIsBoundedToOneHourPerFile() {
        assertEquals(60L * 60L * 1000L, MmsSendPduStager.STAGED_PDU_TTL_MS)
        assertTrue(MmsSendPduStager.STAGED_PDU_TTL_MS > 0L)
        assertFalse(MmsSendCleanupWorker.WORK_TAG.isBlank())
        assertTrue(
            MmsSendPduStager.isValidStagedFileName(
                "123e4567-e89b-12d3-a456-426614174000.pdu"
            )
        )
        assertFalse(MmsSendPduStager.isValidStagedFileName("../escape.pdu"))
        assertFalse(MmsSendPduStager.isValidStagedFileName("shared.pdu"))
    }
}

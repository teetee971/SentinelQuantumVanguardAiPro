package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsPartSafetyPolicyTest {
    @Test fun allowsBoundedSafeImageMetadata() {
        val result = MmsPartSafetyPolicy.evaluate(MmsPartSafetyPolicy.PartMetadata("image/jpeg", "photo.jpg", 1024))
        assertEquals(MmsPartSafetyPolicy.Decision.ALLOW_PREVIEW, result.decision)
    }

    @Test fun quarantinesExecutableAndMimeMismatch() {
        val executable = MmsPartSafetyPolicy.evaluate(MmsPartSafetyPolicy.PartMetadata("application/octet-stream", "payload.exe", 20))
        val mismatch = MmsPartSafetyPolicy.evaluate(MmsPartSafetyPolicy.PartMetadata("image/jpeg", "photo.exe", 20))
        assertEquals(MmsPartSafetyPolicy.Decision.QUARANTINE, executable.decision)
        assertEquals(MmsPartSafetyPolicy.Decision.QUARANTINE, mismatch.decision)
    }

    @Test fun quarantinesTraversalAndOversizedParts() {
        val traversal = MmsPartSafetyPolicy.evaluate(MmsPartSafetyPolicy.PartMetadata("image/png", "../x.png", 20))
        val huge = MmsPartSafetyPolicy.evaluate(MmsPartSafetyPolicy.PartMetadata("image/png", "x.png", 8L * 1024L * 1024L + 1))
        assertEquals(MmsPartSafetyPolicy.Decision.QUARANTINE, traversal.decision)
        assertEquals(MmsPartSafetyPolicy.Decision.QUARANTINE, huge.decision)
    }
}

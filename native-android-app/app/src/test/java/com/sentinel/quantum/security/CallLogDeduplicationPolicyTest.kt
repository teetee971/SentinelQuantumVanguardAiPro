package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogDeduplicationPolicyTest {
    @Test
    fun collapsesSameCallExposedTwiceByProvider() {
        assertTrue(
            CallLogDeduplicationPolicy.sameVisibleCall(
                "+590690349394", 2, 1_000_000L, 0L,
                "+590690349394", 2, 1_001_500L, 0L
            )
        )
    }

    @Test
    fun keepsDifferentCallTypes() {
        assertFalse(
            CallLogDeduplicationPolicy.sameVisibleCall(
                "0652215872", 2, 1_000_000L, 0L,
                "0652215872", 3, 1_001_000L, 0L
            )
        )
    }

    @Test
    fun keepsCallsSeparatedInTime() {
        assertFalse(
            CallLogDeduplicationPolicy.sameVisibleCall(
                "0652215872", 2, 1_000_000L, 0L,
                "0652215872", 2, 1_010_000L, 0L
            )
        )
    }

    @Test
    fun keepsDifferentDurations() {
        assertFalse(
            CallLogDeduplicationPolicy.sameVisibleCall(
                "0652215872", 2, 1_000_000L, 0L,
                "0652215872", 2, 1_001_000L, 25L
            )
        )
    }
}

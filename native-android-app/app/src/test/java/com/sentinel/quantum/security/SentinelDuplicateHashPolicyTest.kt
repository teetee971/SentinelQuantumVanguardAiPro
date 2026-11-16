package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelDuplicateHashPolicyTest {
    @Test
    fun boundedFilesCanBeAutomaticallyHashed() {
        assertEquals(
            SentinelDuplicateHashPolicy.Decision.AUTO_HASH,
            SentinelDuplicateHashPolicy.decide(SentinelDuplicateHashPolicy.AUTO_HASH_MAX_BYTES)
        )
    }

    @Test
    fun largerFilesRequireExplicitDeepScan() {
        assertEquals(
            SentinelDuplicateHashPolicy.Decision.REQUIRE_EXPLICIT_DEEP_SCAN,
            SentinelDuplicateHashPolicy.decide(SentinelDuplicateHashPolicy.AUTO_HASH_MAX_BYTES + 1L)
        )
    }

    @Test
    fun unknownSizeNeverSilentlyEntersAutoHash() {
        assertEquals(
            SentinelDuplicateHashPolicy.Decision.UNKNOWN_SIZE,
            SentinelDuplicateHashPolicy.decide(null)
        )
    }
}

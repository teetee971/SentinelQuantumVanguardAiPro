package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SentinelCleanupPathPolicyTest {
    @Test
    fun childFileIsInsideRoot() {
        assertTrue(
            SentinelCleanupPathPolicy.isStrictChild(
                File("/data/user/0/app/cache/a.tmp"),
                File("/data/user/0/app/cache")
            )
        )
    }

    @Test
    fun siblingPrefixDoesNotPassBoundary() {
        assertFalse(
            SentinelCleanupPathPolicy.isStrictChild(
                File("/data/user/0/app/cache-evil/a.tmp"),
                File("/data/user/0/app/cache")
            )
        )
    }

    @Test
    fun traversalNormalizesOutsideRoot() {
        assertFalse(
            SentinelCleanupPathPolicy.isStrictChild(
                File("/data/user/0/app/cache/../files/secret"),
                File("/data/user/0/app/cache")
            )
        )
    }

    @Test
    fun cacheRootItselfCannotBeDeletedAsCandidate() {
        assertFalse(
            SentinelCleanupPathPolicy.isStrictChild(
                File("/data/user/0/app/cache"),
                File("/data/user/0/app/cache")
            )
        )
    }
}

package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class SentinelCleanupPathPolicyTest {
    private fun inside(target: String, root: String): Boolean {
        val targetPath = Paths.get(target).normalize()
        val rootPath = Paths.get(root).normalize()
        return targetPath.startsWith(rootPath) && targetPath != rootPath
    }

    @Test
    fun childFileIsInsideRoot() {
        assertTrue(inside("/data/user/0/app/cache/a.tmp", "/data/user/0/app/cache"))
    }

    @Test
    fun siblingPrefixDoesNotPassBoundary() {
        assertFalse(inside("/data/user/0/app/cache-evil/a.tmp", "/data/user/0/app/cache"))
    }

    @Test
    fun traversalNormalizesOutsideRoot() {
        assertFalse(inside("/data/user/0/app/cache/../files/secret", "/data/user/0/app/cache"))
    }

    @Test
    fun cacheRootItselfCannotBeDeletedAsCandidate() {
        assertFalse(inside("/data/user/0/app/cache", "/data/user/0/app/cache"))
    }
}

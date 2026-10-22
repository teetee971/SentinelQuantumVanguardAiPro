package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ApkInspectionProcessGuardTest {
    @Test fun concurrentAccessIsRejectedAndLockReleased() {
        val dir = Files.createTempDirectory("sentinel-lock-test").toFile()
        try {
            ApkInspectionProcessGuard.exclusive(dir) {
                assertFalse(ApkInspectionProcessGuard.available(dir))
                assertThrows(IllegalStateException::class.java) {
                    ApkInspectionProcessGuard.exclusive(dir) { Unit }
                }
            }
            assertTrue(ApkInspectionProcessGuard.available(dir))
        } finally { dir.deleteRecursively() }
    }

    @Test fun exceptionDoesNotStrandLock() {
        val dir = Files.createTempDirectory("sentinel-lock-release").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ApkInspectionProcessGuard.exclusive(dir) { throw IllegalArgumentException("test") }
            }
            assertTrue(ApkInspectionProcessGuard.available(dir))
        } finally { dir.deleteRecursively() }
    }
}

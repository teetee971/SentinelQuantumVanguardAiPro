package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ApkInspectionProcessGuardTest {

    @Test
    fun lockIsReleasedAfterSuccess() {
        val dir = Files.createTempDirectory("apk-lock-test").toFile()
        try {
            ApkInspectionProcessGuard.exclusive(dir) {
                assertFalse(ApkInspectionProcessGuard.available(dir))
            }
            assertTrue(ApkInspectionProcessGuard.available(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun lockIsReleasedAfterException() {
        val dir = Files.createTempDirectory("apk-lock-error").toFile()
        try {
            assertThrows(IllegalStateException::class.java) {
                ApkInspectionProcessGuard.exclusive(dir) {
                    throw IllegalStateException("Test failure")
                }
            }
            assertTrue(ApkInspectionProcessGuard.available(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun simultaneousAcquisitionIsRejected() {
        val dir = Files.createTempDirectory("apk-lock-busy").toFile()
        try {
            ApkInspectionProcessGuard.exclusive(dir) {
                assertThrows(IllegalStateException::class.java) {
                    ApkInspectionProcessGuard.exclusive(dir) {
                        error("Second acquisition should not run")
                    }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

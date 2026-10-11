package com.sentinel.quantum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelUninstallFlowTest {
    @Test
    fun opens_uninstall_only_after_the_requested_backup_was_exported() {
        assertTrue(SentinelUninstallFlow.shouldOpenUninstallAfterBackup(true, true))
        assertFalse(SentinelUninstallFlow.shouldOpenUninstallAfterBackup(true, false))
        assertFalse(SentinelUninstallFlow.shouldOpenUninstallAfterBackup(false, true))
    }
}

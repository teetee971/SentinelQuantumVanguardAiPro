package com.sentinel.quantum.data

import java.io.File
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

    @Test
    fun failed_uninstall_backup_cannot_arm_a_later_manual_export() {
        val settingsSource = File(
            "src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt"
        ).readText()

        val backupCallback = settingsSource
            .substringAfter("val createBackupLauncher = rememberLauncherForActivityResult(")
            .substringBefore("val restoreBackupLauncher = rememberLauncherForActivityResult(")
        val exportSucceeded = backupCallback.indexOf("val exportSucceeded =")
        val consumePendingUninstall = backupCallback.indexOf("uninstallAfterBackup = false", exportSucceeded)
        val uninstallDecision = backupCallback.indexOf(
            "SentinelUninstallFlow.shouldOpenUninstallAfterBackup(",
            exportSucceeded
        )

        assertTrue("backup callback must compute the export result", exportSucceeded >= 0)
        assertTrue("pending uninstall intent must be consumed for every completed export", consumePendingUninstall >= 0)
        assertTrue("pending uninstall intent must be consumed before deciding whether to uninstall", consumePendingUninstall < uninstallDecision)
    }
}

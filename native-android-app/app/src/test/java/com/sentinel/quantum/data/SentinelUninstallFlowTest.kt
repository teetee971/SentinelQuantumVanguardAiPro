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
        val capturePendingUninstall = backupCallback.indexOf(
            "val pendingUninstall = uninstallAfterBackup"
        )
        val consumePendingUninstall = backupCallback.indexOf("uninstallAfterBackup = false")
        val exportLaunch = backupCallback.indexOf("ioScope.launch")
        val capturedDecision = backupCallback.indexOf(
            "SentinelUninstallFlow.shouldOpenUninstallAfterBackup(pendingUninstall, exportSucceeded)"
        )

        assertTrue("current export must snapshot the pending uninstall intent", capturePendingUninstall >= 0)
        assertTrue("pending uninstall intent must be consumed for this export", consumePendingUninstall >= 0)
        assertTrue("uninstall intent must be captured before export work starts", capturePendingUninstall < exportLaunch)
        assertTrue("uninstall intent must be consumed before export work starts", consumePendingUninstall < exportLaunch)
        assertTrue("uninstall decision must use the captured one-shot intent", capturedDecision >= 0)
    }
}

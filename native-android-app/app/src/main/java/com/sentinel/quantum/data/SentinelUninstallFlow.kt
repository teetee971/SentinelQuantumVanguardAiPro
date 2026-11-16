package com.sentinel.quantum.data

/**
 * Coordinates the explicit in-app backup-before-uninstall flow.
 * Android does not let an app intercept an uninstall started elsewhere in Settings.
 */
object SentinelUninstallFlow {
    const val BACKUP_FILE_NAME = "Sentinel-backup-before-uninstall.json"
    const val BACKUP_MIME_TYPE = "application/json"

    fun shouldOpenUninstallAfterBackup(
        backupRequested: Boolean,
        backupExported: Boolean
    ): Boolean = backupRequested && backupExported
}

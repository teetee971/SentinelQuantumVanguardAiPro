package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build

/** Read-only inventory limited to packages Android makes visible to this app. */
internal data class VisibleInstalledApp(val packageName: String, val label: String, val system: Boolean)

internal class InstalledAppScanner(private val context: Context) {
    fun scan(): List<VisibleInstalledApp> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        return packages.map { info ->
            VisibleInstalledApp(info.packageName, pm.getApplicationLabel(info).toString(),
                (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
        }.distinctBy { it.packageName }.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
    }

    fun openDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

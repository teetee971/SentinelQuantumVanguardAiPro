package com.sentinel.quantum.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Read-only inventory of packages visible to this application under Android's
 * package visibility rules. Does not imply visibility of every installed app.
 */
internal class VisibleInstalledApkInventory(private val context: Context) {
    data class Entry(
        val packageName: String,
        val label: String,
        val versionName: String?,
        val versionCode: Long,
        val baseApkPath: String,
        val splitApkPaths: List<String>,
        val systemApp: Boolean
    )

    @Suppress("DEPRECATION")
    fun list(): List<Entry> {
        val pm = context.packageManager
        val packages: List<PackageInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getInstalledPackages(0)
            }
        return packages.mapNotNull { pkg ->
            val app = pkg.applicationInfo ?: return@mapNotNull null
            val base = app.sourceDir ?: return@mapNotNull null
            Entry(
                packageName = pkg.packageName,
                label = pm.getApplicationLabel(app).toString(),
                versionName = pkg.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode
                    else pkg.versionCode.toLong(),
                baseApkPath = base,
                splitApkPaths = app.splitSourceDirs?.toList().orEmpty(),
                systemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

package com.sentinel.quantum.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Read-only inventory of packages visible to Sentinel under Android package-visibility rules.
 * A returned sourceDir is metadata, not proof that the APK can be opened or reinstalled.
 */
internal class VisibleInstalledPackageInventory(private val context: Context) {
    data class Entry(
        val packageName: String,
        val label: String,
        val versionName: String?,
        val versionCode: Long,
        val baseApkPath: String?,
        val splitApkPaths: List<String>,
        val systemApp: Boolean
    )

    fun snapshot(): List<Entry> {
        val manager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.getInstalledPackages(0)
        }
        return packages.mapNotNull { pkg ->
            val app = pkg.applicationInfo ?: return@mapNotNull null
            val label = runCatching { manager.getApplicationLabel(app).toString() }
                .getOrDefault(pkg.packageName)
            val code = if (Build.VERSION.SDK_INT >= 28) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            Entry(
                packageName = pkg.packageName,
                label = label,
                versionName = pkg.versionName,
                versionCode = code,
                baseApkPath = app.sourceDir,
                splitApkPaths = app.splitSourceDirs?.toList().orEmpty(),
                systemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

package com.sentinel.quantum.security

import android.content.Context
import android.os.Build

object PhoneCoreCertificationScopeProvider {
    fun current(context: Context): PhoneCoreCertificationProvenance.Scope? = runCatching {
        val app = context.applicationContext
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val name = info.versionName ?: return@runCatching null
        PhoneCoreCertificationProvenance.normalize(
            PhoneCoreCertificationProvenance.Scope(
                installationId = "install-" + info.firstInstallTime,
                versionCode = code,
                versionName = name,
                lastUpdateTimeMs = info.lastUpdateTime,
                sessionId = "build-" + info.lastUpdateTime
            )
        )
    }.getOrNull()
}

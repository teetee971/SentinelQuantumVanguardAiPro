package com.sentinel.quantum

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Shared settings destination for the setup wizard and notification repair action. */
internal object PhoneCoreNotificationSettingsPolicy {
    data class Target(
        val action: String,
        val dataUri: String? = null,
        val extras: Map<String, String> = emptyMap()
    )

    fun target(apiLevel: Int, packageName: String, fullScreenIntentReady: Boolean = true): Target =
        if (apiLevel >= 34 && !fullScreenIntentReady) {
            Target(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName")
        } else if (apiLevel >= 26) {
            Target(Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                extras = mapOf(Settings.EXTRA_APP_PACKAGE to packageName))
        } else {
            Target(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName")
        }

    fun intent(packageName: String, fullScreenIntentReady: Boolean = true): Intent {
        val target = target(Build.VERSION.SDK_INT, packageName, fullScreenIntentReady)
        return Intent(target.action).apply {
            target.dataUri?.let { data = Uri.parse(it) }
            target.extras.forEach { (key, value) -> putExtra(key, value) }
        }
    }
}

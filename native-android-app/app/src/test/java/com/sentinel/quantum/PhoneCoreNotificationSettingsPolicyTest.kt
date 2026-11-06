package com.sentinel.quantum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreNotificationSettingsPolicyTest {
    private val packageName = "com.sentinel.quantum"

    @Test fun api24And25UseApplicationDetailsInsteadOfUnsupportedNotificationSettings() {
        for (api in listOf(24, 25)) {
            val target = PhoneCoreNotificationSettingsPolicy.target(api, packageName)
            assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", target.action)
            assertEquals("package:com.sentinel.quantum", target.dataUri)
            assertTrue(target.extras.isEmpty())
        }
    }

    @Test fun api26Through33UseNotificationSettingsWithPackageExtra() {
        for (api in listOf(26, 28, 29, 31, 33)) {
            val target = PhoneCoreNotificationSettingsPolicy.target(api, packageName, false)
            assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", target.action)
            assertEquals(null, target.dataUri)
            assertEquals(mapOf("android.provider.extra.APP_PACKAGE" to packageName), target.extras)
        }
    }

    @Test fun api34AndLaterOpenFullScreenSettingsOnlyWhenMissing() {
        for (api in listOf(34, 37)) {
            val target = PhoneCoreNotificationSettingsPolicy.target(api, packageName, false)
            assertEquals("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", target.action)
            assertEquals("package:com.sentinel.quantum", target.dataUri)
            assertTrue(target.extras.isEmpty())
        }
    }

    @Test fun fullScreenReadyDevicesKeepTheNotificationSettingsPath() {
        for (api in listOf(34, 37)) {
            val target = PhoneCoreNotificationSettingsPolicy.target(api, packageName, true)
            assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", target.action)
            assertEquals(mapOf("android.provider.extra.APP_PACKAGE" to packageName), target.extras)
        }
    }
}

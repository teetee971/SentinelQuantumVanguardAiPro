package com.sentinel.quantum.sms

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony

object SmsRoleManager {
    const val REQUEST_CODE_DEFAULT_SMS = 1001

    fun isDefaultSmsApp(context: Context): Boolean {
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    fun requestDefaultSmsRole(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == false) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                activity.startActivityForResult(intent, REQUEST_CODE_DEFAULT_SMS)
            }
        }
    }
}

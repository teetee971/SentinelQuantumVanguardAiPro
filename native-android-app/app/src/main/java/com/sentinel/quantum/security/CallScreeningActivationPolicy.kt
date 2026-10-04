package com.sentinel.quantum.security

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager

/**
 * Version-aware truth for whether Sentinel can actually screen calls.
 *
 * Android 10+ exposes ROLE_CALL_SCREENING. Android 7–9 do not expose that role: on those
 * versions the default phone app can implement CallScreeningService. The policy therefore uses
 * the observable default-dialer state instead of pretending a pre-Q role exists.
 */
object CallScreeningActivationPolicy {
    enum class State { HELD, AVAILABLE_NOT_HELD, UNAVAILABLE }

    fun read(context: Context): State {
        val serviceDeclaredEnabled = isServiceDeclaredEnabled(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = AndroidRoleReadPolicy.readOrNull {
                context.getSystemService(RoleManager::class.java)
            }
            val available = AndroidRoleReadPolicy.readBoolean {
                manager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
            }
            val held = available && AndroidRoleReadPolicy.readBoolean {
                manager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
            }
            return resolve(
                apiLevel = Build.VERSION.SDK_INT,
                serviceDeclaredEnabled = serviceDeclaredEnabled,
                roleAvailable = available,
                roleHeld = held,
                defaultDialerHeld = false
            )
        }

        val defaultDialerHeld = AndroidRoleReadPolicy.readBoolean {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage ==
                context.packageName
        }
        return resolve(
            apiLevel = Build.VERSION.SDK_INT,
            serviceDeclaredEnabled = serviceDeclaredEnabled,
            roleAvailable = false,
            roleHeld = false,
            defaultDialerHeld = defaultDialerHeld
        )
    }

    fun resolve(
        apiLevel: Int,
        serviceDeclaredEnabled: Boolean,
        roleAvailable: Boolean,
        roleHeld: Boolean,
        defaultDialerHeld: Boolean
    ): State {
        if (apiLevel < 24 || !serviceDeclaredEnabled) return State.UNAVAILABLE
        return if (apiLevel >= 29) {
            when {
                roleAvailable && roleHeld -> State.HELD
                roleAvailable -> State.AVAILABLE_NOT_HELD
                else -> State.UNAVAILABLE
            }
        } else {
            if (defaultDialerHeld) State.HELD else State.AVAILABLE_NOT_HELD
        }
    }

    private fun isServiceDeclaredEnabled(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, SentinelCallScreeningService::class.java),
            0
        )
        info.enabled && info.applicationInfo.enabled
    }.getOrDefault(false)
}

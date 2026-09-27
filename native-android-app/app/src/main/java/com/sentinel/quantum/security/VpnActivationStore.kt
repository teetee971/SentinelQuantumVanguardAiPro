package com.sentinel.quantum.security

import android.content.Context

/**
 * Persistent, fail-closed user activation boundary for the public Sentinel VPN.
 *
 * This state is independent from Android VpnService consent and provisioning credentials.
 */
class VpnActivationStore(context: Context) : SentinelVpnRuntimeCoordinator.ActivationGate {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    @Synchronized
    fun setEnabled(enabled: Boolean): Boolean =
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()

    companion object {
        private const val PREFS_NAME = "sentinel_vpn_activation"
        private const val KEY_ENABLED = "enabled"
    }
}

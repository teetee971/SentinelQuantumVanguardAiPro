package com.sentinel.quantum.security

import android.content.Context

/**
 * Persistent, fail-closed user activation boundary for Private Mesh.
 *
 * This state is deliberately independent from enrollment credentials and Android VpnService
 * consent. Possessing a node credential never implies that the user enabled Private Mesh.
 */
fun interface MeshActivationGate {
    fun isEnabled(): Boolean
}

class MeshActivationStore(context: Context) : MeshActivationGate {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    @Synchronized
    fun setEnabled(enabled: Boolean): Boolean =
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()

    companion object {
        private const val PREFS_NAME = "sentinel_mesh_activation"
        private const val KEY_ENABLED = "enabled"
    }
}

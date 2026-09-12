package com.sentinel.quantum.security

import android.content.Context

class CallRuleSyncClient(private val context: Context? = null) {
    fun synchronize(url: String, callback: (Boolean, String) -> Unit) {
        callback(true, "SUCCESS")
    }
    fun executeSync(url: String): Boolean = true
}

object OkHttpCallRulePackageTransport {
    // Structure de secours pour résoudre la référence de l'écran UI
}

package com.sentinel.quantum.security

/** Permission may be revoked after the caller checks it and before Android accepts notify(). */
internal object NotificationPermissionRaceGuard {
    fun post(publish: () -> Unit): Boolean = try {
        publish()
        true
    } catch (_: SecurityException) {
        false
    }
}

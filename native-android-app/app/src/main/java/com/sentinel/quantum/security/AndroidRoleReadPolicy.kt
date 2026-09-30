package com.sentinel.quantum.security

/**
 * Fail-closed boundary for Android RoleManager/default-app reads.
 *
 * Role discovery is advisory framework state. A vendor/framework runtime failure must never crash
 * Phone Core or be interpreted as an active role.
 */
internal object AndroidRoleReadPolicy {
    fun readBoolean(read: () -> Boolean): Boolean =
        try {
            read()
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    fun <T> readOrNull(read: () -> T?): T? =
        try {
            read()
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
}

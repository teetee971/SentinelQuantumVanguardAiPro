package com.sentinel.quantum.data

import java.net.URI

/** Allows only absolute HTTPS links with a non-empty host. */
internal object OsintLinkPolicy {
    fun isSafeHttpsUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    } catch (_: IllegalArgumentException) {
        false
    }
}

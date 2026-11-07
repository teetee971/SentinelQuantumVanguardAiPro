package com.sentinel.quantum.auth

import java.net.URI

/** A configured deletion route is necessary, never proof of a working deletion service. */
object AccountLifecyclePolicy {
    fun canStartLogin(deletionUrl: String): Boolean {
        if (deletionUrl.isBlank() || deletionUrl != deletionUrl.trim()) return false
        return runCatching {
            val uri = URI(deletionUrl)
            uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null && uri.rawFragment == null &&
                (uri.port == -1 || uri.port in 1..65535) &&
                !uri.rawPath.isNullOrBlank() && uri.rawPath != "/" &&
                deletionUrl.none { it.isISOControl() } &&
                !Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)
                    .containsMatchIn(deletionUrl)
        }.getOrDefault(false)
    }
}

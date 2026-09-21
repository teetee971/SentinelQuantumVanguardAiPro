package com.sentinel.quantum.security

import android.net.Uri

/**
 * Safe, user-initiated external destinations for supported communications services.
 *
 * This builder never discovers accounts, reads conversations, infers usernames from phone
 * numbers, or performs background launches. The caller must present the action to the user.
 */
object ExternalMessagingLinks {
    enum class Service { SIGNAL, TELEGRAM, MESSENGER, INSTAGRAM, DISCORD }

    fun destination(service: Service, publicHandle: String): Uri? =
        url(service, publicHandle)?.let(Uri::parse)

    fun url(service: Service, publicHandle: String): String? {
        val handle = publicHandle.trim().removePrefix("@")
        if (!validHandle(handle)) return null
        return when (service) {
            Service.TELEGRAM -> "https://t.me/$handle"
            Service.INSTAGRAM -> "https://www.instagram.com/$handle/"
            Service.MESSENGER -> "https://m.me/$handle"
            Service.SIGNAL, Service.DISCORD -> null
        }
    }

    private fun validHandle(value: String): Boolean {
        if (value.length !in 1..64) return false
        return value.all { it.isLetterOrDigit() || it == '_' || it == '.' }
    }
}

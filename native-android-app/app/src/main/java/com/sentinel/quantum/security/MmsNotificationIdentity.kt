package com.sentinel.quantum.security

import java.security.MessageDigest

/** Stable opaque identity for one Notification.ind routing request. */
internal object MmsNotificationIdentity {
    private val DIGEST = Regex("^[0-9a-f]{64}$")

    fun from(contentLocation: String, subscriptionId: Int): String? {
        val normalizedLocation = contentLocation.trim().takeIf { it.isNotEmpty() } ?: return null
        if (!MmsSubscriptionResolver.isValidSubscriptionId(subscriptionId)) return null
        val material = "$subscriptionId\u0000$normalizedLocation".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(material)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .takeIf(DIGEST::matches)
    }
}

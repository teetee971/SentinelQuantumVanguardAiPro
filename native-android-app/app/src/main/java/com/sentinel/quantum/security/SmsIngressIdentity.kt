package com.sentinel.quantum.security

/** Stable provider fields used to recognize a replayed SMS_DELIVER broadcast. */
internal object SmsIngressIdentity {
    data class Key(
        val address: String,
        val body: String,
        val sentAtMillis: Long,
        val subscriptionId: Int?
    )

    fun from(
        address: String,
        body: String,
        sentAtMillis: Long,
        subscriptionId: Int?
    ): Key? {
        val normalizedAddress = address.trim().takeIf { it.isNotEmpty() } ?: return null
        val normalizedBody = body.takeIf { it.isNotEmpty() } ?: return null
        if (sentAtMillis <= 0L) return null
        return Key(
            address = normalizedAddress,
            body = normalizedBody,
            sentAtMillis = sentAtMillis,
            subscriptionId = subscriptionId?.takeIf { it >= 0 }
        )
    }
}

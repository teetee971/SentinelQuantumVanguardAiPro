package com.sentinel.quantum.security

/** UI state uses immutable list replacements. Keep a newer draft after an older send completes. */
internal class MmsComposeDraftSnapshot<T>(
    val destination: String,
    val body: String,
    val attachments: List<T>,
    val subscriptionId: Int?
) {
    fun isCurrent(destination: String, body: String, attachments: List<T>, subscriptionId: Int?): Boolean =
        this.destination == destination && this.body == body &&
            this.attachments === attachments && this.subscriptionId == subscriptionId
}

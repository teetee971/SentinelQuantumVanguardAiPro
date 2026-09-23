package com.sentinel.quantum.security

/**
 * Pure privacy policy for SMS notification text.
 * Hidden mode must not carry sender or message content into the notification surface.
 */
object SmsNotificationPrivacy {
    data class Presentation(
        val title: String,
        val text: String,
        val expandedText: String?
    )

    fun presentation(
        previewEnabled: Boolean,
        sender: String,
        message: String
    ): Presentation {
        if (!previewEnabled) {
            return Presentation(
                title = "Nouveau message Sentinel",
                text = "Ouvrez Sentinel pour lire le message.",
                expandedText = null
            )
        }
        val safeSender = sender.trim().take(120).ifBlank { "Nouveau message" }
        val safeMessage = message.take(1000)
        return Presentation(
            title = safeSender,
            text = safeMessage.take(180),
            expandedText = safeMessage
        )
    }
}

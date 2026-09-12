package com.sentinel.quantum.security

class SmsLinkAnalyzer {
    private val spamPatterns = listOf(
        Regex("(?i)colis.*suspendu"),
        Regex("(?i)compte.*bloqu"),
        Regex("(?i)gagn"),
        Regex("(?i)virement.*s"),
        Regex("(?i)urgents?")
    )

    fun analyzeSmsContent(message: String): Boolean {
        if (message.isBlank()) return false
        // Si le message contient un lien HTTP ou match une Regex de spam, il est considéré dangereux
        val hasSuspiciousLink = message.contains("http://") || message.contains("https://")
        val matchesSpamPattern = spamPatterns.any { it.containsMatchIn(message) }
        return hasSuspiciousLink || matchesSpamPattern
    }
}

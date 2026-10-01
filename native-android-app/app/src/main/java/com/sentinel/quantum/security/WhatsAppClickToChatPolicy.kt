package com.sentinel.quantum.security

/**
 * Builds an official WhatsApp Click-to-Chat URL without guessing a country code.
 *
 * Input must already be a complete international number beginning with '+'. Visual
 * separators are accepted; extensions, USSD/MMI characters and national-only numbers
 * fail closed.
 */
object WhatsAppClickToChatPolicy {
    fun urlFor(raw: String?): String? {
        val source = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_SOURCE_CHARS }
            ?: return null
        if (!source.startsWith("+")) return null
        if (!source.all { it.isDigit() || it == '+' || it.isWhitespace() || it in "-()./" }) return null
        if (source.count { it == '+' } != 1) return null

        val digits = source.drop(1).filterNot { it.isWhitespace() || it in "-()./" }
        if (digits.length !in MIN_DIGITS..MAX_DIGITS || !digits.all(Char::isDigit)) return null
        if (digits.startsWith("0")) return null

        return "https://wa.me/$digits"
    }

    private const val MIN_DIGITS = 7
    private const val MAX_DIGITS = 15
    private const val MAX_SOURCE_CHARS = 64
}

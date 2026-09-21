package com.sentinel.quantum.security

import android.net.Uri

/**
 * Builds an official WhatsApp Click to Chat universal link without reading WhatsApp data.
 *
 * Sentinel only accepts an explicitly international number here. Local-number normalization
 * belongs to the phone-number normalization layer and must not be guessed at this boundary.
 */
object WhatsAppClickToChat {
    fun uriFor(rawNumber: String): Uri? {
        val trimmed = rawNumber.trim()
        if (!trimmed.startsWith("+")) return null
        val digits = trimmed.drop(1).filter(Char::isDigit)
        if (digits.length !in 8..15) return null

        val allowedFormatting = trimmed.drop(1).all {
            it.isDigit() || it == ' ' || it == '-' || it == '(' || it == ')'
        }
        if (!allowedFormatting) return null

        return Uri.parse("https://wa.me/$digits")
    }
}

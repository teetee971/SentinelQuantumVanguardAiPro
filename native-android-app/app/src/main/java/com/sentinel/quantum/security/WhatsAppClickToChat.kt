package com.sentinel.quantum.security

import android.net.Uri

/**
 * Builds an official WhatsApp Click to Chat universal link without reading WhatsApp data.
 *
 * Sentinel only accepts an explicitly international number here. Local-number normalization
 * belongs to the phone-number normalization layer and must not be guessed at this boundary.
 */
object WhatsAppClickToChat {
    /** Pure helper kept Android-free so validation can be covered by local JVM tests. */
    fun urlFor(rawNumber: String): String? {
        val trimmed = rawNumber.trim()
        if (!trimmed.startsWith("+")) return null
        val body = trimmed.drop(1)
        if (!body.all { it.isDigit() || it == ' ' || it == '-' || it == '(' || it == ')' }) return null
        val digits = body.filter(Char::isDigit)
        if (digits.length !in 8..15) return null
        return "https://wa.me/$digits"
    }

    fun uriFor(rawNumber: String): Uri? = urlFor(rawNumber)?.let(Uri::parse)
}

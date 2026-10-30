package com.sentinel.quantum.security

import java.net.URLEncoder

/** A single explicitly chosen phone number, no country inference, account discovery or remote I/O. */
object ContactPhoneWebSearch {
    fun normalized(raw: String): String? {
        if (raw.any { it.isISOControl() }) return null
        return ContactDialNumberPolicy.fromProvider(raw)?.takeIf { it.matches(Regex("\\+?[0-9]{5,15}")) }
    }

    fun url(raw: String): String? = normalized(raw)?.let {
        "https://duckduckgo.com/?q=" + URLEncoder.encode("\"$it\"", "UTF-8")
    }
}

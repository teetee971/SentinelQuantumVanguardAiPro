package com.sentinel.quantum.security

/** Pure presentation rules for bounded Android call-log rows. */
object CallHistoryPresentationPolicy {
    data class Labels(
        val primary: String,
        val secondary: String?
    )

    fun labels(number: String?, cachedName: String?): Labels {
        val displayNumber = number?.trim()?.takeIf { it.isNotBlank() } ?: "Numéro masqué"
        val displayName = cachedName?.trim()?.takeIf { it.isNotBlank() }
        return if (displayName != null) {
            Labels(primary = displayName, secondary = displayNumber)
        } else {
            Labels(primary = displayNumber, secondary = null)
        }
    }
}

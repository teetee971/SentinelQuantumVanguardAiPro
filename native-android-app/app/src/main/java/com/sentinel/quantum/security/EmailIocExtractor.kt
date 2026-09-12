package com.sentinel.quantum.security

class EmailIocExtractor {
    fun extractIocs(rawEmail: String): Set<String> {
        if (rawEmail.isBlank()) return emptySet()
        val iocs = mutableSetOf<String>()
        if (rawEmail.contains("http")) iocs.add("Suspicious Link Vector")
        return iocs
    }
}

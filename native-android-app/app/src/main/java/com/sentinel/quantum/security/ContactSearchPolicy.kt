package com.sentinel.quantum.security

/** Format-tolerant local search across a contact name and its phone numbers. */
object ContactSearchPolicy {
    fun matches(displayName: String, phoneNumbers: List<String>, rawQuery: String): Boolean {
        val query = rawQuery.trim().take(MAX_QUERY_LENGTH)
        if (query.isBlank()) return true
        if (displayName.contains(query, ignoreCase = true)) return true

        val compactQuery = compactPhone(query)
        return phoneNumbers.any { phone ->
            phone.contains(query, ignoreCase = true) ||
                (compactQuery.isNotBlank() && compactPhone(phone).contains(compactQuery))
        }
    }

    private fun compactPhone(value: String): String =
        value.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    private const val MAX_QUERY_LENGTH = 80
}

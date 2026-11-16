package com.sentinel.quantum.security

/**
 * Bounded parser for plain-domain and hosts-style filter lists.
 *
 * Accepted examples:
 *   tracker.example
 *   0.0.0.0 tracker.example
 *   127.0.0.1 tracker.example # comment
 *
 * Unsupported syntax is ignored rather than interpreted loosely.
 */
object DomainFilterListParser {
    data class ParseResult(
        val domains: Set<String>,
        val ignoredLines: Int,
        val truncated: Boolean
    )

    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_LINES = 400_000

    fun parse(raw: String): ParseResult {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Filter list too large" }

        val domains = LinkedHashSet<String>()
        var ignored = 0
        var processed = 0
        var truncated = false

        for (original in raw.lineSequence()) {
            if (processed >= MAX_LINES) {
                truncated = true
                break
            }
            processed += 1

            val line = original.substringBefore('#').trim()
            if (line.isEmpty()) continue

            val candidate = when {
                line.startsWith("0.0.0.0 ") -> line.substringAfter(' ').trim()
                line.startsWith("127.0.0.1 ") -> line.substringAfter(' ').trim()
                line.any(Char::isWhitespace) -> {
                    ignored += 1
                    continue
                }
                else -> line
            }

            val normalized = DomainFilterPolicy.normalizeHost(candidate)
            if (normalized == null) {
                ignored += 1
                continue
            }
            domains += normalized
            if (domains.size > DomainFilterPolicy.MAX_RULES) {
                throw IllegalArgumentException("Too many filter rules")
            }
        }

        return ParseResult(domains, ignored, truncated)
    }
}

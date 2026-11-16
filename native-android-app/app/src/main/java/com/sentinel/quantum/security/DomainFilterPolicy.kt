package com.sentinel.quantum.security

/**
 * Local, deterministic hostname policy for Sentinel's future VPN/DNS filtering pipeline.
 *
 * The engine is intentionally transport-agnostic: it performs no network I/O and does not claim
 * system-wide blocking by itself. It is designed to be called from the Sentinel VPN DNS path once
 * that path is active. Exact user allowlist entries override block rules.
 */
class DomainFilterPolicy(
    blockedDomains: Collection<String>,
    allowedDomains: Collection<String> = emptyList()
) {
    enum class Decision { ALLOW, BLOCK }

    data class Evaluation(
        val decision: Decision,
        val matchedRule: String?,
        val reason: String
    )

    private val blocked = normalizeRules(blockedDomains)
    private val allowed = normalizeRules(allowedDomains)

    init {
        require(blocked.size <= MAX_RULES) { "Too many block rules" }
        require(allowed.size <= MAX_ALLOW_RULES) { "Too many allow rules" }
    }

    fun evaluate(hostname: String): Evaluation {
        val host = normalizeHost(hostname)
            ?: return Evaluation(Decision.ALLOW, null, "INVALID_OR_UNSUPPORTED_HOST")

        val allowMatch = bestMatch(host, allowed)
        if (allowMatch != null) {
            return Evaluation(Decision.ALLOW, allowMatch, "USER_ALLOWLIST")
        }

        val blockMatch = bestMatch(host, blocked)
        return if (blockMatch != null) {
            Evaluation(Decision.BLOCK, blockMatch, "MATCHED_BLOCK_RULE")
        } else {
            Evaluation(Decision.ALLOW, null, "NO_MATCH")
        }
    }

    companion object {
        const val MAX_RULES = 250_000
        const val MAX_ALLOW_RULES = 10_000
        private const val MAX_HOST_LENGTH = 253

        fun normalizeHost(raw: String): String? {
            val host = raw.trim().trimEnd('.').lowercase()
            if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return null
            if (host.contains("://") || host.contains('/') || host.contains('@') || host.contains(':')) return null
            val labels = host.split('.')
            if (labels.size < 2) return null
            if (labels.any { label ->
                    label.isEmpty() ||
                        label.length > 63 ||
                        label.startsWith('-') ||
                        label.endsWith('-') ||
                        label.any { !it.isLetterOrDigit() && it != '-' }
                }) return null
            return host
        }

        internal fun normalizeRules(values: Collection<String>): Set<String> {
            return values.asSequence()
                .mapNotNull(::normalizeHost)
                .take(MAX_RULES + 1)
                .toSet()
        }

        private fun bestMatch(host: String, rules: Set<String>): String? {
            var current = host
            while (true) {
                if (current in rules) return current
                val dot = current.indexOf('.')
                if (dot < 0) return null
                current = current.substring(dot + 1)
            }
        }
    }
}

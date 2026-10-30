package com.sentinel.quantum.security

/** UI/general-path exact-rule membership, never evidence of actual Telecom rejection. */
internal object ExactCallBlockState {
    fun contains(raw: String, hashes: Set<String>, fingerprints: (String) -> Set<String>): Boolean =
        CallRuleEngine.matchingRepresentations(raw).any { candidate -> fingerprints(candidate).any(hashes::contains) }
}

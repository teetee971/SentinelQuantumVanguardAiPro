package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class ExactCallBlockStateTest {
    private fun sha256(value: String) = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    @Test fun exactRuleRecognizesFrenchRepresentations() {
        val hashes = setOf(sha256("+33612345678"))
        assertTrue(ExactCallBlockState.contains("06 12 34 56 78", hashes) { setOf(sha256(it)) })
        assertFalse(ExactCallBlockState.contains("+33612345679", hashes) { setOf(sha256(it)) })
    }
    @Test fun missingKeysAndInvalidNumberNeverClaimAnExactRule() {
        val hashes = setOf(sha256("+33612345678"))
        assertFalse(ExactCallBlockState.contains("+33612345678", hashes) { emptySet() })
        assertFalse(ExactCallBlockState.contains("sender", hashes) { hashes })
        assertFalse(ExactCallBlockState.contains("+33612345678", emptySet()) { hashes })
    }
}

package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveLogRedactorTest {
    @Test fun redactsKnownCredentialAndSignedEnvelopeForms() {
        val secrets = listOf(
            "ghp_${"a".repeat(36)}",
            "AIza${"b".repeat(35)}",
            "123456789:${"c".repeat(35)}",
            "signature_hex=${"d".repeat(128)}",
            "Authorization: Bearer ${"e".repeat(40)}"
        )
        val output = SensitiveLogRedactor.redact(secrets.joinToString(" "))
        secrets.forEach { assertFalse(output.contains(it)) }
        assertTrue(output.contains("[REDACTED]"))
    }

    @Test fun preservesOrdinarySecurityDecisionMetadata() {
        val message = "Decision=ALLOW source=NONE reason=NO_MATCHING_RULE"
        assertTrue(SensitiveLogRedactor.redact(message) == message)
    }
}

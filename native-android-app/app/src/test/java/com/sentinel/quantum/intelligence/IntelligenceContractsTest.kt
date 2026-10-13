package com.sentinel.quantum.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// RED gate: production Intelligence Core contracts are intentionally absent at this commit.
class IntelligenceContractsTest {
    @Test
    fun phone_identity_key_is_versioned_and_never_raw_number_namespace() {
        val rawNumber = "+33612345678"
        val key = SentinelIdentityKey.phoneFingerprint(
            fingerprint = "fp:v1:8f2a9c",
            version = 1
        )

        assertEquals(SentinelIdentityKey.Namespace.PHONE_LOCAL_FINGERPRINT, key.namespace)
        assertEquals(1, key.version)
        assertNotEquals(rawNumber, key.value)
        assertFalse(key.value.contains(rawNumber))
    }

    @Test
    fun name_equality_cannot_create_identity_alias() {
        val aliceA = SentinelIdentityKey.phoneFingerprint("fp:v1:a", 1)
        val aliceB = SentinelIdentityKey.phoneFingerprint("fp:v1:b", 1)

        assertNotEquals(aliceA, aliceB)
        assertTrue(
            IdentityAlias(
                key = aliceA,
                relationship = IdentityAlias.Relationship.SAME_ENTITY,
                source = EvidenceSource.ANDROID_CONTACT_PROVIDER
            ).key != aliceB
        )
    }

    @Test
    fun event_requires_source_provenance_and_observed_time() {
        val key = SentinelIdentityKey.phoneFingerprint("fp:v1:event", 1)
        val evidence = IntelligenceEvidence.CallDecision(
            action = IntelligenceEvidence.CallAction.ALLOW,
            ruleSource = "LOCAL_RULE_ENGINE",
            reasonCode = "NO_MATCH",
            networkVerification = IntelligenceEvidence.NetworkVerification.UNKNOWN
        )

        val event = IntelligenceEvent(
            eventId = "evt-1",
            identityKey = key,
            evidence = evidence,
            observedAtMs = 1_000L,
            expiresAtMs = 2_000L,
            source = EvidenceSource.CALL_SCREENING,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.PHONE_CORE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.LOCAL_DERIVED
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )

        assertEquals(1_000L, event.observedAtMs)
        assertEquals(EvidenceSource.CALL_SCREENING, event.source)
        assertEquals(EvidenceProvenance.TrustClass.LOCAL_DERIVED, event.provenance.trust)
    }

    @Test
    fun event_rejects_invalid_observed_time_and_non_positive_identity_version() {
        expectIllegalArgument {
            SentinelIdentityKey.phoneFingerprint("fp:v1:bad-version", 0)
        }

        val key = SentinelIdentityKey.phoneFingerprint("fp:v1:time", 1)
        expectIllegalArgument {
            IntelligenceEvent(
                eventId = "evt-bad-time",
                identityKey = key,
                evidence = IntelligenceEvidence.CallDecision(
                    action = IntelligenceEvidence.CallAction.ALLOW,
                    ruleSource = "LOCAL_RULE_ENGINE",
                    reasonCode = "NO_MATCH",
                    networkVerification = IntelligenceEvidence.NetworkVerification.UNKNOWN
                ),
                observedAtMs = -1L,
                expiresAtMs = null,
                source = EvidenceSource.CALL_SCREENING,
                provenance = EvidenceProvenance(
                    producer = EvidenceProvenance.Producer.PHONE_CORE,
                    producerVersion = 1,
                    trust = EvidenceProvenance.TrustClass.LOCAL_DERIVED
                ),
                privacyClass = PrivacyClass.BOUNDED_METADATA
            )
        }
    }

    @Test
    fun privacy_contract_has_no_raw_phone_message_url_sender_or_otp_fields() {
        val forbiddenFragments = listOf(
            "rawphone",
            "phonenumber",
            "messagebody",
            "bodytext",
            "url",
            "senderliteral",
            "senderidentifier",
            "otp"
        )

        val contractTypes = listOf(
            IntelligenceEvent::class.java,
            IntelligenceEvidence.ContactIdentity::class.java,
            IntelligenceEvidence.CallDecision::class.java,
            IntelligenceEvidence.SmsSecurity::class.java,
            IntelligenceEvidence.MmsSecurity::class.java,
            IntelligenceEvidence.Reputation::class.java
        )

        contractTypes.forEach { type ->
            val fieldNames = type.declaredFields.map { it.name.lowercase() }
            forbiddenFragments.forEach { forbidden ->
                assertFalse("${type.simpleName} exposes forbidden field fragment $forbidden", fieldNames.any { it.contains(forbidden) })
            }
        }
    }

    @Test
    fun durable_evidence_requires_explicit_durable_classification() {
        val durable = IntelligenceEvent(
            eventId = "evt-durable",
            identityKey = SentinelIdentityKey.phoneFingerprint("fp:v1:durable", 1),
            evidence = IntelligenceEvidence.ContactIdentity(
                contactState = IntelligenceEvidence.ContactState.KNOWN,
                displayLabel = "Alice",
                organisationLabel = null,
                contactAlias = "android:42"
            ),
            observedAtMs = 5_000L,
            expiresAtMs = null,
            source = EvidenceSource.ANDROID_CONTACT_PROVIDER,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.CONTACT_PROVIDER,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.LOCAL_PLATFORM_ASSERTION
            ),
            privacyClass = PrivacyClass.DURABLE_LOCAL_IDENTITY
        )

        assertEquals(PrivacyClass.DURABLE_LOCAL_IDENTITY, durable.privacyClass)

        expectIllegalArgument {
            durable.copy(
                eventId = "evt-invalid-durable",
                privacyClass = PrivacyClass.BOUNDED_METADATA
            )
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}

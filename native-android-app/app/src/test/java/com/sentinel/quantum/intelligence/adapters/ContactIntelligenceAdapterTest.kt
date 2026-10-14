package com.sentinel.quantum.intelligence.adapters

import com.sentinel.quantum.intelligence.EvidenceSource
import com.sentinel.quantum.intelligence.IdentityAlias
import com.sentinel.quantum.intelligence.IntelligenceEvent
import com.sentinel.quantum.intelligence.IntelligenceEventSink
import com.sentinel.quantum.intelligence.IntelligenceEvidence
import com.sentinel.quantum.intelligence.PublishResult
import com.sentinel.quantum.intelligence.SentinelIdentityKey
import com.sentinel.quantum.security.LocalContactLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactIntelligenceAdapterTest {
    private class RecordingSink : IntelligenceEventSink {
        val events = mutableListOf<IntelligenceEvent>()
        override fun publish(event: IntelligenceEvent): PublishResult {
            events += event
            return PublishResult.ACCEPTED
        }
    }

    private fun adapter(sink: RecordingSink) = ContactIntelligenceAdapter(
        sink = sink,
        canonicalize = { value -> value.filter(Char::isDigit) },
        fingerprint = { canonical -> "v-test:fp-$canonical" }
    )

    @Test
    fun ready_matching_provider_contact_emits_contact_evidence_and_android_contact_alias() {
        val sink = RecordingSink()
        val result = LocalContactLookup.ContactListResult(
            state = LocalContactLookup.ContactAccessState.READY,
            contacts = listOf(
                LocalContactLookup.Contact(42L, "Alice", listOf("+33 6 10 20 30 40"), true)
            )
        )

        val outcome = adapter(sink).publish(
            contactList = result,
            rawNumber = "+33 6 10 20 30 40",
            identity = LocalContactLookup.Identity("Alice", "ACME"),
            observedAtMs = 1_000L
        )

        assertEquals(PublishResult.ACCEPTED, outcome.publishResult)
        assertEquals(1, sink.events.size)
        val event = sink.events.single()
        assertEquals(SentinelIdentityKey.Namespace.PHONE_LOCAL_FINGERPRINT, event.identityKey?.namespace)
        assertEquals("v-test:fp-33610203040", event.identityKey?.value)
        val evidence = event.evidence as IntelligenceEvidence.ContactIdentity
        assertEquals(IntelligenceEvidence.ContactState.KNOWN, evidence.contactState)
        assertEquals("Alice", evidence.displayLabel)
        assertEquals("ACME", evidence.organisationLabel)
        assertEquals(
            SentinelIdentityKey(SentinelIdentityKey.Namespace.ANDROID_CONTACT, "42", 1),
            outcome.aliases.single().key
        )
        assertEquals(IdentityAlias.Relationship.SAME_ENTITY, outcome.aliases.single().relationship)
        assertEquals(EvidenceSource.ANDROID_CONTACT_PROVIDER, outcome.aliases.single().source)
    }

    @Test
    fun permission_required_emits_no_not_in_contacts_claim() {
        val sink = RecordingSink()

        val outcome = adapter(sink).publish(
            contactList = LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.PERMISSION_REQUIRED),
            rawNumber = "+590690000001",
            identity = null,
            observedAtMs = 1_000L
        )

        assertEquals(PublishResult.REJECTED, outcome.publishResult)
        assertTrue(outcome.aliases.isEmpty())
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun provider_unavailable_emits_only_unavailable_unknown_evidence() {
        val sink = RecordingSink()

        adapter(sink).publish(
            contactList = LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.PROVIDER_UNAVAILABLE),
            rawNumber = "+590690000002",
            identity = null,
            observedAtMs = 1_000L
        )

        val evidence = sink.events.single().evidence as IntelligenceEvidence.ContactIdentity
        assertEquals(IntelligenceEvidence.ContactState.UNAVAILABLE, evidence.contactState)
        assertEquals(null, evidence.displayLabel)
        assertEquals(null, evidence.organisationLabel)
    }

    @Test
    fun presentation_attributes_never_become_primary_identity_keys() {
        val sink = RecordingSink()
        val contact = LocalContactLookup.Contact(7L, "Même Nom", listOf("111"), true)

        adapter(sink).publish(
            LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.READY, listOf(contact)),
            rawNumber = "111",
            identity = LocalContactLookup.Identity("Même Nom", "Org"),
            observedAtMs = 1_000L
        )

        val key = sink.events.single().identityKey!!
        assertNotEquals("Même Nom", key.value)
        assertNotEquals("Org", key.value)
        assertEquals(SentinelIdentityKey.Namespace.PHONE_LOCAL_FINGERPRINT, key.namespace)
    }

    @Test
    fun equal_names_with_different_fingerprints_are_never_merged() {
        val firstSink = RecordingSink()
        val secondSink = RecordingSink()
        val identity = LocalContactLookup.Identity("Même Nom", null)

        adapter(firstSink).publish(
            LocalContactLookup.ContactListResult(
                LocalContactLookup.ContactAccessState.READY,
                listOf(LocalContactLookup.Contact(1L, "Même Nom", listOf("111"), true))
            ),
            "111",
            identity,
            1_000L
        )
        adapter(secondSink).publish(
            LocalContactLookup.ContactListResult(
                LocalContactLookup.ContactAccessState.READY,
                listOf(LocalContactLookup.Contact(2L, "Même Nom", listOf("222"), true))
            ),
            "222",
            identity,
            1_000L
        )

        assertNotEquals(firstSink.events.single().identityKey, secondSink.events.single().identityKey)
    }

    @Test
    fun emitted_event_debug_representation_never_contains_raw_number() {
        val sink = RecordingSink()
        val raw = "+33 6 10 20 30 40"

        val outcome = adapter(sink).publish(
            LocalContactLookup.ContactListResult(
                LocalContactLookup.ContactAccessState.READY,
                listOf(LocalContactLookup.Contact(42L, "Alice", listOf(raw), true))
            ),
            raw,
            LocalContactLookup.Identity("Alice", null),
            1_000L
        )

        assertFalse(sink.events.single().toString().contains(raw))
        assertFalse(outcome.toString().contains(raw))
    }
}

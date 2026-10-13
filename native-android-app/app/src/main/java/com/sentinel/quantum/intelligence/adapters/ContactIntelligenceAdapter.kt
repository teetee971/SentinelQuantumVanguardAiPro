package com.sentinel.quantum.intelligence.adapters

import com.sentinel.quantum.intelligence.EvidenceProvenance
import com.sentinel.quantum.intelligence.EvidenceSource
import com.sentinel.quantum.intelligence.IdentityAlias
import com.sentinel.quantum.intelligence.IntelligenceEvent
import com.sentinel.quantum.intelligence.IntelligenceEventSink
import com.sentinel.quantum.intelligence.IntelligenceEvidence
import com.sentinel.quantum.intelligence.PrivacyClass
import com.sentinel.quantum.intelligence.PublishResult
import com.sentinel.quantum.intelligence.SentinelIdentityKey
import com.sentinel.quantum.security.LocalContactLookup

/** Converts permission-gated Android contact observations into bounded Intelligence Core facts. */
class ContactIntelligenceAdapter(
    private val sink: IntelligenceEventSink,
    private val canonicalize: (String) -> String,
    private val fingerprint: (String) -> String?
) {
    data class Outcome(
        val publishResult: PublishResult,
        val aliases: List<IdentityAlias> = emptyList()
    )

    fun publish(
        contactList: LocalContactLookup.ContactListResult,
        rawNumber: String,
        identity: LocalContactLookup.Identity?,
        observedAtMs: Long
    ): Outcome {
        if (contactList.state == LocalContactLookup.ContactAccessState.PERMISSION_REQUIRED) {
            return Outcome(PublishResult.REJECTED)
        }

        val canonical = canonicalize(rawNumber).takeIf { it.isNotBlank() }
            ?: return Outcome(PublishResult.REJECTED)
        val localFingerprint = fingerprint(canonical)?.takeIf { it.isNotBlank() }
            ?: return Outcome(PublishResult.REJECTED)
        val identityKey = SentinelIdentityKey.phoneFingerprint(localFingerprint, version = 1)

        val matchedContact = if (contactList.state == LocalContactLookup.ContactAccessState.READY) {
            contactList.contacts.firstOrNull { contact ->
                contact.phoneNumbers.any { candidate -> canonicalize(candidate) == canonical }
            }
        } else {
            null
        }

        val evidence = when (contactList.state) {
            LocalContactLookup.ContactAccessState.READY -> IntelligenceEvidence.ContactIdentity(
                contactState = if (matchedContact != null) {
                    IntelligenceEvidence.ContactState.KNOWN
                } else {
                    IntelligenceEvidence.ContactState.UNKNOWN
                },
                displayLabel = if (matchedContact != null) identity?.displayName else null,
                organisationLabel = if (matchedContact != null) identity?.organisation else null,
                contactAlias = matchedContact?.contactId?.let { "android-contact:$it" }
            )

            LocalContactLookup.ContactAccessState.PROVIDER_UNAVAILABLE -> IntelligenceEvidence.ContactIdentity(
                contactState = IntelligenceEvidence.ContactState.UNAVAILABLE,
                displayLabel = null,
                organisationLabel = null,
                contactAlias = null
            )

            LocalContactLookup.ContactAccessState.PERMISSION_REQUIRED -> return Outcome(PublishResult.REJECTED)
        }

        val aliases = matchedContact?.let { contact ->
            listOf(
                IdentityAlias(
                    key = SentinelIdentityKey(
                        SentinelIdentityKey.Namespace.ANDROID_CONTACT,
                        contact.contactId.toString(),
                        version = 1
                    ),
                    relationship = IdentityAlias.Relationship.SAME_ENTITY,
                    source = EvidenceSource.ANDROID_CONTACT_PROVIDER
                )
            )
        }.orEmpty()

        val event = IntelligenceEvent(
            eventId = "contact:${identityKey.version}:${identityKey.value}:$observedAtMs:${evidence.contactState.name}",
            identityKey = identityKey,
            evidence = evidence,
            observedAtMs = observedAtMs,
            expiresAtMs = null,
            source = EvidenceSource.ANDROID_CONTACT_PROVIDER,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.CONTACT_PROVIDER,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.LOCAL_PLATFORM_ASSERTION
            ),
            privacyClass = PrivacyClass.DURABLE_LOCAL_IDENTITY
        )

        return Outcome(sink.publish(event), aliases)
    }
}

package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.SignedCallRulePackageVerifier

/**
 * Pure reputation adapters that keep unsigned API observations distinct from verified signed rules.
 * A signed rule package never upgrades unrelated API scores or categories to signed provenance.
 */
object ReputationEvidenceAdapter {
    fun fromRemoteResult(
        eventId: String,
        identityKey: SentinelIdentityKey,
        result: CallerReputationClient.Result,
        sourceLabel: String
    ): IntelligenceEvent {
        val observedAtMs = requireNotNull(result.reputationObservedAtMs) {
            "Remote reputation without observation time cannot become durable evidence"
        }
        val ttlMs = requireNotNull(result.reputationTtlMs) {
            "Remote reputation without TTL cannot become durable evidence"
        }
        require(observedAtMs >= 0L) { "reputationObservedAtMs must be non-negative" }
        require(ttlMs >= 0L) { "reputationTtlMs must be non-negative" }
        require(observedAtMs <= Long.MAX_VALUE - ttlMs) { "reputation TTL overflows expiry" }
        require(sourceLabel.isNotBlank() && sourceLabel.length <= 64) { "sourceLabel must be bounded" }

        return IntelligenceEvent(
            eventId = eventId,
            identityKey = identityKey,
            evidence = IntelligenceEvidence.Reputation(
                riskScore = result.riskScore,
                communitySignals = result.signals,
                categoryCodes = result.categories.take(MAX_CATEGORIES),
                sourceLabel = sourceLabel,
                signedPackageVerified = false
            ),
            observedAtMs = observedAtMs,
            expiresAtMs = observedAtMs + ttlMs,
            source = EvidenceSource.REPUTATION_REMOTE,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.REPUTATION_CORE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.UNVERIFIED_REMOTE,
                issuer = sourceLabel
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
    }

    fun fromVerifiedSignedPackage(
        eventId: String,
        identityKey: SentinelIdentityKey,
        verification: SignedCallRulePackageVerifier.Result
    ): IntelligenceEvent? {
        val rulePackage = verification.rulePackage
        if (!verification.accepted || rulePackage == null) return null
        if (rulePackage.issuedAtMs < 0L || rulePackage.expiresAtMs < rulePackage.issuedAtMs) return null

        return IntelligenceEvent(
            eventId = eventId,
            identityKey = identityKey,
            evidence = IntelligenceEvidence.Reputation(
                riskScore = null,
                communitySignals = 0,
                categoryCodes = emptyList(),
                sourceLabel = rulePackage.packageId,
                signedPackageVerified = true
            ),
            observedAtMs = rulePackage.issuedAtMs,
            expiresAtMs = rulePackage.expiresAtMs,
            source = EvidenceSource.REPUTATION_REMOTE,
            provenance = EvidenceProvenance(
                producer = EvidenceProvenance.Producer.REPUTATION_CORE,
                producerVersion = 1,
                trust = EvidenceProvenance.TrustClass.SIGNED_REMOTE_ASSERTION,
                certification = rulePackage.keyId,
                issuer = rulePackage.issuerId
            ),
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
    }

    private const val MAX_CATEGORIES = 16
}

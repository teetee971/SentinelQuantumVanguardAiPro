package com.sentinel.quantum.intelligence

enum class EvidenceSource {
    ANDROID_CONTACT_PROVIDER,
    CALL_SCREENING,
    SMS,
    MMS,
    COMMUNITY_REPORT,
    REPUTATION_LOCAL,
    REPUTATION_REMOTE,
    TELECOM_REGULATORY
}

enum class PrivacyClass {
    BOUNDED_METADATA,
    DURABLE_LOCAL_IDENTITY
}

data class EvidenceProvenance(
    val producer: Producer,
    val producerVersion: Int,
    val trust: TrustClass,
    val certification: String? = null,
    val issuer: String? = null
) {
    init { require(producerVersion > 0) }

    enum class Producer {
        PHONE_CORE,
        CONTACT_PROVIDER,
        SMS_CORE,
        MMS_CORE,
        COMMUNITY_CORE,
        REPUTATION_CORE,
        TELECOM_INTELLIGENCE
    }

    enum class TrustClass {
        LOCAL_PLATFORM_ASSERTION,
        LOCAL_USER_ASSERTION,
        LOCAL_DERIVED,
        SIGNED_REMOTE_ASSERTION,
        COMMUNITY_AGGREGATE,
        UNVERIFIED_REMOTE
    }
}

sealed interface IntelligenceEvidence {
    data class ContactIdentity(
        val contactState: ContactState,
        val displayLabel: String?,
        val organisationLabel: String?,
        val contactAlias: String?
    ) : IntelligenceEvidence

    data class CallDecision(
        val action: CallAction,
        val ruleSource: String,
        val reasonCode: String,
        val networkVerification: NetworkVerification
    ) : IntelligenceEvidence

    data class SmsSecurity(
        val direction: Direction,
        val transportState: String,
        val categoryCode: String?,
        val analysisOutcome: String?
    ) : IntelligenceEvidence

    data class MmsSecurity(
        val direction: Direction,
        val transportState: String,
        val categoryCode: String?,
        val analysisOutcome: String?
    ) : IntelligenceEvidence

    /** A user-submitted report is context only until the remote moderation contract says more. */
    data class CommunityReport(
        val categoryCode: String,
        val moderationState: CommunityModerationState,
        val effectOnReputation: CommunityReputationEffect
    ) : IntelligenceEvidence {
        init {
            require(CATEGORY_CODE.matches(categoryCode))
        }

        private companion object {
            val CATEGORY_CODE = Regex("[A-Z0-9_]{1,32}")
        }
    }

    data class Reputation(
        val riskScore: Int?,
        val communitySignals: Int,
        val categoryCodes: List<String>,
        val sourceLabel: String?,
        val signedPackageVerified: Boolean
    ) : IntelligenceEvidence {
        init {
            require(riskScore == null || riskScore in 0..100)
            require(communitySignals >= 0)
        }
    }

    /**
     * Numbering/regulatory context is an annotation for UI and explanation only. It is never
     * interpreted as a block, silence, identity or fraud decision.
     */
    data class TelecomNumberEnrichment(
        val resolutionStatus: String,
        val callingCode: String?,
        val jurisdictions: List<String>,
        val sourceIds: List<String>,
        val freshness: TelecomFreshness,
        val authority: TelecomAuthority = TelecomAuthority.ANNOTATION_ONLY
    ) : IntelligenceEvidence {
        init {
            require(resolutionStatus.matches(STATUS_PATTERN))
            require(jurisdictions.size <= 8)
            require(sourceIds.size <= 8)
            require(sourceIds.all { it.matches(SOURCE_PATTERN) })
            require(authority == TelecomAuthority.ANNOTATION_ONLY)
        }

        private companion object {
            val STATUS_PATTERN = Regex("[A-Z_]{2,48}")
            val SOURCE_PATTERN = Regex("[A-Z0-9_-]{2,32}")
        }
    }

    enum class ContactState { KNOWN, UNKNOWN, UNAVAILABLE }
    enum class CallAction { ALLOW, SILENCE, BLOCK }
    enum class NetworkVerification { VERIFIED, FAILED, NOT_VERIFIED, UNKNOWN }
    enum class Direction { INCOMING, OUTGOING, UNKNOWN }
    enum class CommunityModerationState { PENDING, DUPLICATE, ACCEPTED, REJECTED, UNKNOWN }
    enum class CommunityReputationEffect { UNCHANGED, PENDING, UPDATED, UNKNOWN }
    enum class TelecomFreshness { FRESH, STALE, UNKNOWN }
    enum class TelecomAuthority { ANNOTATION_ONLY }
}

package com.sentinel.quantum.intelligence

enum class EvidenceSource {
    ANDROID_CONTACT_PROVIDER,
    CALL_SCREENING,
    SMS,
    MMS,
    COMMUNITY_REPORT,
    REPUTATION_LOCAL,
    REPUTATION_REMOTE
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
        REPUTATION_CORE
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

    enum class ContactState { KNOWN, UNKNOWN, UNAVAILABLE }
    enum class CallAction { ALLOW, SILENCE, BLOCK }
    enum class NetworkVerification { VERIFIED, FAILED, NOT_VERIFIED, UNKNOWN }
    enum class Direction { INCOMING, OUTGOING, UNKNOWN }
    enum class CommunityModerationState { PENDING, DUPLICATE, ACCEPTED, REJECTED, UNKNOWN }
    enum class CommunityReputationEffect { UNCHANGED, PENDING, UPDATED, UNKNOWN }
}

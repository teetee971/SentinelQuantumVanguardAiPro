package com.sentinel.quantum.security

/**
 * Hash-reputation contract. UNKNOWN must never be promoted to CLEAN.
 *
 * A real provider may later be backed by a signed local feed or a privacy-safe
 * service. Until such a provider is configured, availability remains false.
 */
interface SentinelThreatReputation {

    enum class Verdict {
        MALICIOUS,
        SUSPICIOUS,
        UNKNOWN
    }

    data class Result(
        val verdict: Verdict,
        val source: String,
        val signatureName: String? = null
    ) {
        init {
            require(source.isNotBlank()) { "Reputation source must not be blank" }
        }
    }

    val isAvailable: Boolean

    fun lookupSha256(sha256: String): Result

    object Unavailable : SentinelThreatReputation {
        override val isAvailable: Boolean = false

        override fun lookupSha256(sha256: String): Result = Result(
            verdict = Verdict.UNKNOWN,
            source = "unavailable"
        )
    }

    class LocalHashSet(
        malicious: Map<String, String>,
        suspicious: Map<String, String> = emptyMap(),
        private val sourceName: String = "local_hash_set"
    ) : SentinelThreatReputation {

        private val maliciousHashes = malicious.mapNotNull { (hash, name) ->
            SentinelSha256.normalize(hash)?.let { it to name }
        }.toMap()

        private val suspiciousHashes = suspicious.mapNotNull { (hash, name) ->
            SentinelSha256.normalize(hash)?.let { it to name }
        }.toMap()

        override val isAvailable: Boolean
            get() = maliciousHashes.isNotEmpty() || suspiciousHashes.isNotEmpty()

        override fun lookupSha256(sha256: String): Result {
            val normalized = SentinelSha256.normalize(sha256)
                ?: return Result(Verdict.UNKNOWN, sourceName)

            maliciousHashes[normalized]?.let { name ->
                return Result(Verdict.MALICIOUS, sourceName, name)
            }
            suspiciousHashes[normalized]?.let { name ->
                return Result(Verdict.SUSPICIOUS, sourceName, name)
            }
            return Result(Verdict.UNKNOWN, sourceName)
        }
    }
}

object SentinelThreatReputationPolicy {

    fun findingFor(
        packageName: String,
        sha256: String,
        result: SentinelThreatReputation.Result,
        observedAtEpochMillis: Long
    ): SentinelMalwareDiagnostic.Finding? {
        require(packageName.isNotBlank()) { "Package name must not be blank" }

        return when (result.verdict) {
            SentinelThreatReputation.Verdict.MALICIOUS ->
                SentinelMalwareDiagnostic.Finding(
                    id = "reputation.malicious.$packageName",
                    verdict = SentinelMalwareDiagnostic.Verdict.MALICIOUS,
                    summary = "Empreinte APK reconnue comme malveillante par une source de réputation configurée.",
                    observedValue = result.signatureName ?: sha256,
                    observedAtEpochMillis = observedAtEpochMillis
                )

            SentinelThreatReputation.Verdict.SUSPICIOUS ->
                SentinelMalwareDiagnostic.Finding(
                    id = "reputation.suspicious.$packageName",
                    verdict = SentinelMalwareDiagnostic.Verdict.SUSPICIOUS,
                    summary = "Empreinte APK signalée comme potentiellement indésirable ou suspecte.",
                    observedValue = result.signatureName ?: sha256,
                    observedAtEpochMillis = observedAtEpochMillis
                )

            SentinelThreatReputation.Verdict.UNKNOWN -> null
        }
    }
}

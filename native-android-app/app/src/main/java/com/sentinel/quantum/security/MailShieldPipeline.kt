package com.sentinel.quantum.security

/**
 * Deterministic, local-first orchestration for Sentinel Mail Shield.
 *
 * This first pipeline deliberately performs no mailbox connection, DNS lookup, IP geolocation,
 * quarantine, deletion, link opening, attachment execution, or remote enrichment.
 */
object MailShieldPipeline {
    enum class Stage { INGEST, HEADER_ANALYSIS, AUTHENTICATION, IOC_EXTRACTION, ATTACHMENTS, CLASSIFICATION }
    enum class Disposition { REVIEW, NORMAL, SUSPICIOUS, QUARANTINE_CANDIDATE }

    data class Result(
        val accepted: Boolean,
        val reason: String?,
        val completedStages: List<Stage>,
        val disposition: Disposition,
        val analysis: EmailSecurityAnalyzer.Analysis
    )

    fun analyze(rawMessage: String, analyzer: EmailSecurityAnalyzer): Result {
        val analysis = analyzer.analyze(rawMessage)
        if (!analysis.accepted) {
            return Result(false, analysis.reason, listOf(Stage.INGEST), Disposition.REVIEW, analysis)
        }
        val disposition = when {
            analysis.riskLevel == EmailSecurityAnalyzer.RiskLevel.HIGH &&
                analysis.attachments.any { it.dangerous } -> Disposition.QUARANTINE_CANDIDATE
            analysis.riskLevel == EmailSecurityAnalyzer.RiskLevel.HIGH -> Disposition.SUSPICIOUS
            analysis.riskLevel == EmailSecurityAnalyzer.RiskLevel.MEDIUM -> Disposition.REVIEW
            else -> Disposition.NORMAL
        }
        return Result(
            true,
            null,
            listOf(Stage.INGEST, Stage.HEADER_ANALYSIS, Stage.AUTHENTICATION, Stage.IOC_EXTRACTION, Stage.ATTACHMENTS, Stage.CLASSIFICATION),
            disposition,
            analysis
        )
    }
}

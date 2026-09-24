package com.sentinel.quantum.security

/**
 * PII-free provenance boundary for Phone Core physical certification.
 *
 * A physical proof is valid only for the same app installation, exact build and explicit
 * certification session that observed it. No hardware, phone, SIM or account identifier belongs
 * in this model.
 */
object PhoneCoreCertificationProvenance {
    data class Scope(
        val installationId: String,
        val versionCode: Long,
        val versionName: String,
        val lastUpdateTimeMs: Long,
        val sessionId: String
    )

    fun normalize(scope: Scope): Scope? {
        val installationId = token(scope.installationId, 64) ?: return null
        val versionName = token(scope.versionName, 64) ?: return null
        val sessionId = token(scope.sessionId, 64) ?: return null
        if (scope.versionCode <= 0L || scope.lastUpdateTimeMs < 0L) return null
        return scope.copy(
            installationId = installationId,
            versionName = versionName,
            sessionId = sessionId
        )
    }

    fun belongsTo(proof: Scope?, active: Scope): Boolean {
        val expected = normalize(active) ?: return false
        val candidate = proof?.let(::normalize) ?: return false
        return candidate == expected
    }

    private fun token(value: String, maxLength: Int): String? {
        val clean = value.trim()
        if (clean.isEmpty() || clean.length > maxLength) return null
        if (clean.any { !(it.isLetterOrDigit() || it == '.' || it == '_' || it == '-') }) return null
        return clean
    }
}

package com.sentinel.quantum.security

/**
 * Immutable comparison selection. Starting a replacement invalidates the old selection
 * and every comparison immediately, even if the next APK cannot be read.
 * Pure Kotlin: testable on JVM without Android or an emulator.
 */
internal data class ApkSelectionState(
    val reference: ApkEvidence? = null,
    val candidate: ApkEvidence? = null,
    val comparison: ApkComparison? = null,
) {
    fun startReferenceReplacement(): ApkSelectionState = copy(reference = null, comparison = null)
    fun startCandidateReplacement(): ApkSelectionState = copy(candidate = null, comparison = null)
    fun referenceLoaded(value: ApkEvidence): ApkSelectionState = copy(reference = value, comparison = null)
    fun candidateLoaded(value: ApkEvidence): ApkSelectionState = copy(candidate = value, comparison = null)
    fun withComparison(value: ApkComparison): ApkSelectionState {
        check(reference != null && candidate != null) { "Deux APK sont nécessaires" }
        return copy(comparison = value)
    }
}

package com.sentinel.quantum.security

/**
 * Pure UI operation state. A completed, failed or cancelled inspection must
 * always release its loading indicator. Distinguishes analysis failures from cancellation.
 */
internal data class ApkInspectionActivityState(
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun started() = ApkInspectionActivityState(loading = true)
    fun completed() = ApkInspectionActivityState()
    fun failed(reason: String) = ApkInspectionActivityState(error = reason)
    fun cancelled() = ApkInspectionActivityState()
}

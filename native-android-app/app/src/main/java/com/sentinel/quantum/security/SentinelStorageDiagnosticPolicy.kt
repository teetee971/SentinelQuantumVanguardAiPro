package com.sentinel.quantum.security

object SentinelStorageDiagnosticPolicy {
    private const val WARNING_PERCENT = 10.0
    private const val CRITICAL_PERCENT = 5.0
    private const val WARNING_BYTES = 5L * 1024 * 1024 * 1024
    private const val CRITICAL_BYTES = 1L * 1024 * 1024 * 1024

    fun evaluate(snapshot: SentinelStorageSnapshot): SentinelDeviceDiagnostic.Evidence {
        val total = snapshot.totalBytes
        val available = snapshot.availableBytes
        if (total == null || available == null || total <= 0L || available < 0L || available > total) {
            return SentinelDeviceDiagnostic.Evidence(
                id = "storage.primary_free",
                status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                summary = "Espace de stockage principal non mesurable de façon fiable.",
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            )
        }

        val freePercent = available.toDouble() * 100.0 / total.toDouble()
        val status = when {
            available <= CRITICAL_BYTES || freePercent <= CRITICAL_PERCENT ->
                SentinelDeviceDiagnostic.Status.CRITICAL
            available <= WARNING_BYTES || freePercent <= WARNING_PERCENT ->
                SentinelDeviceDiagnostic.Status.WARNING
            else -> SentinelDeviceDiagnostic.Status.OK
        }
        val summary = when (status) {
            SentinelDeviceDiagnostic.Status.CRITICAL ->
                "Stockage presque saturé ; libérer de l’espace avant les opérations volumineuses."
            SentinelDeviceDiagnostic.Status.WARNING ->
                "Espace de stockage faible ; un nettoyage guidé peut être utile."
            else -> "Espace de stockage disponible suffisant selon la politique Sentinel."
        }
        return SentinelDeviceDiagnostic.Evidence(
            id = "storage.primary_free",
            status = status,
            summary = summary,
            observedValue = "$available/$total",
            observedAtEpochMillis = snapshot.observedAtEpochMillis
        )
    }
}

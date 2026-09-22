package com.sentinel.quantum.security

/**
 * Local deterministic classification of already-observed service metadata.
 *
 * The analyzer does not probe ports, open sockets, fingerprint software versions,
 * query CVEs or claim that an observed service is vulnerable. It only explains which
 * local service surfaces deserve user review.
 */
enum class NetworkServiceScope {
    LOOPBACK,
    LOCAL_LAN,
    UNKNOWN
}

enum class NetworkServiceTransport {
    TCP,
    UDP,
    OTHER
}

enum class NetworkServiceExposureKind {
    PLAINTEXT_PROTOCOL,
    REMOTE_ADMIN_SURFACE,
    FILE_SHARING_SURFACE,
    DATABASE_SURFACE,
    DISCOVERY_SURFACE
}

enum class NetworkServiceExposureSeverity {
    INFO,
    REVIEW,
    HIGH
}

data class ObservedNetworkService(
    val subjectFingerprint: String,
    val port: Int,
    val transport: NetworkServiceTransport,
    val scope: NetworkServiceScope,
    val serviceHint: String? = null
)

data class NetworkServiceExposureFinding(
    val kind: NetworkServiceExposureKind,
    val severity: NetworkServiceExposureSeverity,
    val subjectFingerprint: String,
    val port: Int,
    val transport: NetworkServiceTransport,
    val serviceHint: String?,
    val explanation: String
)

data class NetworkServiceExposureAssessment(
    val acceptedServices: Int,
    val rejectedServices: Int,
    val findings: List<NetworkServiceExposureFinding>,
    val highCount: Int,
    val reviewCount: Int
)

object NetworkServiceExposureAnalyzer {
    private const val MAX_SERVICES = 20_000
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val MAX_HINT_LENGTH = 64

    fun assess(services: List<ObservedNetworkService>): NetworkServiceExposureAssessment {
        val bounded = services.take(MAX_SERVICES)
        var rejected = services.size - bounded.size
        val findings = mutableListOf<NetworkServiceExposureFinding>()
        var accepted = 0

        bounded.forEach { raw ->
            val service = normalize(raw)
            if (service == null) {
                rejected += 1
                return@forEach
            }
            accepted += 1

            if (service.scope == NetworkServiceScope.LOOPBACK) {
                return@forEach
            }

            findings += classify(service)
        }

        val ordered = findings
            .distinctBy {
                listOf(
                    it.kind.name,
                    it.subjectFingerprint,
                    it.port.toString(),
                    it.transport.name
                ).joinToString("|")
            }
            .sortedWith(
                compareByDescending<NetworkServiceExposureFinding> { severityWeight(it.severity) }
                    .thenBy { it.subjectFingerprint }
                    .thenBy { it.port }
                    .thenBy { it.kind.name }
            )

        return NetworkServiceExposureAssessment(
            acceptedServices = accepted,
            rejectedServices = rejected,
            findings = ordered,
            highCount = ordered.count { it.severity == NetworkServiceExposureSeverity.HIGH },
            reviewCount = ordered.count { it.severity == NetworkServiceExposureSeverity.REVIEW }
        )
    }

    private fun classify(service: ObservedNetworkService): List<NetworkServiceExposureFinding> {
        val findings = mutableListOf<NetworkServiceExposureFinding>()
        val hint = service.serviceHint.orEmpty().lowercase()

        if (isPlaintextProtocol(service.port, hint)) {
            val high = service.port == 23 || hint.contains("telnet")
            findings += finding(
                service = service,
                kind = NetworkServiceExposureKind.PLAINTEXT_PROTOCOL,
                severity = if (high) NetworkServiceExposureSeverity.HIGH else NetworkServiceExposureSeverity.REVIEW,
                explanation = if (high) {
                    "Service Telnet observé : protocole d’administration historiquement non chiffré. Vérifier s’il est réellement nécessaire sur ce réseau."
                } else {
                    "Service généralement non chiffré observé. Vérifier que des données sensibles ne transitent pas en clair et qu’une alternative chiffrée est disponible."
                }
            )
        }

        if (isRemoteAdminSurface(service.port, hint)) {
            findings += finding(
                service = service,
                kind = NetworkServiceExposureKind.REMOTE_ADMIN_SURFACE,
                severity = NetworkServiceExposureSeverity.REVIEW,
                explanation = "Surface d’administration distante observée. Sa présence peut être légitime ; vérifier qu’elle est attendue, authentifiée et limitée au périmètre prévu."
            )
        }

        if (isFileSharingSurface(service.port, hint)) {
            findings += finding(
                service = service,
                kind = NetworkServiceExposureKind.FILE_SHARING_SURFACE,
                severity = NetworkServiceExposureSeverity.REVIEW,
                explanation = "Service de partage de fichiers observé. Vérifier les droits d’accès, les partages anonymes et la segmentation du réseau."
            )
        }

        if (isDatabaseSurface(service.port, hint)) {
            findings += finding(
                service = service,
                kind = NetworkServiceExposureKind.DATABASE_SURFACE,
                severity = NetworkServiceExposureSeverity.REVIEW,
                explanation = "Service de base de données observé sur le réseau local. Vérifier qu’il doit être joignable depuis ce segment et qu’une authentification appropriée est activée."
            )
        }

        if (isDiscoverySurface(service.port, hint)) {
            findings += finding(
                service = service,
                kind = NetworkServiceExposureKind.DISCOVERY_SURFACE,
                severity = NetworkServiceExposureSeverity.INFO,
                explanation = "Service de découverte locale observé. Ce comportement est fréquent sur les réseaux domestiques et IoT ; conserver la provenance pour expliquer la topologie."
            )
        }

        return findings
    }

    private fun finding(
        service: ObservedNetworkService,
        kind: NetworkServiceExposureKind,
        severity: NetworkServiceExposureSeverity,
        explanation: String
    ) = NetworkServiceExposureFinding(
        kind = kind,
        severity = severity,
        subjectFingerprint = service.subjectFingerprint,
        port = service.port,
        transport = service.transport,
        serviceHint = service.serviceHint,
        explanation = explanation
    )

    private fun isPlaintextProtocol(port: Int, hint: String): Boolean =
        port in setOf(21, 23, 80, 110, 143) ||
            listOf("ftp", "telnet", "http", "pop3", "imap").any { hint == it }

    private fun isRemoteAdminSurface(port: Int, hint: String): Boolean =
        port in setOf(22, 23, 3389, 5900, 5985, 5986) ||
            listOf("ssh", "telnet", "rdp", "vnc", "winrm").any { hint.contains(it) }

    private fun isFileSharingSurface(port: Int, hint: String): Boolean =
        port in setOf(139, 445, 2049) ||
            listOf("smb", "cifs", "nfs").any { hint.contains(it) }

    private fun isDatabaseSurface(port: Int, hint: String): Boolean =
        port in setOf(1433, 1521, 3306, 5432, 6379, 27017) ||
            listOf("mssql", "oracle", "mysql", "postgres", "redis", "mongodb").any { hint.contains(it) }

    private fun isDiscoverySurface(port: Int, hint: String): Boolean =
        port in setOf(1900, 5353) ||
            listOf("ssdp", "mdns", "bonjour").any { hint.contains(it) }

    private fun normalize(raw: ObservedNetworkService): ObservedNetworkService? {
        val fingerprint = raw.subjectFingerprint.trim().lowercase()
        if (fingerprint.length != FINGERPRINT_HEX_LENGTH) return null
        if (!fingerprint.all { it in '0'..'9' || it in 'a'..'f' }) return null
        if (raw.port !in 1..65535) return null

        val hint = raw.serviceHint
            ?.trim()
            ?.lowercase()
            ?.take(MAX_HINT_LENGTH)
            ?.takeIf { it.isNotEmpty() }

        return raw.copy(
            subjectFingerprint = fingerprint,
            serviceHint = hint
        )
    }

    private fun severityWeight(severity: NetworkServiceExposureSeverity): Int = when (severity) {
        NetworkServiceExposureSeverity.HIGH -> 3
        NetworkServiceExposureSeverity.REVIEW -> 2
        NetworkServiceExposureSeverity.INFO -> 1
    }
}

package com.sentinel.quantum.security

enum class NetworkServiceScope { LOOPBACK, LOCAL_LAN, UNKNOWN }
enum class NetworkServiceTransport { TCP, UDP, OTHER }
enum class NetworkServiceExposureKind { PLAINTEXT_PROTOCOL, REMOTE_ADMIN_SURFACE, FILE_SHARING_SURFACE, DATABASE_SURFACE, DISCOVERY_SURFACE }
enum class NetworkServiceExposureSeverity { INFO, REVIEW, HIGH }

data class ObservedNetworkService(val subjectFingerprint:String,val port:Int,val transport:NetworkServiceTransport,val scope:NetworkServiceScope,val serviceHint:String?=null)
data class NetworkServiceExposureFinding(val kind:NetworkServiceExposureKind,val severity:NetworkServiceExposureSeverity,val subjectFingerprint:String,val port:Int,val transport:NetworkServiceTransport,val serviceHint:String?,val explanation:String)
data class NetworkServiceExposureAssessment(val acceptedServices:Int,val rejectedServices:Int,val findings:List<NetworkServiceExposureFinding>,val highCount:Int,val reviewCount:Int)

/** Local deterministic classifier for already-observed service metadata. It never probes or opens sockets. */
object NetworkServiceExposureAnalyzer {
    private const val MAX_SERVICES=20_000
    private const val FP_LEN=64
    private const val MAX_HINT=64

    fun assess(services:List<ObservedNetworkService>):NetworkServiceExposureAssessment {
        val bounded=services.take(MAX_SERVICES); var rejected=services.size-bounded.size; var accepted=0
        val findings=mutableListOf<NetworkServiceExposureFinding>()
        bounded.forEach { raw ->
            val s=normalize(raw) ?: run { rejected++; return@forEach }
            accepted++
            if(s.scope!=NetworkServiceScope.LOOPBACK) findings += classify(s)
        }
        val ordered=findings.distinctBy { "${it.kind}|${it.subjectFingerprint}|${it.port}|${it.transport}" }
            .sortedWith(compareByDescending<NetworkServiceExposureFinding>{ weight(it.severity) }.thenBy{it.subjectFingerprint}.thenBy{it.port}.thenBy{it.kind.name})
        return NetworkServiceExposureAssessment(accepted,rejected,ordered,ordered.count{it.severity==NetworkServiceExposureSeverity.HIGH},ordered.count{it.severity==NetworkServiceExposureSeverity.REVIEW})
    }

    private fun classify(s:ObservedNetworkService):List<NetworkServiceExposureFinding> {
        val out=mutableListOf<NetworkServiceExposureFinding>(); val h=s.serviceHint.orEmpty().lowercase()
        if(s.port in setOf(21,23,80,110,143) || listOf("ftp","telnet","http","pop3","imap").any{h==it}) {
            val high=s.port==23 || h.contains("telnet")
            out += finding(s,NetworkServiceExposureKind.PLAINTEXT_PROTOCOL,if(high) NetworkServiceExposureSeverity.HIGH else NetworkServiceExposureSeverity.REVIEW,if(high) "Service Telnet observé : protocole d’administration historiquement non chiffré. Vérifier s’il est réellement nécessaire sur ce réseau." else "Service généralement non chiffré observé. Vérifier que des données sensibles ne transitent pas en clair et qu’une alternative chiffrée est disponible.")
        }
        if(s.port in setOf(22,23,3389,5900,5985,5986) || listOf("ssh","telnet","rdp","vnc","winrm").any{h.contains(it)}) out += finding(s,NetworkServiceExposureKind.REMOTE_ADMIN_SURFACE,NetworkServiceExposureSeverity.REVIEW,"Surface d’administration distante observée. Sa présence peut être légitime ; vérifier qu’elle est attendue, authentifiée et limitée au périmètre prévu.")
        if(s.port in setOf(139,445,2049) || listOf("smb","cifs","nfs").any{h.contains(it)}) out += finding(s,NetworkServiceExposureKind.FILE_SHARING_SURFACE,NetworkServiceExposureSeverity.REVIEW,"Service de partage de fichiers observé. Vérifier les droits d’accès, les partages anonymes et la segmentation du réseau.")
        if(s.port in setOf(1433,1521,3306,5432,6379,27017) || listOf("mssql","oracle","mysql","postgres","redis","mongodb").any{h.contains(it)}) out += finding(s,NetworkServiceExposureKind.DATABASE_SURFACE,NetworkServiceExposureSeverity.REVIEW,"Service de base de données observé sur le réseau local. Vérifier qu’il doit être joignable depuis ce segment et qu’une authentification appropriée est activée.")
        if(s.port in setOf(1900,5353) || listOf("ssdp","mdns","bonjour").any{h.contains(it)}) out += finding(s,NetworkServiceExposureKind.DISCOVERY_SURFACE,NetworkServiceExposureSeverity.INFO,"Service de découverte locale observé. Ce comportement est fréquent sur les réseaux domestiques et IoT ; conserver la provenance pour expliquer la topologie.")
        return out
    }
    private fun finding(s:ObservedNetworkService,k:NetworkServiceExposureKind,v:NetworkServiceExposureSeverity,e:String)=NetworkServiceExposureFinding(k,v,s.subjectFingerprint,s.port,s.transport,s.serviceHint,e)
    private fun normalize(raw:ObservedNetworkService):ObservedNetworkService? {
        val fp=raw.subjectFingerprint.trim().lowercase(); if(fp.length!=FP_LEN || !fp.all{it in '0'..'9'||it in 'a'..'f'} || raw.port !in 1..65535) return null
        val hint=raw.serviceHint?.trim()?.lowercase()?.take(MAX_HINT)?.takeIf{it.isNotEmpty()}
        return raw.copy(subjectFingerprint=fp,serviceHint=hint)
    }
    private fun weight(v:NetworkServiceExposureSeverity)=when(v){NetworkServiceExposureSeverity.HIGH->3;NetworkServiceExposureSeverity.REVIEW->2;NetworkServiceExposureSeverity.INFO->1}
}

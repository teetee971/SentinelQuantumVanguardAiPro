package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkServiceExposureAnalyzerTest {
    private fun service(port:Int,transport:NetworkServiceTransport=NetworkServiceTransport.TCP,scope:NetworkServiceScope=NetworkServiceScope.LOCAL_LAN,hint:String?=null)=ObservedNetworkService("a".repeat(64),port,transport,scope,hint)

    @Test fun telnetIsHighAndRemoteAdmin(){ val r=NetworkServiceExposureAnalyzer.assess(listOf(service(23,hint="Telnet"))); assertEquals(1,r.highCount); assertTrue(r.findings.any{it.kind==NetworkServiceExposureKind.REMOTE_ADMIN_SURFACE}) }
    @Test fun sshIsReviewOnly(){ val r=NetworkServiceExposureAnalyzer.assess(listOf(service(22,hint="SSH"))); assertEquals(0,r.highCount); assertEquals(setOf(NetworkServiceExposureKind.REMOTE_ADMIN_SURFACE),r.findings.map{it.kind}.toSet()) }
    @Test fun databaseIsReview(){ val f=NetworkServiceExposureAnalyzer.assess(listOf(service(5432,hint="PostgreSQL"))).findings.single(); assertEquals(NetworkServiceExposureKind.DATABASE_SURFACE,f.kind); assertEquals(NetworkServiceExposureSeverity.REVIEW,f.severity) }
    @Test fun discoveryIsInfo(){ val f=NetworkServiceExposureAnalyzer.assess(listOf(service(5353,NetworkServiceTransport.UDP,hint="mDNS"))).findings.single(); assertEquals(NetworkServiceExposureSeverity.INFO,f.severity) }
    @Test fun loopbackCreatesNoFinding(){ val r=NetworkServiceExposureAnalyzer.assess(listOf(service(23,scope=NetworkServiceScope.LOOPBACK,hint="telnet"))); assertTrue(r.findings.isEmpty()) }
    @Test fun malformedInputsRejected(){ val r=NetworkServiceExposureAnalyzer.assess(listOf(ObservedNetworkService("AA:BB:CC:DD:EE:FF",443,NetworkServiceTransport.TCP,NetworkServiceScope.LOCAL_LAN),service(0))); assertEquals(0,r.acceptedServices); assertEquals(2,r.rejectedServices) }
    @Test fun hintIsNormalizedAndBounded(){ val f=NetworkServiceExposureAnalyzer.assess(listOf(service(22,hint="  SSH   "+"x".repeat(100)))).findings.single(); assertEquals(64,f.serviceHint!!.length); assertTrue(f.serviceHint!!.startsWith("ssh")) }
    @Test fun sameFindingDeduplicated(){ val s=service(445,hint="smb"); val r=NetworkServiceExposureAnalyzer.assess(listOf(s,s)); assertEquals(2,r.acceptedServices); assertEquals(1,r.findings.size) }
}

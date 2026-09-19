package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MailRelayProvenanceAnalyzerTest {
 @Test fun extractsPublicRelayAsIndicativeInfrastructure(){
  val r=MailRelayProvenanceAnalyzer.analyze(listOf("from mail.example (mail.example [8.8.8.8]) by mx.local"))
  assertEquals("mail.example",r.hops.single().fromHost);assertEquals("mx.local",r.hops.single().byHost)
  assertEquals(listOf("8.8.8.8"),r.hops.single().observablePublicIps)
  assertEquals(MailRelayProvenanceAnalyzer.Confidence.INDICATIVE,r.hops.single().confidence)
 }
 @Test fun filtersPrivateAndDocumentationIpv4(){
  val r=MailRelayProvenanceAnalyzer.analyze(listOf("from x [10.0.0.1] by y [192.0.2.10]"))
  assertTrue(r.hops.single().observablePublicIps.isEmpty())
 }
 @Test fun acceptsPublicIpv6AndRejectsDocumentationIpv6(){
  val r=MailRelayProvenanceAnalyzer.analyze(listOf("from x [2606:4700:4700::1111] by y [2001:db8::1]"))
  assertEquals(1,r.hops.single().observablePublicIps.size)
 }
 @Test fun boundsUntrustedChain(){
  val r=MailRelayProvenanceAnalyzer.analyze((0 until 25).map{"from h$it [8.8.8.8] by mx"})
  assertEquals(EmailHeaderAnalyzer.MAX_RECEIVED_HOPS,r.hops.size);assertTrue(r.truncated)
 }
}

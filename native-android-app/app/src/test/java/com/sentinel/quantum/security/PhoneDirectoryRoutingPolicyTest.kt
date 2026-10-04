package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneDirectoryRoutingPolicyTest {
    @Test fun routesOnlyExplicitCanonicalCountryIdentities() {
        assertEquals(PhoneDirectoryRoutingPolicy.Target.RTR, PhoneDirectoryRoutingPolicy.targetFor("+431234567"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.ARCEP, PhoneDirectoryRoutingPolicy.targetFor("+33142123456"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.ARCEP, PhoneDirectoryRoutingPolicy.targetFor("+590690123456"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.ARCEP, PhoneDirectoryRoutingPolicy.targetFor("+594694123456"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.ARCEP, PhoneDirectoryRoutingPolicy.targetFor("+596696123456"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.ARCEP, PhoneDirectoryRoutingPolicy.targetFor("+262692123456"))
    }

    @Test fun rejectsNationalMalformedAndUnsupportedInternationalNumbers() {
        assertEquals(PhoneDirectoryRoutingPolicy.Target.NONE, PhoneDirectoryRoutingPolicy.targetFor("0612345678"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.NONE, PhoneDirectoryRoutingPolicy.targetFor("+01234567"))
        assertEquals(PhoneDirectoryRoutingPolicy.Target.NONE, PhoneDirectoryRoutingPolicy.targetFor("+14155550132"))
    }
}

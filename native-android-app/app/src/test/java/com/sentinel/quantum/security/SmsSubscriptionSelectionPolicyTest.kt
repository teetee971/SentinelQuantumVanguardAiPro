package com.sentinel.quantum.security
import org.junit.Assert.*
import org.junit.Test
class SmsSubscriptionSelectionPolicyTest {
 @Test fun requiresChoiceWhenSeveralLinesHaveNoDefault(){val r=SmsSubscriptionSelectionPolicy.select(setOf(1,2),null,null);assertFalse(r.accepted);assertEquals("USER_SELECTION_REQUIRED",r.reason)}
 @Test fun requiresChoiceWhenSeveralLinesHaveAndroidDefault(){val r=SmsSubscriptionSelectionPolicy.select(setOf(1,2),null,1);assertFalse(r.accepted);assertNull(r.subscriptionId);assertEquals("USER_SELECTION_REQUIRED",r.reason)}
 @Test fun acceptsExplicitActiveLine(){val r=SmsSubscriptionSelectionPolicy.select(setOf(1,2),2,1);assertTrue(r.accepted);assertEquals(2,r.subscriptionId)}
 @Test fun rejectsStaleRequestedLine(){val r=SmsSubscriptionSelectionPolicy.select(setOf(1),2,1);assertFalse(r.accepted)}
 @Test fun usesSingleActiveLine(){val r=SmsSubscriptionSelectionPolicy.select(setOf(7),null,null);assertTrue(r.accepted);assertEquals(7,r.subscriptionId)}
}

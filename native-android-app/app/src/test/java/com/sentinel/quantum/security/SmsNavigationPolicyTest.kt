package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsNavigationPolicyTest {
    @Test fun backFromAConversationReturnsToTheList() {
        assertEquals(SmsNavigationPolicy.BackAction.CONVERSATIONS,
            SmsNavigationPolicy.backAction(false, true, false))
    }
    @Test fun backFromTheRootListFinishesTheActivity() {
        assertEquals(SmsNavigationPolicy.BackAction.FINISH,
            SmsNavigationPolicy.backAction(false, false, true))
    }
    @Test fun composerEnteredFromTheListReturnsToTheList() {
        assertEquals(SmsNavigationPolicy.BackAction.CONVERSATIONS,
            SmsNavigationPolicy.backAction(true, false, true))
    }
    @Test fun externalComposerReturnsToItsCallerEvenIfAnOldThreadWasSelected() {
        assertEquals(SmsNavigationPolicy.BackAction.FINISH,
            SmsNavigationPolicy.backAction(true, false, false))
        assertEquals(SmsNavigationPolicy.BackAction.FINISH,
            SmsNavigationPolicy.backAction(true, true, false))
    }
}

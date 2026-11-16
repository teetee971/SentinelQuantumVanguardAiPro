package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsSubscriptionStateTest {
    @Test
    fun keepsExplicitSelectionWhileSimRemainsActive() {
        assertEquals(22, SmsSubscriptionState.reconcileSelection(22, listOf(11, 22)))
    }

    @Test
    fun clearsStaleSelectionWhenMultipleSimsRemain() {
        assertNull(SmsSubscriptionState.reconcileSelection(33, listOf(11, 22)))
    }

    @Test
    fun selectsOnlyActiveSimWhenChoiceIsUnambiguous() {
        assertEquals(11, SmsSubscriptionState.reconcileSelection(null, listOf(11)))
        assertEquals(11, SmsSubscriptionState.reconcileSelection(33, listOf(11)))
    }

    @Test
    fun clearsSelectionWhenNoSimIsActive() {
        assertNull(SmsSubscriptionState.reconcileSelection(11, emptyList()))
    }
}

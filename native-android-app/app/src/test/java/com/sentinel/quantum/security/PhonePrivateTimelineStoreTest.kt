package com.sentinel.quantum.security

import org.junit.Assert.assertNull
import org.junit.Test

class PhonePrivateTimelineStoreTest {
    @Test
    fun malformedPersistedTimelineIsRejectedInsteadOfBecomingEmptyHistory() {
        assertNull(PhonePrivateTimelineStore.decodeStoredEvents("[{not-json}]"))
    }
}

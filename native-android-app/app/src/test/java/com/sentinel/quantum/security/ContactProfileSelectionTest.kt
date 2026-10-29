package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class ContactProfileSelectionTest {
    private fun contact(id: Long, name: String) = LocalContactLookup.Contact(id, name, listOf("0123456789"), true)
    @Test fun refreshReplacesAnOpenProfileWithTheCurrentProviderRecord() {
        val updated = contact(7, "Updated").copy(phoneNumbers = listOf("0987654321"), photoRevision = 2)
        val result = LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.READY, listOf(contact(8, "Other"), updated))
        assertEquals(updated, ContactProfileSelection.reconcile(7, result, true))
    }
    @Test fun deletionClosesProfileInsteadOfSelectingAnotherContact() {
        val result = LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.READY, listOf(contact(8, "Other")))
        assertNull(ContactProfileSelection.reconcile(7, result, true))
    }
    @Test fun revokedPermissionDiscardsEvenAnAlreadyLoadedRecord() {
        val result = LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.READY, listOf(contact(7, "Private")))
        assertNull(ContactProfileSelection.reconcile(7, result, false))
    }
    @Test fun unavailableProviderAndClosedProfileNeverExposeACachedSelection() {
        for (state in listOf(LocalContactLookup.ContactAccessState.PERMISSION_REQUIRED, LocalContactLookup.ContactAccessState.PROVIDER_UNAVAILABLE)) {
            val result = LocalContactLookup.ContactListResult(state, listOf(contact(7, "Stale")))
            assertNull(ContactProfileSelection.reconcile(7, result, true))
        }
        assertNull(ContactProfileSelection.reconcile(null, LocalContactLookup.ContactListResult(LocalContactLookup.ContactAccessState.READY, listOf(contact(7, "Other"))), true))
    }
}

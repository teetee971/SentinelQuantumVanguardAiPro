package com.sentinel.quantum.security

/** Reconcile an open sheet with an authoritative read; never retain a stale actionable record. */
object ContactProfileSelection {
    fun reconcile(
        selectedId: Long?,
        result: LocalContactLookup.ContactListResult,
        permissionGranted: Boolean
    ): LocalContactLookup.Contact? {
        if (!permissionGranted || result.state != LocalContactLookup.ContactAccessState.READY) return null
        return selectedId?.let { id -> result.contacts.firstOrNull { it.contactId == id } }
    }
}

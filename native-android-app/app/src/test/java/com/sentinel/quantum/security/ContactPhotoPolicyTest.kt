package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class ContactPhotoPolicyTest {
    @Test fun loadedPhotoNeverCrossesContactIdentityWhenAListSlotIsReused() {
        val loaded = ContactPhotoSnapshot(12, "content://com.android.contacts/photo/12", "photo pixels")
        assertEquals("photo pixels", loaded.forContact(12, loaded.uri))
        assertNull(loaded.forContact(13, loaded.uri))
    }
    @Test fun changedOrRemovedPhotoHidesThePreviousResultImmediately() {
        val loaded = ContactPhotoSnapshot(12, "content://com.android.contacts/photo/12", "old pixels")
        assertNull(loaded.forContact(12, "content://com.android.contacts/photo/updated"))
        assertNull(loaded.forContact(12, null))
    }
    @Test fun onlyTheLocalAndroidContactsProviderMaySupplyAnAvatar() {
        assertEquals("content://com.android.contacts/contacts/12/photo", ContactPhotoPolicy.localUri("content://com.android.contacts/contacts/12/photo"))
        assertNull(ContactPhotoPolicy.localUri("https://example.invalid/avatar.png"))
        assertNull(ContactPhotoPolicy.localUri("content://untrusted.provider/avatar"))
    }
    @Test fun malformedOrCredentialBearingUrisAreRejected() {
        assertNull(ContactPhotoPolicy.localUri("content://user@com.android.contacts/contacts/12/photo"))
        assertNull(ContactPhotoPolicy.localUri("content://com.android.contacts.evil/photo"))
        assertNull(ContactPhotoPolicy.localUri("x".repeat(3000)))
    }
    @Test fun missingPhotoRemainsMissingRatherThanInventingAPortrait() {
        assertNull(ContactPhotoPolicy.localUri(null))
        assertNull(ContactPhotoPolicy.localUri(""))
    }
}

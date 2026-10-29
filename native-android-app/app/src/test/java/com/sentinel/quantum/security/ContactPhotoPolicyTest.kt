package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class ContactPhotoPolicyTest {
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

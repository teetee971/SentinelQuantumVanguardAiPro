package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class ContactPhotoPolicyTest {
    @Test fun localFullPhotoIsUsedOnlyWhenThumbnailIsMissingOrUnsafe() {
        val thumb = "content://com.android.contacts/contacts/12/photo"
        val full = "content://com.android.contacts/display_photo/12"
        assertEquals(thumb, ContactPhotoPolicy.preferredLocalUri(thumb, full))
        assertEquals(full, ContactPhotoPolicy.preferredLocalUri(null, full))
        assertEquals(full, ContactPhotoPolicy.preferredLocalUri("https://example.invalid/photo", full))
        assertNull(ContactPhotoPolicy.preferredLocalUri(null, "https://example.invalid/photo"))
        assertNull(ContactPhotoPolicy.preferredLocalUri(null, null))
    }
    @Test fun loadedPhotoNeverCrossesContactIdentityWhenAListSlotIsReused() {
        val identity = ContactPhotoIdentity(12, "content://com.android.contacts/photo/12")
        val loaded = ContactPhotoSnapshot(identity, "photo pixels")
        assertEquals("photo pixels", loaded.forIdentity(identity))
        assertNull(loaded.forIdentity(identity.copy(contactId = 13)))
    }
    @Test fun changedOrRemovedPhotoHidesThePreviousResultImmediately() {
        val identity = ContactPhotoIdentity(12, "content://com.android.contacts/photo/12")
        val loaded = ContactPhotoSnapshot(identity, "old pixels")
        assertNull(loaded.forIdentity(identity.copy(uri = "content://com.android.contacts/photo/updated")))
        assertNull(loaded.forIdentity(identity.copy(uri = null)))
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
    @Test fun sameUriWithNewProviderRevisionRejectsTheOldPixels() {
        val original = ContactPhotoIdentity(12, "content://com.android.contacts/photo/12", 100L, 1)
        val loaded = ContactPhotoSnapshot(original, "old pixels")
        assertEquals("old pixels", loaded.forIdentity(original))
        assertNull(loaded.forIdentity(original.copy(revision = 101L)))
    }
    @Test fun returningFromEditorInvalidatesEvenWhenProviderRevisionIsUnavailable() {
        val original = ContactPhotoIdentity(12, "content://com.android.contacts/photo/12", null, 1)
        val loaded = ContactPhotoSnapshot(original, "old pixels")
        assertNull(loaded.forIdentity(original.copy(refreshGeneration = 2)))
    }
}

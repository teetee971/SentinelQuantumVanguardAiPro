package com.sentinel.quantum.security

import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

/** Synthetic local provider, no personal contacts or external searches. */
@SdkSuppress(minSdkVersion = 29)
class ContactPhoneWebGuardInstrumentationTest {
    @Test fun validatesSelectedLookupAndCurrentPhoneAndFailsAfterRevocation() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        var allowed = true
        var revokeOnPhoneQuery = false
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
                return if (uri == ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7)) {
                    MatrixCursor(arrayOf(ContactsContract.Contacts.LOOKUP_KEY)).apply { addRow(arrayOf("fixture-lookup")) }
                } else {
                    assertEquals(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, uri)
                    assertEquals("contact_id=?", selection)
                    assertArrayEquals(arrayOf("7"), args)
                    if (revokeOnPhoneQuery) allowed = false
                    MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)).apply { addRow(arrayOf("+1 (202) 555-0123")) }
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes")
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No writes")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("No writes")
        }
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(base) {
            override fun getContentResolver() = resolver
            override fun checkPermission(permission: String, pid: Int, uid: Int) = if (allowed) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        val guard = LocalContactPhoneWebGuard(context)
        assertTrue(guard.isCurrent(7, "fixture-lookup", "+12025550123"))
        assertFalse(guard.isCurrent(7, "other-owner", "+12025550123"))
        assertFalse(guard.isCurrent(7, "fixture-lookup", "+12025550124"))
        assertFalse(guard.isCurrent(7, null, "+12025550123"))
        revokeOnPhoneQuery = true
        assertFalse(guard.isCurrent(7, "fixture-lookup", "+12025550123"))
        assertFalse(guard.hasPermission())
    }
}

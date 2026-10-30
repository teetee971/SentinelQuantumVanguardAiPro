package com.sentinel.quantum.security

import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

/** Exercises actual cursor decoding against a synthetic provider, never personal contacts. */
@SdkSuppress(minSdkVersion = 29)
class LocalContactProfileReaderInstrumentationTest {
    @Test fun selectedContactQueryMapsStructuredAndExtendedLocalFields() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        var reads = 0
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
                reads++
                assertEquals(ContactsContract.Data.CONTENT_URI, uri)
                assertTrue(selection!!.startsWith("contact_id=? AND mimetype IN"))
                assertEquals("7", args!![0])
                assertEquals(13, args.size)
                val columns = projection!!
                val cursor = MatrixCursor(columns)
                fun row(mime: String, values: Map<Int, String>) {
                    cursor.addRow(columns.map { col -> if (col == "mimetype") mime else values[col.removePrefix("data").toInt()] }.toTypedArray())
                }
                row(StructuredName.CONTENT_ITEM_TYPE, mapOf(1 to "Fixture Name", 2 to "Fixture", 3 to "Name", 7 to "Phonetic"))
                row(Organization.CONTENT_ITEM_TYPE, mapOf(1 to "Company", 4 to "Role", 5 to "Department", 6 to "Description", 7 to "Symbol", 8 to "Phonetic Org", 9 to "Office"))
                row(StructuredPostal.CONTENT_ITEM_TYPE, mapOf(4 to "Street", 7 to "City", 8 to "Region", 9 to "Postal", 10 to "Country"))
                row(Phone.CONTENT_ITEM_TYPE, mapOf(1 to "0123456789", 2 to "2"))
                row(Email.CONTENT_ITEM_TYPE, mapOf(1 to "fixture@example.invalid", 2 to "0", 3 to "Custom label"))
                row(Event.CONTENT_ITEM_TYPE, mapOf(1 to "--04-12", 2 to "3"))
                row(Note.CONTENT_ITEM_TYPE, mapOf(1 to "Private synthetic note"))
                row(Website.CONTENT_ITEM_TYPE, mapOf(1 to "https://example.invalid", 2 to "4"))
                row(Nickname.CONTENT_ITEM_TYPE, mapOf(1 to "Other fixture name"))
                row(Relation.CONTENT_ITEM_TYPE, mapOf(1 to "Fixture relation"))
                row(Im.CONTENT_ITEM_TYPE, mapOf(1 to "Fixture handle", 5 to "-1", 6 to "Fixture IM"))
                row(SipAddress.CONTENT_ITEM_TYPE, mapOf(1 to "fixture@example.invalid"))
                return cursor
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes permitted")
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No writes permitted")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("No writes permitted")
        }
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(base) {
            override fun getContentResolver() = resolver
            override fun checkPermission(permission: String, pid: Int, uid: Int) = PackageManager.PERMISSION_GRANTED
        }
        val fields = LocalContactProfileReader(context).read(7)!!
        assertEquals(1, reads)
        assertEquals("Nom affiché : Fixture Name\nPrénom : Fixture\nNom : Name\nPrénom phonétique : Phonetic", fields.single { it.kind == ContactProfileKind.NAME }.value)
        assertEquals("Entreprise : Company\nFonction : Role\nService : Department\nDescription du poste : Description\nSymbole : Symbol\nNom phonétique : Phonetic Org\nBureau : Office", fields.single { it.kind == ContactProfileKind.ORGANISATION }.value)
        assertEquals("Street\nCity\nRegion\nPostal\nCountry", fields.single { it.kind == ContactProfileKind.ADDRESS }.value)
        assertEquals("Custom label", fields.single { it.kind == ContactProfileKind.EMAIL }.label)
        assertEquals("--04-12", fields.single { it.kind == ContactProfileKind.EVENT }.value)
        assertEquals("Fixture IM", fields.single { it.kind == ContactProfileKind.IM }.label)
        assertEquals(ContactProfileKind.entries.toSet(), fields.map { it.kind }.toSet())
    }
}

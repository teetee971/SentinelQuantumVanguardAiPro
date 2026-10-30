package com.sentinel.quantum.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sentinel.quantum.ConversationNumberIntents
import com.sentinel.quantum.SentinelDialerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Inspects intents only: no Activity launch, call, contact save or provider write. */
@RunWith(AndroidJUnit4::class)
class ConversationNumberIntentsInstrumentationTest {
    @Test fun phoneActionOnlyPrefillsTheExplicitSentinelDialer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = requireNotNull(ConversationNumberIntents.dial(context, "+33 6 12 34 56 78"))
        assertEquals(Intent.ACTION_DIAL, request.action)
        assertEquals(SentinelDialerActivity::class.java.name, request.component?.className)
        assertEquals(context.packageName, request.component?.packageName)
        assertEquals("tel", request.data?.scheme)
        assertEquals("+33612345678", request.data?.schemeSpecificPart)
    }
    @Test fun contactActionDelegatesAnEditablePhoneFieldToAndroid() {
        val request = requireNotNull(ConversationNumberIntents.contact("06 12 34 56 78"))
        assertEquals(Intent.ACTION_INSERT_OR_EDIT, request.action)
        assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, request.type)
        assertEquals("0612345678", request.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        assertNull(request.component)
    }
    @Test fun senderLabelsAndServiceCodesDoNotCreateAnyAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull(ConversationNumberIntents.dial(context, "BANQUE"))
        assertNull(ConversationNumberIntents.contact("*123#"))
    }
}

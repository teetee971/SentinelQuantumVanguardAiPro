package com.sentinel.quantum

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.sentinel.quantum.security.ConversationNumberPolicy

/** Prefill only: never ACTION_CALL, a contacts-provider write or an automatic save. */
internal object ConversationNumberIntents {
    fun dial(context: Context, rawNumber: String?): Intent? =
        ConversationNumberPolicy.number(rawNumber)?.let { number ->
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                .setClass(context, SentinelDialerActivity::class.java)
        }

    fun contact(rawNumber: String?): Intent? =
        ConversationNumberPolicy.number(rawNumber)?.let { number ->
            Intent(Intent.ACTION_INSERT_OR_EDIT)
                .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
}

package com.sentinel.quantum.security

import android.provider.Telephony

/** Pure interpretation of Android SMS provider TYPE + STATUS for durable UI state. */
object SmsProviderMessageState {
    enum class State {
        RECEIVED,
        SENT,
        DELIVERED,
        DELIVERY_PENDING,
        DELIVERY_FAILED,
        SENDING,
        SEND_FAILED,
        DRAFT,
        OTHER
    }

    fun classify(type: Int, status: Int): State = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> State.RECEIVED
        Telephony.Sms.MESSAGE_TYPE_FAILED -> State.SEND_FAILED
        Telephony.Sms.MESSAGE_TYPE_OUTBOX,
        Telephony.Sms.MESSAGE_TYPE_QUEUED -> State.SENDING
        Telephony.Sms.MESSAGE_TYPE_DRAFT -> State.DRAFT
        Telephony.Sms.MESSAGE_TYPE_SENT -> when (status) {
            Telephony.Sms.STATUS_COMPLETE -> State.DELIVERED
            Telephony.Sms.STATUS_PENDING -> State.DELIVERY_PENDING
            Telephony.Sms.STATUS_FAILED -> State.DELIVERY_FAILED
            else -> State.SENT
        }
        else -> State.OTHER
    }
}

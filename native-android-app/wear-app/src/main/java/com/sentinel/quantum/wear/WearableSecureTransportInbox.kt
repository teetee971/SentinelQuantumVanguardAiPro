package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2

internal fun interface WearableSecureFrameHandler {
    fun onFrame(sourceNodeId: String, path: String, frame: WearableProtocolV2.Frame)
}

internal object WearableSecureTransportInbox {
    @Volatile
    private var handler: WearableSecureFrameHandler? = null

    fun install(value: WearableSecureFrameHandler) {
        handler = value
    }

    fun clear() {
        handler = null
    }

    fun dispatch(sourceNodeId: String, path: String, frame: WearableProtocolV2.Frame): Boolean {
        val current = handler ?: return false
        current.onFrame(sourceNodeId, path, frame)
        return true
    }
}

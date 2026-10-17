package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2

internal fun interface WearableSecureFrameHandler {
    fun onFrame(sourceNodeId: String, path: String, frame: WearableProtocolV2.Frame)
}

internal object WearableSecureTransportInbox {
    @Volatile
    private var handler: WearableSecureFrameHandler? = null

    @Synchronized
    fun install(value: WearableSecureFrameHandler) {
        handler = value
    }

    @Synchronized
    fun clear() {
        handler = null
    }

    @Synchronized
    fun clear(expectedOwner: WearableSecureFrameHandler): Boolean {
        if (handler !== expectedOwner) return false
        handler = null
        return true
    }

    fun dispatch(sourceNodeId: String, path: String, frame: WearableProtocolV2.Frame): Boolean {
        val current = handler ?: return false
        return try {
            current.onFrame(sourceNodeId, path, frame)
            true
        } catch (_: Exception) {
            false
        }
    }
}

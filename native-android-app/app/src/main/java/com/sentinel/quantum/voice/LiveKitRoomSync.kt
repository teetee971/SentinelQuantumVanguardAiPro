package com.sentinel.quantum.voice

internal enum class LiveKitSyncSignal {
    DISCONNECTED,
    RECONNECTING,
    RECONNECTED,
    LOCAL_MIC_LOST,
    TOKEN_EXPIRED
}

internal enum class LiveKitSyncFollowUp {
    NONE,
    PUBLICATION_LOST,
    ROOM_GONE
}

/** What Sentinel must do when LiveKit changes the room after connect. Never republishes the microphone. */
internal object LiveKitRoomSync {
    fun followUp(signal: LiveKitSyncSignal): LiveKitSyncFollowUp = when (signal) {
        LiveKitSyncSignal.RECONNECTING,
        LiveKitSyncSignal.LOCAL_MIC_LOST,
        LiveKitSyncSignal.RECONNECTED -> LiveKitSyncFollowUp.PUBLICATION_LOST
        LiveKitSyncSignal.DISCONNECTED,
        LiveKitSyncSignal.TOKEN_EXPIRED -> LiveKitSyncFollowUp.ROOM_GONE
    }
}

internal class LiveKitPublicationSuspended :
    IllegalStateException("LiveKit microphone publication is suspended")

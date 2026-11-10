package com.sentinel.quantum.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveKitRoomSyncTest {
    @Test
    fun reconnectAndLostPublicationDropTheMicrophoneWithoutLeavingTheRoom() {
        assertEquals(LiveKitSyncFollowUp.PUBLICATION_LOST, LiveKitRoomSync.followUp(LiveKitSyncSignal.RECONNECTING))
        assertEquals(LiveKitSyncFollowUp.PUBLICATION_LOST, LiveKitRoomSync.followUp(LiveKitSyncSignal.RECONNECTED))
        assertEquals(LiveKitSyncFollowUp.PUBLICATION_LOST, LiveKitRoomSync.followUp(LiveKitSyncSignal.LOCAL_MIC_LOST))
    }

    @Test
    fun disconnectAndTokenExpiryLeaveTheRoom() {
        assertEquals(LiveKitSyncFollowUp.ROOM_GONE, LiveKitRoomSync.followUp(LiveKitSyncSignal.DISCONNECTED))
        assertEquals(LiveKitSyncFollowUp.ROOM_GONE, LiveKitRoomSync.followUp(LiveKitSyncSignal.TOKEN_EXPIRED))
    }
}

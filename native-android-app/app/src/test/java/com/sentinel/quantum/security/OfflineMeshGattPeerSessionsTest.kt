package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshGattPeerSessionsTest {
    @Test fun connectionAndSubscriptionNeverGrantPrivateData() {
        val peers = OfflineMeshGattPeerSessions()
        val token = peers.connect("peer-a")!!
        assertTrue(peers.updateMtu(token, 247))
        assertTrue(peers.setNotifications(token, true))
        assertTrue(peers.canSendControlNotification(token, 244))
        assertFalse(peers.canSendControlNotification(token, 245))
        assertFalse(peers.canTransferPrivateData(token))
        assertFalse(peers.snapshot(token)!!.privateDataAllowed)
    }

    @Test fun staleCallbacksCannotChangeReconnectedPeer() {
        val peers = OfflineMeshGattPeerSessions()
        val old = peers.connect("peer-a")!!
        assertTrue(peers.disconnect(old))
        val current = peers.connect("peer-a")!!
        assertNotEquals(old, current)
        assertFalse(peers.setNotifications(old, true))
        assertFalse(peers.updateMtu(old, 517))
        assertFalse(peers.disconnect(old))
        assertFalse(peers.snapshot(current)!!.notificationsEnabled)
        assertEquals(23, peers.snapshot(current)!!.mtu)
    }

    @Test fun subscriptionsAreIsolatedAndBounded() {
        val peers = OfflineMeshGattPeerSessions(2)
        val a = peers.connect("peer-a")!!
        val b = peers.connect("peer-b")!!
        assertNull(peers.connect("peer-c"))
        assertNull(peers.connect("peer-a"))
        assertTrue(peers.setNotifications(a, true))
        assertTrue(peers.canSendControlNotification(a, 20))
        assertFalse(peers.canSendControlNotification(b, 20))
        assertFalse(peers.canSendControlNotification(a, 21))
        assertTrue(peers.disconnect(a))
        assertNotNull(peers.connect("peer-c"))
        peers.close()
        assertEquals(0, peers.peerCount())
        assertNull(peers.snapshot(b))
    }

    @Test fun rejectedMtuNeverChangesCurrentLimit() {
        val peers = OfflineMeshGattPeerSessions()
        val token = peers.connect("peer-a")!!
        assertFalse(peers.updateMtu(token, 22))
        assertFalse(peers.updateMtu(token, 518))
        assertEquals(23, peers.snapshot(token)!!.mtu)
        assertFalse(peers.canTransferPrivateData(token))
    }

    @Test fun onlyExplicitAuthenticatedChannelMayEnablePrivateData() {
        val peers = OfflineMeshGattPeerSessions()
        val token = peers.connect("peer-a")!!
        assertFalse(peers.setPrivateDataAllowed(token, true))
        assertTrue(peers.setNotifications(token, true))
        assertTrue(peers.setPrivateDataAllowed(token, true))
        assertTrue(peers.canTransferPrivateData(token))
        assertTrue(peers.setNotifications(token, false))
        assertFalse(peers.canTransferPrivateData(token))
    }
}

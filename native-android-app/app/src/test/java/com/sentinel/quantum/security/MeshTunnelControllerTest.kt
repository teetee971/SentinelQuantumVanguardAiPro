package com.sentinel.quantum.security

import com.wireguard.config.InetNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTunnelControllerTest {
    private val privateKey = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="
    private val peerKey = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="

    @Test
    fun buildsSplitRouteMeshConfigWithoutDefaultRoutesOrDns() {
        val plan = MeshTunnelController.TunnelPlan(
            localNodeId = "device:android-01",
            localMeshAddresses = listOf("10.220.0.1/32", "fd42:220::1/128"),
            peers = listOf(
                MeshTunnelController.PeerPlan(
                    nodeId = "device:peer-01",
                    publicKeyBase64 = peerKey,
                    meshAddresses = listOf("10.220.0.2/32", "fd42:220::2/128"),
                    endpoint = "192.0.2.44:51820"
                )
            )
        )

        val config = MeshTunnelController.buildAndValidateConfig(plan, privateKey)
        assertEquals(1, config.peers.size)
        val allowed = config.peers.single().allowedIps
        assertTrue(allowed.contains(InetNetwork.parse("10.220.0.2/32")))
        assertTrue(allowed.contains(InetNetwork.parse("fd42:220::2/128")))
        assertFalse(allowed.contains(InetNetwork.parse("0.0.0.0/0")))
        assertFalse(allowed.contains(InetNetwork.parse("::/0")))
        assertTrue(config.getInterface().getDnsServers().isEmpty())
    }

    @Test
    fun rejectsDefaultRouteInPrivateMesh() {
        val plan = MeshTunnelController.TunnelPlan(
            localNodeId = "device:android-01",
            localMeshAddresses = listOf("10.220.0.1/32"),
            peers = listOf(
                MeshTunnelController.PeerPlan(
                    nodeId = "device:peer-01",
                    publicKeyBase64 = peerKey,
                    meshAddresses = listOf("0.0.0.0/0"),
                    endpoint = "192.0.2.44:51820"
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            MeshTunnelController.buildAndValidateConfig(plan, privateKey)
        }
    }

    @Test
    fun rejectsRouteAssignedToMultiplePeers() {
        val shared = "10.220.0.9/32"
        val plan = MeshTunnelController.TunnelPlan(
            localNodeId = "device:android-01",
            localMeshAddresses = listOf("10.220.0.1/32"),
            peers = listOf(
                MeshTunnelController.PeerPlan(
                    nodeId = "device:peer-01",
                    publicKeyBase64 = peerKey,
                    meshAddresses = listOf(shared),
                    endpoint = "192.0.2.44:51820"
                ),
                MeshTunnelController.PeerPlan(
                    nodeId = "device:peer-02",
                    publicKeyBase64 = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVpbXF1eX2A=",
                    meshAddresses = listOf(shared),
                    endpoint = "192.0.2.45:51820"
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            MeshTunnelController.buildAndValidateConfig(plan, privateKey)
        }
    }

    @Test
    fun hostCidrValidationAcceptsOverlayHostsAndRejectsNetworkPrefixes() {
        assertEquals("10.220.0.1/32", MeshTunnelController.validateHostCidr("10.220.0.1/32"))
        assertEquals("fd42:220::1/128", MeshTunnelController.validateHostCidr("fd42:220::1/128"))
        assertThrows(IllegalArgumentException::class.java) {
            MeshTunnelController.validateHostCidr("10.220.0.0/24")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeshTunnelController.validateHostCidr("::/0")
        }
    }
}


    @Test
    fun rejectsUnsafeSpecialUseOverlayAddresses() {
        val unsafe = listOf(
            "0.0.0.1/32",
            "127.0.0.1/32",
            "169.254.10.1/32",
            "224.0.0.1/32",
            "255.255.255.255/32",
            "::/128",
            "::1/128",
            "fe80::1/128",
            "ff02::1/128",
            "::ffff:192.0.2.1/128"
        )
        unsafe.forEach { address ->
            assertThrows(IllegalArgumentException::class.java) {
                MeshTunnelController.validateHostCidr(address)
            }
        }
    }

package com.sentinel.quantum.security

import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnGatewayCatalogTransportTest {
    @Test fun defaultClientDisablesRedirects() {
        val client = OkHttpVpnGatewayCatalogTransport.defaultClient()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test fun rejectsHttpEndpoint() {
        assertThrows {
            OkHttpVpnGatewayCatalogTransport(
                endpointUrl = "http://vpn.example.com/catalog",
                allowedHosts = setOf("vpn.example.com")
            )
        }
    }

    @Test fun rejectsEndpointOutsideAllowedHosts() {
        assertThrows {
            OkHttpVpnGatewayCatalogTransport(
                endpointUrl = "https://attacker.example/catalog",
                allowedHosts = setOf("vpn.example.com")
            )
        }
    }

    @Test fun rejectsCredentialsQueryAndFragment() {
        for (endpoint in listOf(
            "https://user:pass@vpn.example.com/catalog",
            "https://vpn.example.com/catalog?x=1",
            "https://vpn.example.com/catalog#fragment"
        )) {
            assertThrows {
                OkHttpVpnGatewayCatalogTransport(
                    endpointUrl = endpoint,
                    allowedHosts = setOf("vpn.example.com")
                )
            }
        }
    }

    @Test fun rejectsClientThatFollowsRedirects() {
        val redirectingClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        assertThrows {
            OkHttpVpnGatewayCatalogTransport(
                endpointUrl = "https://vpn.example.com/catalog",
                allowedHosts = setOf("vpn.example.com"),
                client = redirectingClient
            )
        }
    }

    @Test fun acceptsCanonicalHttpsEndpointConfiguration() {
        val transport = OkHttpVpnGatewayCatalogTransport(
            endpointUrl = "https://vpn.example.com/catalog",
            allowedHosts = setOf("VPN.EXAMPLE.COM")
        )
        assertTrue(transport is VpnGatewayCatalogTransport)
    }

    private fun assertThrows(block: () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}

package com.sentinel.quantum.security

import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.net.InetAddress
import org.json.JSONArray
import org.json.JSONObject

/**
 * Validates a bounded provisioning response and assembles the WireGuard client configuration
 * locally so the device private key is never returned by or transmitted to the control plane.
 */
object VpnProvisioningContract {
    data class ProvisioningPlan(
        val gatewayId: String,
        val gatewayPublicKeyBase64: String,
        val endpointHost: String,
        val endpointPort: Int,
        val clientAddresses: List<String>,
        val dnsServers: Set<String>,
        val expiresAtMs: Long
    )

    data class Result(
        val accepted: Boolean,
        val reason: String,
        val plan: ProvisioningPlan? = null,
        val configuration: ByteArray? = null
    )

    fun buildConfiguration(
        responseBody: String,
        gateway: SignedVpnGatewayCatalogVerifier.Gateway,
        devicePublicKeyBase64: String,
        devicePrivateKeyBase64: String,
        now: Long
    ): Result {
        if (responseBody.length !in 2..MAX_RESPONSE_CHARS || now < 0L) {
            return Result(false, "VPN_PROVISIONING_INPUT_INVALID")
        }
        if (!VpnWireGuardIdentityStore.validWireGuardKey(devicePublicKeyBase64) ||
            !VpnWireGuardIdentityStore.validWireGuardKey(devicePrivateKeyBase64)) {
            return Result(false, "VPN_PROVISIONING_DEVICE_KEY_INVALID")
        }
        val derived = runCatching {
            KeyPair(Key.fromBase64(devicePrivateKeyBase64)).publicKey.toBase64()
        }.getOrNull()
        if (derived != devicePublicKeyBase64) {
            return Result(false, "VPN_PROVISIONING_DEVICE_KEY_MISMATCH")
        }
        if (gateway.status != SentinelVpnController.GatewayStatus.AVAILABLE) {
            return Result(false, "VPN_PROVISIONING_GATEWAY_NOT_AVAILABLE")
        }

        val json = runCatching { JSONObject(responseBody) }.getOrNull()
            ?: return Result(false, "VPN_PROVISIONING_RESPONSE_INVALID")
        if (json.length() != REQUIRED_FIELDS.size || REQUIRED_FIELDS.any { !json.has(it) }) {
            return Result(false, "VPN_PROVISIONING_RESPONSE_SCHEMA_INVALID")
        }

        val gatewayId = json.optString("gatewayId", "")
        val echoedDeviceKey = json.optString("devicePublicKey", "")
        val gatewayPublicKey = json.optString("gatewayPublicKey", "")
        val endpointHost = json.optString("endpointHost", "")
        val endpointPort = json.optInt("endpointPort", -1)
        val expiresAtMs = json.optLong("expiresAtMs", -1L)
        val addresses = json.optJSONArray("clientAddresses")
            ?.let(::stringArray)
            ?: return Result(false, "VPN_PROVISIONING_ADDRESSES_INVALID")
        val dns = json.optJSONArray("dnsServers")
            ?.let(::stringArray)
            ?: return Result(false, "VPN_PROVISIONING_DNS_INVALID")

        if (gatewayId != gateway.id) return Result(false, "VPN_PROVISIONING_GATEWAY_MISMATCH")
        if (echoedDeviceKey != devicePublicKeyBase64) return Result(false, "VPN_PROVISIONING_DEVICE_KEY_MISMATCH")
        if (!VpnWireGuardIdentityStore.validWireGuardKey(gatewayPublicKey)) {
            return Result(false, "VPN_PROVISIONING_GATEWAY_KEY_INVALID")
        }
        if (endpointHost != gateway.endpointHostname || endpointPort != gateway.endpointPort) {
            return Result(false, "VPN_PROVISIONING_ENDPOINT_MISMATCH")
        }
        if (expiresAtMs <= now || expiresAtMs - now > MAX_PLAN_LIFETIME_MS) {
            return Result(false, "VPN_PROVISIONING_EXPIRY_INVALID")
        }
        if (addresses.size !in 2..MAX_CLIENT_ADDRESSES ||
            addresses.toSet().size != addresses.size ||
            addresses.none(::isIpv4HostCidr) ||
            addresses.none(::isIpv6HostCidr)) {
            return Result(false, "VPN_PROVISIONING_ADDRESSES_INVALID")
        }

        val canonicalDns = runCatching {
            dns.map(SentinelVpnController::canonicalizeNumericIp).toSet()
        }.getOrNull() ?: return Result(false, "VPN_PROVISIONING_DNS_INVALID")
        if (canonicalDns.isEmpty() || canonicalDns != gateway.dnsServerAddresses) {
            return Result(false, "VPN_PROVISIONING_DNS_MISMATCH")
        }

        val plan = ProvisioningPlan(
            gatewayId = gatewayId,
            gatewayPublicKeyBase64 = gatewayPublicKey,
            endpointHost = endpointHost,
            endpointPort = endpointPort,
            clientAddresses = addresses,
            dnsServers = canonicalDns,
            expiresAtMs = expiresAtMs
        )

        val config = buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $devicePrivateKeyBase64")
            appendLine("Address = ${addresses.joinToString(", ")}")
            appendLine("DNS = ${canonicalDns.sorted().joinToString(", ")}")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $gatewayPublicKey")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = $endpointHost:$endpointPort")
            appendLine("PersistentKeepalive = 25")
        }.toByteArray(Charsets.US_ASCII)

        return runCatching {
            val parsed = SentinelVpnController.parseAndValidateFullTunnelConfig(config)
            SentinelVpnController.validateGatewayDns(parsed, gateway.toControllerDescriptor())
            Result(true, "VPN_PROVISIONING_READY", plan, config)
        }.getOrElse {
            config.fill(0)
            Result(false, "VPN_PROVISIONING_CONFIGURATION_INVALID")
        }
    }

    private fun stringArray(array: JSONArray): List<String>? {
        if (array.length() !in 1..MAX_ARRAY_ITEMS) return null
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index, null) ?: return null
                if (value.length !in 1..MAX_VALUE_CHARS || value.trim() != value) return null
                add(value)
            }
        }
    }

    private fun isIpv4HostCidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2 || parts[1] != "32") return false
        val ip = parts[0]
        if (!ip.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))) return false
        return runCatching { InetAddress.getByName(ip).address.size == 4 }.getOrDefault(false)
    }

    private fun isIpv6HostCidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2 || parts[1] != "128" || !parts[0].contains(':')) return false
        return runCatching { InetAddress.getByName(parts[0]).address.size == 16 }.getOrDefault(false)
    }

    private const val MAX_RESPONSE_CHARS = 16_384
    private const val MAX_ARRAY_ITEMS = 8
    private const val MAX_CLIENT_ADDRESSES = 4
    private const val MAX_VALUE_CHARS = 256
    private const val MAX_PLAN_LIFETIME_MS = 15 * 60 * 1000L
    private val REQUIRED_FIELDS = setOf(
        "gatewayId",
        "devicePublicKey",
        "gatewayPublicKey",
        "endpointHost",
        "endpointPort",
        "clientAddresses",
        "dnsServers",
        "expiresAtMs"
    )
}

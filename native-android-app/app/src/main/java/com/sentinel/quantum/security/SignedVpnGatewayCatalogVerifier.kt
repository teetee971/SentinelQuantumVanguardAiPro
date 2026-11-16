package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec

/**
 * Verifies the signed Sentinel VPN gateway catalog.
 *
 * The verifier is intentionally fail-closed: only signed, fresh, increasing catalogs are accepted,
 * and only AVAILABLE gateways with fresh health evidence can be selected.
 */
class SignedVpnGatewayCatalogVerifier(
    trustedKeys: Map<String, PublicKey>,
    private val expectedIssuerId: String
) {
    private val keys = trustedKeys.toMap()
    private val p256Parameters = runCatching {
        AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
    }.getOrNull()

    init {
        require(ID_PATTERN.matches(expectedIssuerId)) { "VPN_CATALOG_ISSUER_CONFIG_INVALID" }
        require(keys.size in 1..MAX_TRUSTED_KEYS && keys.keys.all(ID_PATTERN::matches)) {
            "VPN_CATALOG_KEY_CONFIG_INVALID"
        }
    }

    data class Gateway(
        val id: String,
        val countryCode: String,
        val status: SentinelVpnController.GatewayStatus,
        val endpointHostname: String?,
        val endpointPort: Int?,
        val ipv4: Boolean,
        val ipv6: Boolean,
        val loadPercent: Int?,
        val latencyMs: Int?,
        val healthCheckedAtMs: Long?,
        val dnsServerAddresses: Set<String>
    ) {
        fun toControllerDescriptor() = SentinelVpnController.GatewayDescriptor(
            id = id,
            countryCode = countryCode,
            status = status,
            dnsServerAddresses = dnsServerAddresses
        )
    }

    data class Catalog(
        val catalogId: String,
        val sequence: Long,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        val issuerId: String,
        val keyId: String,
        val gateways: List<Gateway>
    ) {
        fun selectBestAvailable(countryCode: String? = null, now: Long): Gateway? {
            if (now < 0L || now >= expiresAtMs) return null
            val requestedCountry = countryCode?.also {
                if (!COUNTRY_PATTERN.matches(it)) return null
            }
            return gateways.asSequence()
                .filter { it.status == SentinelVpnController.GatewayStatus.AVAILABLE }
                .filter { requestedCountry == null || it.countryCode == requestedCountry }
                .filter { gateway ->
                    val checked = gateway.healthCheckedAtMs ?: return@filter false
                    checked <= now + MAX_FUTURE_SKEW_MS && now - checked <= MAX_HEALTH_AGE_MS
                }
                .sortedWith(
                    compareBy<Gateway> { it.latencyMs ?: Int.MAX_VALUE }
                        .thenBy { it.loadPercent ?: Int.MAX_VALUE }
                        .thenBy { it.id }
                )
                .firstOrNull()
        }
    }

    data class Result(
        val accepted: Boolean,
        val reason: String,
        val catalog: Catalog? = null
    )

    fun verify(envelope: String, highestAcceptedSequence: Long, now: Long): Result {
        if (envelope.length !in 1..MAX_ENVELOPE_CHARS || highestAcceptedSequence < 0L || now < 0L) {
            return Result(false, "VPN_CATALOG_INPUT_INVALID")
        }
        val lines = envelope.split('\n')
        if (lines.size != 3 || lines.any { it.endsWith('\r') }) {
            return Result(false, "VPN_CATALOG_ENVELOPE_INVALID")
        }
        val keyId = field(lines[0], "key_id")?.takeIf(ID_PATTERN::matches)
            ?: return Result(false, "VPN_CATALOG_KEY_ID_INVALID")
        val payloadBytes = decodeHex(field(lines[1], "payload_hex"))
            ?: return Result(false, "VPN_CATALOG_PAYLOAD_INVALID")
        val signatureBytes = decodeHex(field(lines[2], "signature_hex"))
            ?: return Result(false, "VPN_CATALOG_SIGNATURE_INVALID")
        if (payloadBytes.size !in 1..MAX_PAYLOAD_BYTES || signatureBytes.size !in 64..80) {
            return Result(false, "VPN_CATALOG_SIZE_INVALID")
        }

        val key = keys[keyId] ?: return Result(false, "VPN_CATALOG_KEY_UNKNOWN")
        if (!isP256(key)) return Result(false, "VPN_CATALOG_KEY_ALGORITHM_INVALID")
        val verified = runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(key)
                update(payloadBytes)
            }.verify(signatureBytes)
        }.getOrDefault(false)
        if (!verified) return Result(false, "VPN_CATALOG_SIGNATURE_INVALID")

        val catalog = parsePayload(payloadBytes, keyId, now)
            ?: return Result(false, "VPN_CATALOG_SCHEMA_INVALID")
        if (catalog.issuerId != expectedIssuerId) return Result(false, "VPN_CATALOG_ISSUER_INVALID")
        if (catalog.sequence <= highestAcceptedSequence) return Result(false, "VPN_CATALOG_ROLLBACK_REJECTED")
        if (catalog.issuedAtMs > now + MAX_FUTURE_SKEW_MS) return Result(false, "VPN_CATALOG_ISSUED_IN_FUTURE")
        if (catalog.expiresAtMs <= now) return Result(false, "VPN_CATALOG_EXPIRED")
        if (catalog.expiresAtMs <= catalog.issuedAtMs ||
            catalog.expiresAtMs - catalog.issuedAtMs > MAX_CATALOG_LIFETIME_MS) {
            return Result(false, "VPN_CATALOG_LIFETIME_INVALID")
        }
        return Result(true, "VPN_CATALOG_ACCEPTED", catalog)
    }

    private fun parsePayload(bytes: ByteArray, envelopeKeyId: String, now: Long): Catalog? {
        val payload = runCatching {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull() ?: return null
        val lines = payload.split('\n')
        if (lines.size !in FIXED_PAYLOAD_LINES..(FIXED_PAYLOAD_LINES + MAX_GATEWAYS) ||
            lines.firstOrNull() != DOMAIN || lines.any { it.endsWith('\r') }) return null

        val catalogId = field(lines[1], "catalog_id")?.takeIf(ID_PATTERN::matches) ?: return null
        if (catalogId != EXPECTED_CATALOG_ID) return null
        val sequence = field(lines[2], "sequence")?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val issuedAt = field(lines[3], "issued_at_ms")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val expiresAt = field(lines[4], "expires_at_ms")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val issuerId = field(lines[5], "issuer_id")?.takeIf(ID_PATTERN::matches) ?: return null
        val keyId = field(lines[6], "key_id")?.takeIf(ID_PATTERN::matches) ?: return null
        if (keyId != envelopeKeyId) return null

        val gateways = lines.drop(FIXED_PAYLOAD_LINES).map { parseGatewayLine(it, now) ?: return null }
        if (gateways.map { it.id } != gateways.map { it.id }.sorted()) return null
        if (gateways.map { it.id }.toSet().size != gateways.size) return null

        return Catalog(catalogId, sequence, issuedAt, expiresAt, issuerId, keyId, gateways)
    }

    private fun parseGatewayLine(line: String, now: Long): Gateway? {
        val raw = field(line, "gateway") ?: return null
        val parts = raw.split('|')
        if (parts.size != GATEWAY_FIELDS) return null

        val id = parts[0].takeIf(GATEWAY_ID_PATTERN::matches) ?: return null
        val country = parts[1].takeIf(COUNTRY_PATTERN::matches) ?: return null
        val status = runCatching { SentinelVpnController.GatewayStatus.valueOf(parts[2]) }.getOrNull() ?: return null
        val endpointHost = parts[3].takeUnless { it == "-" }
        val endpointPort = parts[4].takeUnless { it == "-" }?.toIntOrNull()
        val ipv4 = parseBool(parts[5]) ?: return null
        val ipv6 = parseBool(parts[6]) ?: return null
        val load = parts[7].takeUnless { it == "-" }?.toIntOrNull()
        val latency = parts[8].takeUnless { it == "-" }?.toIntOrNull()
        val health = parts[9].takeUnless { it == "-" }?.toLongOrNull()
        val dns = if (parts[10] == "-") emptySet() else parts[10].split(',').toSet()

        if (endpointHost != null && !HOST_PATTERN.matches(endpointHost)) return null
        if (endpointHost?.endsWith(".invalid") == true) return null
        if (endpointPort != null && endpointPort !in 1..65535) return null
        if (load != null && load !in 0..100) return null
        if (latency != null && latency !in 0..MAX_LATENCY_MS) return null
        if (health != null && (health < 0L || health > now + MAX_FUTURE_SKEW_MS)) return null
        if (dns.size > SentinelVpnController.MAX_GATEWAY_DNS_SERVERS) return null
        val canonicalDns = runCatching {
            dns.map(SentinelVpnController::canonicalizeNumericIp).toSet()
        }.getOrNull() ?: return null
        if (canonicalDns.size != dns.size) return null

        if (status == SentinelVpnController.GatewayStatus.AVAILABLE) {
            if (endpointHost == null || endpointPort == null || !ipv4 || !ipv6 ||
                load == null || latency == null || health == null || canonicalDns.isEmpty()) return null
            if (now - health > MAX_HEALTH_AGE_MS) return null
        }

        return Gateway(
            id = id,
            countryCode = country,
            status = status,
            endpointHostname = endpointHost,
            endpointPort = endpointPort,
            ipv4 = ipv4,
            ipv6 = ipv6,
            loadPercent = load,
            latencyMs = latency,
            healthCheckedAtMs = health,
            dnsServerAddresses = canonicalDns
        )
    }

    private fun parseBool(value: String): Boolean? = when (value) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private fun field(line: String, name: String): String? {
        val prefix = "$name="
        return line.takeIf { it.startsWith(prefix) && it.length > prefix.length }?.removePrefix(prefix)
    }

    private fun decodeHex(value: String?): ByteArray? {
        if (value == null || value.length % 2 != 0 || value.length > MAX_HEX_CHARS ||
            !value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return runCatching {
            ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun isP256(key: PublicKey): Boolean {
        val ecKey = key as? ECPublicKey ?: return false
        val expected = p256Parameters ?: return false
        val actual = ecKey.params
        return actual.curve == expected.curve &&
            actual.generator == expected.generator &&
            actual.order == expected.order &&
            actual.cofactor == expected.cofactor
    }

    companion object {
        const val DOMAIN = "sentinel-vpn-gateway-catalog-v1"
        const val EXPECTED_CATALOG_ID = "public-vpn"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val FIXED_PAYLOAD_LINES = 7
        private const val GATEWAY_FIELDS = 11
        private const val MAX_GATEWAYS = 128
        private const val MAX_TRUSTED_KEYS = 8
        private const val MAX_ENVELOPE_CHARS = 262_144
        private const val MAX_PAYLOAD_BYTES = 96 * 1024
        private const val MAX_HEX_CHARS = 192 * 1024
        private const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L
        private const val MAX_CATALOG_LIFETIME_MS = 24 * 60 * 60 * 1000L
        private const val MAX_HEALTH_AGE_MS = 5 * 60 * 1000L
        private const val MAX_LATENCY_MS = 60_000
        private val ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,64}")
        private val GATEWAY_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{1,62}")
        private val COUNTRY_PATTERN = Regex("[A-Z]{2}")
        private val HOST_PATTERN = Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?")
    }
}

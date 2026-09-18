package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.crypto.Key
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MeshTunnelController(
    context: Context,
    private val identityStore: MeshWireGuardIdentityStore,
    private val backend: Backend = GoBackend(context.applicationContext)
) {
    enum class RuntimeState {
        READY,
        CONSENT_REQUIRED,
        CONNECTING,
        CONNECTED,
        FAILED,
        DISCONNECTED
    }

    data class PeerPlan(
        val nodeId: String,
        val publicKeyBase64: String,
        val meshAddresses: List<String>,
        val endpoint: String,
        val persistentKeepaliveSeconds: Int = 25
    )

    data class TunnelPlan(
        val localNodeId: String,
        val localMeshAddresses: List<String>,
        val peers: List<PeerPlan>
    )

    data class OperationResult(
        val state: RuntimeState,
        val reason: String
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var runtimeState = RuntimeState.DISCONNECTED

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            runtimeState = when (newState) {
                Tunnel.State.UP -> RuntimeState.CONNECTED
                Tunnel.State.DOWN -> {
                    SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
                    RuntimeState.DISCONNECTED
                }
                Tunnel.State.TOGGLE -> runtimeState
            }
        }
    }

    fun currentState(): RuntimeState = runtimeState

    fun prepareConsentIntent(): Intent? = VpnService.prepare(appContext)

    suspend fun connect(plan: TunnelPlan): OperationResult = mutex.withLock {
        if (!SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)) {
            runtimeState = RuntimeState.FAILED
            return OperationResult(runtimeState, "INTERNET_VPN_ALREADY_ACTIVE")
        }

        if (prepareConsentIntent() != null) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
            runtimeState = RuntimeState.CONSENT_REQUIRED
            return OperationResult(runtimeState, "ANDROID_VPN_CONSENT_REQUIRED")
        }

        val privateKey = identityStore.loadPrivateKeyBase64()
        if (privateKey == null) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
            runtimeState = RuntimeState.FAILED
            return OperationResult(runtimeState, "MESH_PRIVATE_KEY_UNAVAILABLE")
        }

        val config = try {
            buildAndValidateConfig(plan, privateKey)
        } catch (_: Exception) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
            runtimeState = RuntimeState.FAILED
            return OperationResult(runtimeState, "INVALID_OR_UNSAFE_MESH_PLAN")
        }

        runtimeState = RuntimeState.CONNECTING
        return try {
            val state = withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.UP, config)
            }
            if (state == Tunnel.State.UP) {
                runtimeState = RuntimeState.CONNECTED
                OperationResult(runtimeState, "MESH_TUNNEL_UP")
            } else {
                SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
                runtimeState = RuntimeState.FAILED
                OperationResult(runtimeState, "BACKEND_DID_NOT_REPORT_UP")
            }
        } catch (_: Exception) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
            runtimeState = RuntimeState.FAILED
            OperationResult(runtimeState, "BACKEND_START_FAILED")
        }
    }

    suspend fun disconnect(): OperationResult = mutex.withLock {
        return try {
            val state = withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            }
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
            if (state == Tunnel.State.DOWN) {
                runtimeState = RuntimeState.DISCONNECTED
                OperationResult(runtimeState, "MESH_TUNNEL_DOWN")
            } else {
                runtimeState = RuntimeState.FAILED
                OperationResult(runtimeState, "BACKEND_DID_NOT_REPORT_DOWN")
            }
        } catch (_: Exception) {
            SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
            runtimeState = RuntimeState.FAILED
            OperationResult(runtimeState, "BACKEND_STOP_FAILED")
        }
    }

    companion object {
        private const val TUNNEL_NAME = "sentinel-mesh"
        internal const val MAX_PEERS = 128
        internal const val MAX_ADDRESSES_PER_NODE = 4
        internal const val MAX_CONFIG_BYTES = 128 * 1024

        internal fun buildAndValidateConfig(plan: TunnelPlan, privateKeyBase64: String): Config {
            require(MeshNodeCredentialStore.validNodeId(plan.localNodeId)) { "invalid local node id" }
            require(validWireGuardKey(privateKeyBase64)) { "invalid private key" }
            require(plan.localMeshAddresses.isNotEmpty()) { "local mesh address required" }
            require(plan.localMeshAddresses.size <= MAX_ADDRESSES_PER_NODE) { "too many local mesh addresses" }

            val localAddresses = plan.localMeshAddresses.map(::validateHostCidr).distinct()
            require(localAddresses.size == plan.localMeshAddresses.size) { "duplicate local mesh address" }

            require(plan.peers.isNotEmpty()) { "at least one mesh peer required" }
            require(plan.peers.size <= MAX_PEERS) { "too many mesh peers" }
            require(plan.peers.map { it.nodeId }.toSet().size == plan.peers.size) { "duplicate mesh peer id" }
            require(plan.peers.map { it.publicKeyBase64 }.toSet().size == plan.peers.size) { "duplicate mesh peer key" }

            val claimedRoutes = mutableSetOf<String>()
            val peerBlocks = plan.peers.map { peer ->
                require(MeshNodeCredentialStore.validNodeId(peer.nodeId)) { "invalid peer node id" }
                require(peer.nodeId != plan.localNodeId) { "self peer forbidden" }
                require(validWireGuardKey(peer.publicKeyBase64)) { "invalid peer key" }
                require(peer.meshAddresses.isNotEmpty()) { "peer mesh address required" }
                require(peer.meshAddresses.size <= MAX_ADDRESSES_PER_NODE) { "too many peer mesh addresses" }
                require(MeshControlPlaneClient.validEndpoint(peer.endpoint)) { "invalid peer endpoint" }
                require(peer.persistentKeepaliveSeconds in 0..120) { "invalid keepalive" }

                val routes = peer.meshAddresses.map(::validateHostCidr).distinct()
                require(routes.size == peer.meshAddresses.size) { "duplicate peer route" }
                routes.forEach { route ->
                    require(route !in localAddresses) { "peer route overlaps local address" }
                    require(claimedRoutes.add(route)) { "mesh route assigned to multiple peers" }
                }

                buildString {
                    appendLine("[Peer]")
                    appendLine("PublicKey = ${peer.publicKeyBase64}")
                    appendLine("AllowedIPs = ${routes.joinToString(", ")}")
                    appendLine("Endpoint = ${peer.endpoint}")
                    if (peer.persistentKeepaliveSeconds > 0) {
                        appendLine("PersistentKeepalive = ${peer.persistentKeepaliveSeconds}")
                    }
                }.trimEnd()
            }

            val text = buildString {
                appendLine("[Interface]")
                appendLine("PrivateKey = $privateKeyBase64")
                appendLine("Address = ${localAddresses.joinToString(", ")}")
                appendLine()
                append(peerBlocks.joinToString("\n\n"))
            }

            val bytes = text.toByteArray(Charsets.US_ASCII)
            try {
                require(bytes.size <= MAX_CONFIG_BYTES) { "mesh config too large" }
                val config = ByteArrayInputStream(bytes).use { Config.parse(it) }
                validateParsedConfig(config, localAddresses.toSet(), claimedRoutes)
                return config
            } finally {
                bytes.fill(0)
            }
        }

        private fun validateParsedConfig(
            config: Config,
            localAddresses: Set<String>,
            expectedRoutes: Set<String>
        ) {
            require(config.peers.isNotEmpty()) { "mesh peers required" }
            require(config.peers.all { it.endpoint.isPresent }) { "all mesh peers require endpoint" }

            val allAllowed = config.peers.flatMap { it.allowedIps }
            val routes = allAllowed.map { it.toString() }.toSet()
            require(routes == expectedRoutes) { "mesh AllowedIPs mismatch" }
            require(InetNetwork.parse("0.0.0.0/0") !in allAllowed) {
                "IPv4 default route forbidden in Mesh mode"
            }
            require(InetNetwork.parse("::/0") !in allAllowed) {
                "IPv6 default route forbidden in Mesh mode"
            }

            val interfaceAddresses = config.getInterface().getAddresses().map { it.toString() }.toSet()
            require(interfaceAddresses == localAddresses) { "local mesh addresses mismatch" }
            require(config.getInterface().getDnsServers().isEmpty()) { "DNS is not configured by private Mesh mode" }
        }

        internal fun validateHostCidr(raw: String): String {
            val value = raw.trim()
            require(value == raw) { "mesh CIDR whitespace forbidden" }
            val network = InetNetwork.parse(value)
            val text = network.toString()
            require(
                (text.contains(":") && text.endsWith("/128")) ||
                    (!text.contains(":") && text.endsWith("/32"))
            ) { "mesh route must be host CIDR" }

            val lower = text.lowercase()
            if (!lower.contains(":")) {
                val host = lower.substringBefore("/")
                val octets = host.split(".").map { it.toInt() }
                val a = octets[0]
                val b = octets[1]
                val c = octets[2]
                val d = octets[3]
                require(
                    a != 0 &&
                        a != 127 &&
                        !(a == 169 && b == 254) &&
                        a < 224 &&
                        !(a == 255 && b == 255 && c == 255 && d == 255)
                ) { "unsafe mesh IPv4" }
            } else {
                require(
                    lower != "::/128" &&
                        lower != "::1/128" &&
                        !lower.startsWith("::ffff:") &&
                        !lower.startsWith("fe80:") &&
                        !lower.startsWith("fe9") &&
                        !lower.startsWith("fea") &&
                        !lower.startsWith("feb") &&
                        !lower.startsWith("ff")
                ) { "unsafe mesh IPv6" }
            }
            return text
        }

        internal fun validWireGuardKey(value: String): Boolean = runCatching {
            if (value.length != 44) return false
            Key.fromBase64(value)
            true
        }.getOrDefault(false)
    }
}

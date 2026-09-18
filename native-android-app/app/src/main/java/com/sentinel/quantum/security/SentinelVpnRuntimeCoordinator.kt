package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Application-facing orchestration for the public Sentinel VPN.
 *
 * The runtime path is:
 * signed catalog -> anti-rollback sequence -> AVAILABLE gateway selection ->
 * device-local WireGuard identity -> HTTPS provisioning -> local config build ->
 * WireGuard tunnel activation.
 */
class SentinelVpnRuntimeCoordinator internal constructor(
    private val catalogVerifier: CatalogVerifier,
    private val sequenceStore: CatalogSequenceStore,
    private val identityProvider: IdentityProvider,
    private val provisioner: Provisioner,
    private val configurationBuilder: ConfigurationBuilder,
    private val tunnelBridge: TunnelBridge
) {
    constructor(
        context: Context,
        verifier: SignedVpnGatewayCatalogVerifier,
        provisioningEndpointUrl: String,
        allowedProvisioningHosts: Set<String>
    ) : this(
        catalogVerifier = CatalogVerifier { envelope, highestSequence, now ->
            verifier.verify(envelope, highestSequence, now)
        },
        sequenceStore = SharedPreferencesCatalogSequenceStore(context.applicationContext),
        identityProvider = AndroidVpnIdentityProvider(VpnWireGuardIdentityStore(context.applicationContext)),
        provisioner = AndroidProvisioner(
            VpnProvisioningClient(
                endpointUrl = provisioningEndpointUrl,
                allowedHosts = allowedProvisioningHosts
            )
        ),
        configurationBuilder = ConfigurationBuilder { body, gateway, publicKey, privateKey, now ->
            VpnProvisioningContract.buildConfiguration(
                responseBody = body,
                gateway = gateway,
                devicePublicKeyBase64 = publicKey,
                devicePrivateKeyBase64 = privateKey,
                now = now
            )
        },
        tunnelBridge = AndroidTunnelBridge(SentinelVpnController(context.applicationContext))
    )

    data class ConnectResult(
        val accepted: Boolean,
        val reason: String,
        val state: SentinelVpnController.RuntimeState? = null,
        val gatewayId: String? = null,
        val catalogSequence: Long? = null
    )

    suspend fun connect(
        signedCatalogEnvelope: String,
        countryCode: String?,
        accessToken: String,
        now: Long = System.currentTimeMillis()
    ): ConnectResult {
        if (now < 0L) return ConnectResult(false, "VPN_RUNTIME_TIME_INVALID")

        val highestSequence = sequenceStore.load()
        val verified = catalogVerifier.verify(signedCatalogEnvelope, highestSequence, now)
        val catalog = verified.catalog
            ?: return ConnectResult(false, verified.reason)

        if (!sequenceStore.save(catalog.sequence)) {
            return ConnectResult(false, "VPN_RUNTIME_CATALOG_SEQUENCE_STORE_FAILED")
        }

        val gateway = catalog.selectBestAvailable(countryCode, now)
            ?: return ConnectResult(
                accepted = false,
                reason = "VPN_RUNTIME_NO_AVAILABLE_GATEWAY",
                catalogSequence = catalog.sequence
            )

        val identity = identityProvider.getOrCreate()
            ?: return ConnectResult(
                false,
                "VPN_RUNTIME_IDENTITY_UNAVAILABLE",
                gatewayId = gateway.id,
                catalogSequence = catalog.sequence
            )

        val fetched = withContext(Dispatchers.IO) {
            provisioner.provision(
                gatewayId = gateway.id,
                devicePublicKeyBase64 = identity.publicKeyBase64,
                catalogSequence = catalog.sequence,
                accessToken = accessToken
            )
        }
        val responseBody = fetched.responseBody
        if (!fetched.accepted || responseBody == null) {
            return ConnectResult(
                false,
                fetched.reason,
                gatewayId = gateway.id,
                catalogSequence = catalog.sequence
            )
        }

        val built = configurationBuilder.build(
            responseBody,
            gateway,
            identity.publicKeyBase64,
            identity.privateKeyBase64,
            now
        )
        val configuration = built.configuration
            ?: return ConnectResult(
                false,
                built.reason,
                gatewayId = gateway.id,
                catalogSequence = catalog.sequence
            )

        return try {
            val result = tunnelBridge.connect(gateway.toControllerDescriptor(), configuration)
            ConnectResult(
                accepted = result.state == SentinelVpnController.RuntimeState.PROTECTED,
                reason = result.reason,
                state = result.state,
                gatewayId = gateway.id,
                catalogSequence = catalog.sequence
            )
        } finally {
            configuration.fill(0)
        }
    }

    suspend fun disconnect(): SentinelVpnController.OperationResult =
        tunnelBridge.disconnect()

    fun prepareConsentIntent(): Intent? = tunnelBridge.prepareConsentIntent()

    fun currentState(): SentinelVpnController.RuntimeState = tunnelBridge.currentState()

    internal data class IdentityMaterial(
        val publicKeyBase64: String,
        val privateKeyBase64: String
    )

    internal fun interface CatalogVerifier {
        fun verify(
            envelope: String,
            highestAcceptedSequence: Long,
            now: Long
        ): SignedVpnGatewayCatalogVerifier.Result
    }

    internal interface CatalogSequenceStore {
        fun load(): Long
        fun save(sequence: Long): Boolean
    }

    internal fun interface IdentityProvider {
        fun getOrCreate(): IdentityMaterial?
    }

    internal fun interface Provisioner {
        fun provision(
            gatewayId: String,
            devicePublicKeyBase64: String,
            catalogSequence: Long,
            accessToken: String
        ): VpnProvisioningClient.FetchResult
    }

    internal fun interface ConfigurationBuilder {
        fun build(
            responseBody: String,
            gateway: SignedVpnGatewayCatalogVerifier.Gateway,
            devicePublicKeyBase64: String,
            devicePrivateKeyBase64: String,
            now: Long
        ): VpnProvisioningContract.Result
    }

    internal interface TunnelBridge {
        suspend fun connect(
            gateway: SentinelVpnController.GatewayDescriptor,
            configuration: ByteArray
        ): SentinelVpnController.OperationResult

        suspend fun disconnect(): SentinelVpnController.OperationResult
        fun prepareConsentIntent(): Intent?
        fun currentState(): SentinelVpnController.RuntimeState
    }

    private class SharedPreferencesCatalogSequenceStore(context: Context) : CatalogSequenceStore {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun load(): Long = prefs.getLong(KEY_SEQUENCE, 0L).coerceAtLeast(0L)

        override fun save(sequence: Long): Boolean {
            if (sequence <= load()) return sequence == load()
            return prefs.edit().putLong(KEY_SEQUENCE, sequence).commit()
        }

        companion object {
            private const val PREFS_NAME = "sentinel_vpn_catalog_state"
            private const val KEY_SEQUENCE = "highest_sequence"
        }
    }

    private class AndroidVpnIdentityProvider(
        private val store: VpnWireGuardIdentityStore
    ) : IdentityProvider {
        override fun getOrCreate(): IdentityMaterial? {
            val identity = runCatching { store.getOrCreateIdentity() }.getOrNull() ?: return null
            val privateKey = store.loadPrivateKeyBase64() ?: return null
            return IdentityMaterial(identity.publicKeyBase64, privateKey)
        }
    }

    private class AndroidProvisioner(
        private val client: VpnProvisioningClient
    ) : Provisioner {
        override fun provision(
            gatewayId: String,
            devicePublicKeyBase64: String,
            catalogSequence: Long,
            accessToken: String
        ): VpnProvisioningClient.FetchResult =
            client.provision(
                gatewayId = gatewayId,
                devicePublicKeyBase64 = devicePublicKeyBase64,
                catalogSequence = catalogSequence,
                accessToken = accessToken
            )
    }

    private class AndroidTunnelBridge(
        private val controller: SentinelVpnController
    ) : TunnelBridge {
        override suspend fun connect(
            gateway: SentinelVpnController.GatewayDescriptor,
            configuration: ByteArray
        ): SentinelVpnController.OperationResult =
            controller.connect(gateway, configuration)

        override suspend fun disconnect(): SentinelVpnController.OperationResult =
            controller.disconnect()

        override fun prepareConsentIntent(): Intent? =
            controller.prepareConsentIntent()

        override fun currentState(): SentinelVpnController.RuntimeState =
            controller.currentState()
    }
}

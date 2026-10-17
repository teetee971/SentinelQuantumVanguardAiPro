package com.sentinel.quantum.integration

enum class IntegrationState {
    CONNECTED,
    LOCKED_EXTERNAL_DEPENDENCY,
    PHYSICAL_REQUIRED,
    INTENTIONALLY_DISABLED,
    CI_ONLY
}

enum class IntegrationDiagnosticReason {
    BACKEND_NOT_DEPLOYED,
    CREDENTIAL_NOT_PROVISIONED,
    GATEWAY_NOT_AVAILABLE,
    RUNTIME_NOT_OBSERVED,
    TRANSPORT_NOT_IMPLEMENTED,
    IDENTITY_BINDING_MISSING,
    PHYSICAL_VALIDATION_REQUIRED,
    INTENTIONALLY_DISABLED_BY_POLICY,
    CI_ONLY_COMPONENT
}

data class IntegrationStatus(
    val state: IntegrationState,
    val diagnosticReason: IntegrationDiagnosticReason? = null
) {
    init {
        require(
            if (state == IntegrationState.CONNECTED) diagnosticReason == null
            else diagnosticReason != null
        ) { "CONNECTED requires no diagnostic reason; non-CONNECTED states require one" }
    }
}

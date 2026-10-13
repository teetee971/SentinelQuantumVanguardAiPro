package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.SmsDeliveryStatusBus

/** Pure adapters for already-bounded SMS/MMS transport and analysis outcomes. */
object SmsMmsEvidenceAdapter {
    enum class MmsTransportState {
        DOWNLOAD_REQUESTED,
        PROVIDER_READY,
        PROVIDER_REPLAY_NO_DUPLICATE,
        SAFE_PREVIEW_READY,
        LOCAL_QUARANTINE
    }

    enum class AnalysisOutcome { SAFE, SUSPICIOUS, MALICIOUS, QUARANTINED, UNKNOWN }

    fun fromSmsDelivery(
        event: SmsDeliveryStatusBus.Event,
        direction: IntelligenceEvidence.Direction
    ): IntelligenceEvidence.SmsSecurity = IntelligenceEvidence.SmsSecurity(
        direction = direction,
        transportState = when (event.stage) {
            SmsDeliveryStatusBus.Stage.SENT -> if (event.successful) "SENT_CONFIRMED" else "SENT_FAILED"
            SmsDeliveryStatusBus.Stage.DELIVERED ->
                if (event.successful) "DELIVERED_CONFIRMED" else "DELIVERY_FAILED"
        },
        categoryCode = null,
        analysisOutcome = null
    )

    fun fromMms(
        direction: IntelligenceEvidence.Direction,
        transportState: MmsTransportState,
        categoryCode: String? = null,
        analysisOutcome: AnalysisOutcome? = null
    ): IntelligenceEvidence.MmsSecurity {
        require(categoryCode == null || CATEGORY_CODE.matches(categoryCode)) {
            "categoryCode must be a bounded machine-readable code"
        }
        return IntelligenceEvidence.MmsSecurity(
            direction = direction,
            transportState = "MMS_${transportState.name}",
            categoryCode = categoryCode,
            analysisOutcome = analysisOutcome?.name
        )
    }

    private val CATEGORY_CODE = Regex("[A-Z0-9_]{1,32}")
}
